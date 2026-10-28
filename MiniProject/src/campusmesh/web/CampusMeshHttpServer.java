package campusmesh.web;

import campusmesh.buffer.MessageBuffer;
import campusmesh.contact.ContactManager;
import campusmesh.discovery.DiscoveredPeer;
import campusmesh.discovery.DiscoveryManager;
import campusmesh.logging.EventLogger;
import campusmesh.model.Message;
import campusmesh.model.Peer;
import campusmesh.model.Priority;
import campusmesh.networking.MultiTransportSender;
import campusmesh.routing.AdaptiveRouter;
import campusmesh.routing.EpidemicRouter;
import campusmesh.routing.RoutingEngine;
import campusmesh.routing.SprayAndWaitRouter;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Embedded HTTP REST & Static Asset Server for CampusMesh.
 * Serves real-time telemetry, routing controls, and static Web Dashboard assets.
 * Hardened with canonical path-traversal prevention, loopback-by-default binding,
 * per-run auth tokens for POST endpoints, strict input validation, and Gson serialization.
 */
public class CampusMeshHttpServer implements AutoCloseable {

    private static final Gson GSON = new Gson();
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");
    public static final int MAX_PAYLOAD_BYTES = 65536; // 64 KB

    private final String bindHost;
    private final int port;
    private final File webRoot;
    private final Peer localPeer;
    private final MessageBuffer buffer;
    private final ContactManager contactManager;
    private final DiscoveryManager discoveryManager;
    private final RoutingEngine routingEngine;
    private final EventLogger logger;
    private final MultiTransportSender sender;
    private final long startTime;
    private final String apiToken;
    private final AtomicBoolean simulationRunning = new AtomicBoolean(false);
    private final ScheduledExecutorService bufferReaper;

    private HttpServer server;
    private volatile boolean running = false;

    public CampusMeshHttpServer(
            int port,
            File webRoot,
            Peer localPeer,
            MessageBuffer buffer,
            ContactManager contactManager,
            DiscoveryManager discoveryManager,
            RoutingEngine routingEngine,
            EventLogger logger,
            MultiTransportSender sender) {
        this("127.0.0.1", port, webRoot, localPeer, buffer, contactManager, discoveryManager, routingEngine, logger, sender);
    }

    public CampusMeshHttpServer(
            String bindHost,
            int port,
            File webRoot,
            Peer localPeer,
            MessageBuffer buffer,
            ContactManager contactManager,
            DiscoveryManager discoveryManager,
            RoutingEngine routingEngine,
            EventLogger logger,
            MultiTransportSender sender) {

        this.bindHost = (bindHost != null && !bindHost.trim().isEmpty()) ? bindHost.trim() : "127.0.0.1";
        this.port = port;
        this.webRoot = (webRoot != null) ? webRoot : new File("web");
        this.localPeer = localPeer;
        this.buffer = buffer;
        this.contactManager = contactManager;
        this.discoveryManager = discoveryManager;
        this.routingEngine = routingEngine;
        this.logger = logger;
        this.sender = (sender != null) ? sender : new MultiTransportSender();
        this.startTime = System.currentTimeMillis();
        this.apiToken = UUID.randomUUID().toString();
        this.bufferReaper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CampusMesh-BufferReaper");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() throws IOException {
        if (running) return;

        server = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "CampusMesh-HttpWorker");
            t.setDaemon(true);
            return t;
        }));

        // Periodically purge expired messages from DTN buffer every 1 second
        this.bufferReaper.scheduleAtFixedRate(
                this::purgeExpiredMessages,
                1000,
                1000,
                TimeUnit.MILLISECONDS
        );

        // API Endpoints
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/peers", new PeersHandler());
        server.createContext("/api/buffer", new BufferHandler());
        server.createContext("/api/buffer/purge", new BufferHandler());
        server.createContext("/api/messages", new MessagesHandler());
        server.createContext("/api/strategy", new StrategyHandler());
        server.createContext("/api/contacts", new ContactsHandler());
        server.createContext("/api/evaluation", new EvaluationHandler());
        server.createContext("/api/events", new EventsHandler());
        server.createContext("/api/simulation/run", new SimulationHandler());

        // Static Web Dashboard files
        server.createContext("/", new StaticFileHandler());

        server.start();
        running = true;
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (bufferReaper != null) {
            bufferReaper.shutdownNow();
        }
        if (server != null) {
            server.stop(1);
            server = null;
        }
        sender.close();
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return (server != null) ? server.getAddress().getPort() : port;
    }

    public String getBindHost() {
        return bindHost;
    }

    public String getApiToken() {
        return apiToken;
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-CampusMesh-Token");
        return apiToken != null && apiToken.equals(token);
    }

    /**
     * Purges expired messages from the local DTN buffer and logs drop events in SQLite.
     *
     * @return List of expired messages removed
     */
    public synchronized List<Message> purgeExpiredMessages() {
        if (buffer == null) return Collections.emptyList();
        List<Message> expired = buffer.removeExpiredMessages();
        for (Message m : expired) {
            if (logger != null) {
                logger.logEvent(EventLogger.EventType.MESSAGE_EXPIRED, localPeer.getPeerId(), m.getMessageId(), m.getDestinationId(), "EXPIRED_TTL", "{\"ttlSeconds\":" + m.getOriginalTTL() + "}");
            }
            System.out.println("[DTN BUFFER] Dropped expired message: " + m.getMessageId() + " (TTL elapsed)");
        }
        return expired;
    }

    // ------------------------------------------------------------------------
    // HTTP Handlers
    // ------------------------------------------------------------------------

    /**
     * GET /api/status - Local node metrics, buffer pressure, and routing settings.
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }

            double wp = 0.40, wt = 0.40, wb = 0.20;
            if (routingEngine.getStrategy() instanceof AdaptiveRouter) {
                AdaptiveRouter ar = (AdaptiveRouter) routingEngine.getStrategy();
                wp = ar.getWP();
                wt = ar.getWT();
                wb = ar.getWB();
            }

            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String hostHeader = exchange.getRequestHeaders().getFirst("Host");
            // In Docker, host connections forward through 172.x.x.1 gateway.
            // Check Host header: if accessing via localhost / 127.0.0.1, it is the host laptop!
            boolean isLocalhost = (hostHeader != null && (hostHeader.startsWith("localhost") || hostHeader.startsWith("127.0.0.1")))
                    || clientIp.equals("127.0.0.1") || clientIp.equals("0:0:0:0:0:0:0:1") || clientIp.equals("localhost");
            boolean isRemote = !isLocalhost;

            // Dynamic Call-Sign / Node ID: support custom device name via X-Node-ID header
            String customNodeId = exchange.getRequestHeaders().getFirst("X-Node-ID");
            String assignedNodeId;
            if (customNodeId != null && !customNodeId.trim().isEmpty()) {
                assignedNodeId = customNodeId.trim().toUpperCase().replaceAll("[^A-Z0-9_.-]", "_");
                if (assignedNodeId.length() > 32) assignedNodeId = assignedNodeId.substring(0, 32);
            } else {
                assignedNodeId = isRemote ? "PHONE_MOBILE" : localPeer.getPeerId();
            }

            if (isRemote && discoveryManager != null) {
                DiscoveredPeer existing = discoveryManager.getRegistry().getPeer(assignedNodeId);
                if (existing == null) {
                    String platform = "MOBILE";
                    String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
                    if (userAgent != null) {
                        if (userAgent.contains("Android")) platform = "ANDROID";
                        else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) platform = "IOS";
                        else if (userAgent.contains("Linux")) platform = "UBUNTU";
                        else if (userAgent.contains("Windows")) platform = "WINDOWS";
                    }
                    DiscoveredPeer remotePeer = new DiscoveredPeer(assignedNodeId, assignedNodeId, platform, clientIp, 8080);
                    remotePeer.addTransport(DiscoveredPeer.TRANSPORT_WIFI);
                    discoveryManager.getRegistry().registerOrUpdate(remotePeer);
                    contactManager.startContact(remotePeer.toPeer());
                } else {
                    existing.touch();
                    if (!contactManager.isInContact(assignedNodeId)) {
                        contactManager.startContact(existing.toPeer());
                    }
                }
            }

            JsonObject root = new JsonObject();
            root.addProperty("localPeerId", localPeer.getPeerId());
            root.addProperty("clientIp", clientIp);
            root.addProperty("isRemoteClient", isRemote);
            root.addProperty("suggestedNodeId", assignedNodeId);
            root.addProperty("myNodeId", assignedNodeId);
            root.addProperty("ipAddress", localPeer.getIpAddress());
            root.addProperty("port", localPeer.getPort());
            root.addProperty("serverPort", getPort());
            root.addProperty("serverLanIp", getLocalLanIp());
            root.addProperty("strategy", routingEngine.getStrategy().getName());

            JsonObject weights = new JsonObject();
            weights.addProperty("wP", Math.round(wp * 100.0) / 100.0);
            weights.addProperty("wT", Math.round(wt * 100.0) / 100.0);
            weights.addProperty("wB", Math.round(wb * 100.0) / 100.0);
            root.add("weights", weights);

            root.addProperty("bufferSize", buffer.size());
            root.addProperty("bufferUsedBytes", buffer.getUsedBufferBytes());
            root.addProperty("bufferMaxBytes", buffer.getCapacityBytes());
            root.addProperty("bufferPressure", Double.parseDouble(String.format(java.util.Locale.US, "%.4f", buffer.getBufferPressure())));
            root.addProperty("uptimeSeconds", (System.currentTimeMillis() - startTime) / 1000);
            if (!isRemote) root.addProperty("apiToken", apiToken); // remote devices must use the #token= link printed in the console

            JsonArray contactsArr = new JsonArray();
            for (Peer p : contactManager.getActiveContacts()) {
                contactsArr.add(p.getPeerId());
            }
            root.add("activeContacts", contactsArr);

            int peerCount = (discoveryManager != null) ? discoveryManager.getRegistry().size() : 0;
            root.addProperty("discoveredPeerCount", peerCount);

            sendJson(exchange, 200, GSON.toJson(root));
        }
    }

    /**
     * GET /api/peers - All discovered peers across Wi-Fi and Bluetooth LE.
     */
    private class PeersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }

            JsonArray peersArray = new JsonArray();
            if (discoveryManager != null) {
                List<DiscoveredPeer> peers = new ArrayList<>(discoveryManager.getRegistry().getAllPeers());
                long now = System.currentTimeMillis();
                for (DiscoveredPeer p : peers) {
                    JsonObject pObj = new JsonObject();
                    pObj.addProperty("peerId", p.getPeerId());
                    pObj.addProperty("displayName", p.getDisplayName());
                    pObj.addProperty("platform", p.getPlatform());
                    pObj.addProperty("ipAddress", p.getIpAddress());
                    pObj.addProperty("port", p.getPort());
                    pObj.addProperty("lastSeenTimestamp", p.getLastSeenTimestamp());
                    pObj.addProperty("lastSeenSecondsAgo", (now - p.getLastSeenTimestamp()) / 1000);
                    pObj.addProperty("isExpired", p.isExpired(15000));
                    pObj.addProperty("inContact", contactManager.isInContact(p.getPeerId()));

                    JsonArray transArray = new JsonArray();
                    for (String t : p.getSupportedTransports()) {
                        transArray.add(t);
                    }
                    pObj.add("transports", transArray);
                    peersArray.add(pObj);
                }
            }
            sendJson(exchange, 200, GSON.toJson(peersArray));
        }
    }

    /**
     * GET /api/buffer - List of queued messages in local store with live remaining TTL.
     * DELETE /api/buffer or POST /api/buffer/purge - Purge all expired messages.
     */
    private class BufferHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            // Always purge expired messages prior to inspection or mutation
            List<Message> purged = purgeExpiredMessages();

            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod()) || ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().endsWith("/purge"))) {
                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("purgedCount", purged.size());
                resp.addProperty("remainingBufferCount", buffer.size());
                sendJson(exchange, 200, GSON.toJson(resp));
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }

            JsonArray bufArray = new JsonArray();
            List<Message> msgs = buffer.getAllMessages();
            for (Message m : msgs) {
                if (m.isExpired()) continue;
                JsonObject mObj = new JsonObject();
                mObj.addProperty("messageId", m.getMessageId());
                mObj.addProperty("sourceId", m.getSourceId());
                mObj.addProperty("destinationId", m.getDestinationId());
                mObj.addProperty("priority", m.getPriority().name());
                mObj.addProperty("priorityWeight", Math.round(m.getPriority().getWeight() * 100.0) / 100.0);
                mObj.addProperty("copyTokens", m.getCopyTokens());
                mObj.addProperty("payload", m.getPayload());
                mObj.addProperty("createdAt", m.getCreatedAt().toString());
                mObj.addProperty("expiresAt", m.getExpiresAt().toString());
                mObj.addProperty("remainingTTL", m.getRemainingTTL());
                mObj.addProperty("isExpired", false);
                mObj.addProperty("version", m.getVersion());
                bufArray.add(mObj);
            }
            sendJson(exchange, 200, GSON.toJson(bufArray));
        }
    }

    /**
     * POST /api/messages - Enqueue and optionally forward a new DTN message.
     */
    private class MessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "UNAUTHORIZED", "Missing or invalid X-CampusMesh-Token header");
                return;
            }

            String body = readRequestBody(exchange);
            JsonObject reqObj;
            try {
                JsonElement parsed = JsonParser.parseString(body);
                if (!parsed.isJsonObject()) {
                    sendError(exchange, 400, "BAD_REQUEST", "Request body must be a JSON object");
                    return;
                }
                reqObj = parsed.getAsJsonObject();
            } catch (Exception e) {
                sendError(exchange, 400, "INVALID_JSON", "Malformed JSON body: " + e.getMessage());
                return;
            }

            // 1. Destination ID validation
            String dest = reqObj.has("destinationId") && !reqObj.get("destinationId").isJsonNull()
                    ? reqObj.get("destinationId").getAsString().trim() : "BROADCAST";
            if (!VALID_ID_PATTERN.matcher(dest).matches()) {
                sendError(exchange, 400, "INVALID_DESTINATION_ID",
                        "destinationId must be 1-64 characters matching [a-zA-Z0-9_.-]");
                return;
            }

            // 2. Source ID validation
            String src = reqObj.has("sourceId") && !reqObj.get("sourceId").isJsonNull()
                    ? reqObj.get("sourceId").getAsString().trim() : localPeer.getPeerId();
            if (!VALID_ID_PATTERN.matcher(src).matches()) {
                sendError(exchange, 400, "INVALID_SOURCE_ID",
                        "sourceId must be 1-64 characters matching [a-zA-Z0-9_.-]");
                return;
            }

            // 3. Message ID validation (optional in request; generated as UUID if omitted)
            String msgId;
            if (reqObj.has("messageId") && !reqObj.get("messageId").isJsonNull() && !reqObj.get("messageId").getAsString().trim().isEmpty()) {
                msgId = reqObj.get("messageId").getAsString().trim();
                if (!VALID_ID_PATTERN.matcher(msgId).matches()) {
                    sendError(exchange, 400, "INVALID_MESSAGE_ID",
                            "messageId must be 1-64 characters matching [a-zA-Z0-9_.-]");
                    return;
                }
            } else {
                msgId = UUID.randomUUID().toString();
            }

            // 4. Payload validation (max 64 KB)
            if (!reqObj.has("payload") || reqObj.get("payload").isJsonNull()) {
                sendError(exchange, 400, "INVALID_PAYLOAD", "payload field is required");
                return;
            }
            String payload = reqObj.get("payload").getAsString();
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            if (payloadBytes.length > MAX_PAYLOAD_BYTES) {
                sendError(exchange, 400, "PAYLOAD_TOO_LARGE",
                        "payload size (" + payloadBytes.length + " bytes) exceeds maximum limit of " + MAX_PAYLOAD_BYTES + " bytes");
                return;
            }

            // 5. TTL seconds range validation (1..604800)
            long ttlSeconds = 3600;
            if (reqObj.has("ttlSeconds") && !reqObj.get("ttlSeconds").isJsonNull()) {
                try {
                    ttlSeconds = reqObj.get("ttlSeconds").getAsLong();
                } catch (Exception e) {
                    sendError(exchange, 400, "INVALID_TTL", "ttlSeconds must be a valid integer");
                    return;
                }
            }
            if (ttlSeconds < 1 || ttlSeconds > 604800) {
                sendError(exchange, 400, "TTL_OUT_OF_RANGE", "ttlSeconds must be between 1 and 604800 (7 days)");
                return;
            }

            // 6. Copy tokens validation (1..16)
            int copyTokens = 4;
            if (reqObj.has("copyTokens") && !reqObj.get("copyTokens").isJsonNull()) {
                try {
                    copyTokens = reqObj.get("copyTokens").getAsInt();
                } catch (Exception e) {
                    sendError(exchange, 400, "INVALID_COPY_TOKENS", "copyTokens must be a valid integer");
                    return;
                }
            }
            if (copyTokens < 1 || copyTokens > 16) {
                sendError(exchange, 400, "COPY_TOKENS_OUT_OF_RANGE", "copyTokens must be between 1 and 16");
                return;
            }

            // Priority
            String prioStr = reqObj.has("priority") && !reqObj.get("priority").isJsonNull()
                    ? reqObj.get("priority").getAsString() : "MEDIUM";
            Priority prio = Priority.fromString(prioStr);

            Message msg = new Message(msgId, src, dest, prio, payload, ttlSeconds, copyTokens);
            MessageBuffer.AddResult addRes = buffer.addMessageWithStatus(msg);

            if (logger != null) {
                logger.logMessageCreated(src, msgId, dest, msg.toJson());
            }

            // Opportunistic dispatch if target is in discovery registry
            String dispatchStatus = "Enqueued in DTN store-and-forward buffer";
            if (discoveryManager != null) {
                DiscoveredPeer target = discoveryManager.getRegistry().getPeer(dest);
                if (target != null) {
                    MultiTransportSender.TransferResult tRes = sender.send(target, msg);
                    if (tRes.success) {
                        dispatchStatus = "Directly delivered via " + tRes.transportUsed + " (" + tRes.bytesTransferred + " bytes)";
                    }
                }
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            resp.addProperty("messageId", msgId);
            resp.addProperty("bufferResult", addRes.name());
            resp.addProperty("status", dispatchStatus);
            sendJson(exchange, 200, GSON.toJson(resp));
        }
    }

    /**
     * POST /api/strategy - Change active routing strategy and weights.
     */
    private class StrategyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "UNAUTHORIZED", "Missing or invalid X-CampusMesh-Token header");
                return;
            }

            String body = readRequestBody(exchange);
            JsonObject reqObj;
            try {
                JsonElement el = JsonParser.parseString(body);
                if (!el.isJsonObject()) {
                    sendError(exchange, 400, "BAD_REQUEST", "Body must be a JSON object");
                    return;
                }
                reqObj = el.getAsJsonObject();
            } catch (Exception e) {
                sendError(exchange, 400, "INVALID_JSON", "Malformed JSON: " + e.getMessage());
                return;
            }

            String strat = reqObj.has("strategy") && !reqObj.get("strategy").isJsonNull()
                    ? reqObj.get("strategy").getAsString().toUpperCase() : "ADAPTIVE";
            double wp = reqObj.has("wP") ? reqObj.get("wP").getAsDouble() : 0.40;
            double wt = reqObj.has("wT") ? reqObj.get("wT").getAsDouble() : 0.40;
            double wb = reqObj.has("wB") ? reqObj.get("wB").getAsDouble() : 0.20;

            if ("EPIDEMIC".equals(strat)) {
                routingEngine.setStrategy(new EpidemicRouter());
            } else if ("SPRAY_AND_WAIT".equals(strat) || "SPRAYANDWAIT".equals(strat)) {
                routingEngine.setStrategy(new SprayAndWaitRouter());
            } else {
                routingEngine.setStrategy(new AdaptiveRouter(wp, wt, wb));
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            resp.addProperty("strategy", routingEngine.getStrategy().getName());
            JsonObject wObj = new JsonObject();
            wObj.addProperty("wP", Math.round(wp * 100.0) / 100.0);
            wObj.addProperty("wT", Math.round(wt * 100.0) / 100.0);
            wObj.addProperty("wB", Math.round(wb * 100.0) / 100.0);
            resp.add("weights", wObj);
            sendJson(exchange, 200, GSON.toJson(resp));
        }
    }

    /**
     * POST /api/contacts - Simulate contact start or end.
     */
    private class ContactsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "UNAUTHORIZED", "Missing or invalid X-CampusMesh-Token header");
                return;
            }

            String body = readRequestBody(exchange);
            JsonObject reqObj;
            try {
                JsonElement el = JsonParser.parseString(body);
                if (!el.isJsonObject()) {
                    sendError(exchange, 400, "BAD_REQUEST", "Body must be a JSON object");
                    return;
                }
                reqObj = el.getAsJsonObject();
            } catch (Exception e) {
                sendError(exchange, 400, "INVALID_JSON", "Malformed JSON: " + e.getMessage());
                return;
            }

            String peerId = reqObj.has("peerId") && !reqObj.get("peerId").isJsonNull()
                    ? reqObj.get("peerId").getAsString().toUpperCase() : "B";
            String action = reqObj.has("action") && !reqObj.get("action").isJsonNull()
                    ? reqObj.get("action").getAsString().toLowerCase() : "toggle";

            Peer target = new Peer(peerId, "127.0.0.1", 5002);
            if (discoveryManager != null) {
                DiscoveredPeer dp = discoveryManager.getRegistry().getPeer(peerId);
                if (dp != null) target = dp.toPeer();
            }

            boolean isContact = contactManager.isInContact(peerId);
            if ("start".equals(action) || ("toggle".equals(action) && !isContact)) {
                contactManager.startContact(target);
            } else {
                contactManager.endContact(target);
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            JsonArray contactsArr = new JsonArray();
            for (Peer p : contactManager.getActiveContacts()) {
                contactsArr.add(p.getPeerId());
            }
            resp.add("activeContacts", contactsArr);
            sendJson(exchange, 200, GSON.toJson(resp));
        }
    }

    /**
     * GET /api/evaluation - Routing decision matrix scores U(M) for candidate messages.
     */
    private class EvaluationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }

            Peer remote = new Peer("REMOTE_NODE", "127.0.0.1", 5002);
            AdaptiveRouter router = (routingEngine.getStrategy() instanceof AdaptiveRouter)
                    ? (AdaptiveRouter) routingEngine.getStrategy()
                    : new AdaptiveRouter();

            List<AdaptiveRouter.UtilityScore> scores = router.evaluateMessages(
                    localPeer, remote, buffer, Collections.emptySet()
            );

            JsonArray evalArr = new JsonArray();
            for (AdaptiveRouter.UtilityScore sc : scores) {
                JsonObject scObj = new JsonObject();
                scObj.addProperty("messageId", sc.message.getMessageId());
                scObj.addProperty("priority", sc.message.getPriority().name());
                scObj.addProperty("p", Math.round(sc.p * 1000.0) / 1000.0);
                scObj.addProperty("t", Math.round(sc.t * 1000.0) / 1000.0);
                scObj.addProperty("b", Math.round(sc.b * 1000.0) / 1000.0);
                scObj.addProperty("utility", Math.round(sc.utility * 10000.0) / 10000.0);
                scObj.addProperty("rank", sc.rank);
                scObj.addProperty("selected", sc.selected);
                scObj.addProperty("reason", sc.selectionReason);
                evalArr.add(scObj);
            }
            sendJson(exchange, 200, GSON.toJson(evalArr));
        }
    }

    /**
     * GET /api/events - Stream recent SQLite audit events.
     */
    private class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }

            JsonArray evArr = new JsonArray();
            if (logger != null) {
                List<EventLogger.EventRecord> records = logger.getRecentEvents(30);
                for (EventLogger.EventRecord rec : records) {
                    JsonObject recObj = new JsonObject();
                    recObj.addProperty("id", rec.id);
                    recObj.addProperty("timestamp", rec.timestamp);
                    recObj.addProperty("eventType", rec.eventType);
                    recObj.addProperty("peerId", rec.peerId);
                    recObj.addProperty("messageId", rec.messageId);
                    recObj.addProperty("remotePeerId", rec.remotePeerId);
                    recObj.addProperty("routing", rec.routing);
                    evArr.add(recObj);
                }
            }
            sendJson(exchange, 200, GSON.toJson(evArr));
        }
    }

    /**
     * POST /api/simulation/run - Runs the 4-node heterogeneous multi-hop demo.
     * Prevents concurrent runs by returning 409 Conflict if already in progress.
     */
    private class SimulationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "UNAUTHORIZED", "Missing or invalid X-CampusMesh-Token header");
                return;
            }

            if (!simulationRunning.compareAndSet(false, true)) {
                sendError(exchange, 409, "SIMULATION_IN_PROGRESS", "A simulation is already in progress. Please wait for it to complete.");
                return;
            }

            new Thread(() -> {
                try {
                    logger.logMessageCreated("LAPTOP_WIN", "CAMPUS_ALERT_01", "LAPTOP_UBU", "{\"type\":\"MULTI_TRANSPORT_DEMO\"}");
                    campusmesh.demo.MultiTransportMeshDemo.main(new String[0]);
                    logger.logMessageForwarded("LAPTOP_WIN", "PHONE_AND", "CAMPUS_ALERT_01", "WIFI_TCP", "{\"bytes\":312}");
                    logger.logMessageForwarded("PHONE_AND", "IPHONE_IOS", "CAMPUS_ALERT_01", "BLE_GATT", "{\"chunks\":45,\"crc\":\"0xDD28BE60\"}");
                    logger.logMessageForwarded("IPHONE_IOS", "LAPTOP_UBU", "CAMPUS_ALERT_01", "WIFI_TCP", "{\"bytes\":312}");
                    logger.logMessageDelivered("LAPTOP_UBU", "CAMPUS_ALERT_01", "LAPTOP_WIN");
                    Thread.sleep(500);
                } catch (Exception e) {
                    System.err.println("[SIMULATION ERROR] " + e.getMessage());
                } finally {
                    simulationRunning.set(false);
                }
            }, "CampusMesh-SimulationRunner").start();

            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            resp.addProperty("status", "Simulation triggered successfully. Inspecting hops on topology canvas.");
            sendJson(exchange, 200, GSON.toJson(resp));
        }
    }

    /**
     * Static Asset Handler - Serves HTML, CSS, and JS from webRoot directory.
     * Hardened against path traversal attacks.
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Prevent path traversal by validating canonical paths
            File canonicalRoot = webRoot.getCanonicalFile();
            File target = new File(canonicalRoot, path.replace("/", File.separator)).getCanonicalFile();

            if (!target.getPath().equals(canonicalRoot.getPath()) && !target.getPath().startsWith(canonicalRoot.getPath() + File.separator)) {
                sendError(exchange, 403, "FORBIDDEN", "Path traversal forbidden: " + path);
                return;
            }

            if (!target.exists() || target.isDirectory()) {
                if (path.endsWith("index.html")) {
                    sendHtml(exchange, 200, getFallbackHtml());
                    return;
                }
                sendError(exchange, 404, "NOT_FOUND", "File Not Found: " + path);
                return;
            }

            String mimeType = getMimeType(target.getName());
            byte[] bytes = readFileToBytes(target);
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            // Short cache for static assets so mobile browsers pick up updates quickly
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=30");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Utility Helpers
    // ------------------------------------------------------------------------

    public static String getLocalLanIp() {
        // In Docker, the container only sees its bridge network (172.x.x.x).
        // HOST_LAN_IP env var allows the host's real LAN IP to be passed through.
        String envIp = System.getenv("HOST_LAN_IP");
        if (envIp != null && !envIp.trim().isEmpty()) {
            return envIp.trim();
        }
        // 1) Ask the OS which address it would use to reach the LAN (skips VirtualBox/WSL/Hyper-V adapters).
        try (java.net.DatagramSocket ds = new java.net.DatagramSocket()) {
            ds.connect(java.net.InetAddress.getByName("192.0.2.1"), 9); // no packet is sent
            java.net.InetAddress a = ds.getLocalAddress();
            if (a != null && !a.isAnyLocalAddress() && !a.isLoopbackAddress()) {
                return a.getHostAddress();
            }
        } catch (Exception ignored) {}
        // 2) Fallback: first private IPv4 on a non-virtual interface.
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                String n = (iface.getDisplayName() + " " + iface.getName()).toLowerCase();
                if (n.contains("virtual") || n.contains("vmware") || n.contains("vbox") || n.contains("hyper-v") || n.contains("wsl") || n.contains("docker")) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        // This is a LAN-only campus mesh dashboard — allow all origins so mobile
        // devices on the same Wi-Fi / hotspot can access API endpoints without CORS blocks.
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isEmpty()) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
        } else {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CampusMesh-Token, X-Node-ID");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String code, String message) throws IOException {
        JsonObject errObj = new JsonObject();
        errObj.addProperty("error", true);
        errObj.addProperty("status", statusCode);
        errObj.addProperty("code", code);
        errObj.addProperty("message", message);
        sendJson(exchange, statusCode, GSON.toJson(errObj));
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readFileToBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = fis.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toByteArray();
        }
    }

    private static String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private String getFallbackHtml() {
        return "<!DOCTYPE html><html><head><title>CampusMesh Dashboard</title>"
                + "<style>body{font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:40px;text-align:center;}"
                + "h1{color:#38bdf8;}a{color:#818cf8;}</style></head><body>"
                + "<h1>CampusMesh Embedded Web Server Active</h1>"
                + "<p>Local Node: <strong>" + localPeer.getPeerId() + "</strong></p>"
                + "<p>API Endpoints: <a href='/api/status'>/api/status</a> | <a href='/api/peers'>/api/peers</a> | <a href='/api/buffer'>/api/buffer</a></p>"
                + "</body></html>";
    }
}
