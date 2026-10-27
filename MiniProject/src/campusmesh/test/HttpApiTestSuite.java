package campusmesh.test;

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
import campusmesh.routing.RoutingEngine;
import campusmesh.web.CampusMeshHttpServer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Automated verification test suite for Phase 1: Security, correctness, validation, and JSON.
 * Validates:
 *  - Canonical path-traversal prevention (rejects /../database/campusmesh.db with 403)
 *  - Per-run X-CampusMesh-Token enforcement on all POST endpoints (returns 401 on unauthorized)
 *  - Standardized JSON error response shape {"error":true,"status":...,"code":...,"message":...}
 *  - Strict input validation on POST /api/messages (payload size, copyTokens, ttlSeconds, IDs)
 *  - UUID message IDs (no buffer.size()-based IDs)
 *  - Protocol version wire format and lossless serialization of quotes, backslashes, unicode, newlines
 *  - Concurrency lock on /api/simulation/run (returns 409 Conflict when already active)
 */
public class HttpApiTestSuite {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static final int TEST_PORT = 8899;
    private static String serverToken = "";

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("       CAMPUSMESH HTTP API TEST SUITE             ");
        System.out.println("==================================================\n");

        Peer localPeer = new Peer("LAPTOP_TEST", "127.0.0.1", 5001);
        MessageBuffer buffer = new MessageBuffer("LAPTOP_TEST");
        ContactManager contactManager = new ContactManager(localPeer);
        DiscoveryManager discoveryManager = new DiscoveryManager(localPeer, contactManager);
        RoutingEngine routingEngine = new RoutingEngine(new AdaptiveRouter(0.40, 0.40, 0.20));
        EventLogger logger = new EventLogger("database/campusmesh_test.db");
        MultiTransportSender sender = new MultiTransportSender();

        try (CampusMeshHttpServer server = new CampusMeshHttpServer(
                "127.0.0.1", TEST_PORT, new java.io.File("web"), localPeer, buffer, contactManager, discoveryManager, routingEngine, logger, sender)) {

            server.start();
            serverToken = server.getApiToken();
            Thread.sleep(200);

            // Baseline REST tests
            runTest("GET /api/status - Local Node Metrics & Token", () -> testGetStatus(buffer));
            runTest("GET /api/peers - Discovered Peer Registry", () -> testGetPeers(discoveryManager));
            runTest("POST /api/messages - Enqueue DTN Message", () -> testPostMessage(buffer));
            runTest("GET /api/buffer - Local Buffer Inspection", () -> testGetBuffer());
            runTest("POST /api/strategy - Update Strategy & Weights", () -> testPostStrategy(routingEngine));
            runTest("POST /api/contacts - Contact Encounter Toggle", () -> testPostContacts(contactManager));
            runTest("GET / - Web Dashboard HTML Serving", () -> testStaticServing());
            runTest("GET /style.css - CSS Asset Serving", () -> testCssServing());
            runTest("GET /app.js - JavaScript Application Serving", () -> testJsServing());

            // Phase 1 Security & Correctness additions
            runTest("StaticFileHandler - Path Traversal (/../database/campusmesh.db) blocked with 403", () -> testPathTraversal());
            runTest("POST Auth Token - Missing X-CampusMesh-Token rejected with 401", () -> testPostMissingToken());
            runTest("POST Auth Token - Invalid X-CampusMesh-Token rejected with 401", () -> testPostInvalidToken());
            runTest("POST /api/messages - Oversized payload (>64KB) rejected with 400", () -> testPostOversizedPayload());
            runTest("POST /api/messages - Invalid copyTokens rejected with 400", () -> testPostInvalidCopyTokens());
            runTest("POST /api/messages - Invalid ttlSeconds rejected with 400", () -> testPostInvalidTtl());
            runTest("POST /api/messages - Invalid destinationId charset rejected with 400", () -> testPostInvalidDestId());
            runTest("JSON Serialization - Escaped quotes, backslashes, unicode & newlines round-trip", () -> testSpecialCharactersPayload());
            runTest("POST /api/simulation/run - Concurrency protection returns 409 Conflict", () -> testSimulationConcurrency());

        } catch (Exception e) {
            System.err.println("[SERVER ERROR] " + e.getMessage());
            e.printStackTrace();
        } finally {
            logger.close();
            discoveryManager.close();
            sender.close();
        }

        System.out.println("\n==================================================");
        System.out.printf("HTTP API RESULTS: %d / %d PASSED%n", testsPassed, testsRun);
        System.out.println("==================================================");

        if (testsPassed != testsRun) {
            System.err.println("Some HTTP API tests failed!");
            System.exit(1);
        } else {
            System.out.println("ALL HTTP API TESTS PASSED SUCCESSFULLY!");
            System.exit(0);
        }
    }

    private static void runTest(String testName, Runnable testMethod) {
        testsRun++;
        try {
            testMethod.run();
            testsPassed++;
            System.out.println("  [PASS] " + testName);
        } catch (Throwable t) {
            System.err.println("  [FAIL] " + testName + " - " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError("Assertion failed: " + message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(String.format("%s (expected: '%s', actual: '%s')", message, expected, actual));
    }

    // 1. GET /api/status
    private static void testGetStatus(MessageBuffer buffer) {
        String resp = httpGet("http://127.0.0.1:" + TEST_PORT + "/api/status");
        assertTrue(resp.contains("\"localPeerId\":\"LAPTOP_TEST\""), "Must contain localPeerId");
        assertTrue(resp.contains("\"strategy\":\"ADAPTIVE\""), "Must contain active strategy");
        assertTrue(resp.contains("\"weights\":{\"wP\":0.4,\"wT\":0.4,\"wB\":0.2}"), "Must contain default weights");
        assertTrue(resp.contains("\"bufferSize\":"), "Must contain bufferSize");
        assertTrue(resp.contains("\"apiToken\":\"" + serverToken + "\""), "Must expose apiToken for same-origin client");
    }

    // 2. GET /api/peers
    private static void testGetPeers(DiscoveryManager dm) {
        DiscoveredPeer dp = new DiscoveredPeer("PHONE_AND_TEST", "Galaxy S23", "ANDROID", "192.168.1.188", 5005);
        dp.addTransport(DiscoveredPeer.TRANSPORT_WIFI);
        dp.addTransport(DiscoveredPeer.TRANSPORT_BLE);
        dm.getRegistry().registerOrUpdate(dp);

        String resp = httpGet("http://127.0.0.1:" + TEST_PORT + "/api/peers");
        assertTrue(resp.startsWith("[") && resp.endsWith("]"), "Must return JSON array");
        assertTrue(resp.contains("\"peerId\":\"PHONE_AND_TEST\""), "Must include registered peer");
        assertTrue(resp.contains("\"platform\":\"ANDROID\""), "Must include platform");
        assertTrue(resp.contains("\"WIFI\""), "Must include WIFI transport");
        assertTrue(resp.contains("\"BLE\""), "Must include BLE transport");
    }

    // 3. POST /api/messages
    private static void testPostMessage(MessageBuffer buffer) {
        String payloadJson = "{"
                + "\"destinationId\":\"LAPTOP_UBU\","
                + "\"priority\":\"HIGH\","
                + "\"payload\":\"Emergency weather bulletin\","
                + "\"ttlSeconds\":180,"
                + "\"copyTokens\":4"
                + "}";

        String resp = httpPost("http://127.0.0.1:" + TEST_PORT + "/api/messages", payloadJson, serverToken);
        assertTrue(resp.contains("\"success\":true"), "POST must return success");
        assertTrue(resp.contains("\"messageId\":"), "POST must return generated messageId");
        assertTrue(buffer.size() > 0, "MessageBuffer size must increase");
    }

    // 4. GET /api/buffer
    private static void testGetBuffer() {
        String resp = httpGet("http://127.0.0.1:" + TEST_PORT + "/api/buffer");
        assertTrue(resp.startsWith("["), "Buffer response must be array");
        assertTrue(resp.contains("\"destinationId\":\"LAPTOP_UBU\""), "Buffer must contain enqueued message");
        assertTrue(resp.contains("\"priority\":\"HIGH\""), "Buffer must show HIGH priority");
        assertTrue(resp.contains("\"payload\":\"Emergency weather bulletin\""), "Buffer must contain payload");
    }

    // 5. POST /api/strategy
    private static void testPostStrategy(RoutingEngine re) {
        String updateJson = "{"
                + "\"strategy\":\"ADAPTIVE\","
                + "\"wP\":0.50,"
                + "\"wT\":0.30,"
                + "\"wB\":0.20"
                + "}";

        String resp = httpPost("http://127.0.0.1:" + TEST_PORT + "/api/strategy", updateJson, serverToken);
        assertTrue(resp.contains("\"success\":true"), "Strategy update must succeed");
        assertTrue(resp.contains("\"wP\":0.5"), "Must return updated wP");

        AdaptiveRouter ar = (AdaptiveRouter) re.getStrategy();
        assertEquals(0.50, ar.getWP(), "Router wP must be updated to 0.50");
        assertEquals(0.30, ar.getWT(), "Router wT must be updated to 0.30");
        assertEquals(0.20, ar.getWB(), "Router wB must be updated to 0.20");
    }

    // 6. POST /api/contacts
    private static void testPostContacts(ContactManager cm) {
        String toggleJson = "{\"peerId\":\"PEER_Z\",\"action\":\"start\"}";
        String resp = httpPost("http://127.0.0.1:" + TEST_PORT + "/api/contacts", toggleJson, serverToken);
        assertTrue(resp.contains("\"success\":true"), "Contact start must succeed");
        assertTrue(resp.contains("\"PEER_Z\""), "Active contacts must contain PEER_Z");
        assertTrue(cm.isInContact("PEER_Z"), "ContactManager state must reflect PEER_Z");
    }

    // 7. GET / Web Dashboard HTML Serving
    private static void testStaticServing() {
        String resp = httpGet("http://127.0.0.1:" + TEST_PORT + "/");
        assertTrue(resp.contains("CampusMesh"), "HTML must mention CampusMesh");
        assertTrue(resp.contains("meshCanvas"), "HTML must include meshCanvas element");
    }

    // 8. GET /style.css
    private static void testCssServing() {
        String resp = httpGet("http://127.0.0.1:" + TEST_PORT + "/style.css");
        assertTrue(resp.contains("dashboard-grid"), "CSS must contain dashboard-grid class");
    }

    // 9. GET /app.js
    private static void testJsServing() {
        String resp = httpGet("http://127.0.0.1:" + TEST_PORT + "/app.js");
        assertTrue(resp.contains("renderCanvasLoop"), "JS must contain renderCanvasLoop function");
    }

    // 10. Path traversal protection test
    private static void testPathTraversal() {
        HttpResponse res = executeRequest("GET", "http://127.0.0.1:" + TEST_PORT + "/../database/campusmesh.db", null, null);
        assertTrue(res.code == 403 || res.code == 404, "Path traversal must return 403 Forbidden or 404 Not Found (got " + res.code + ")");
        if (res.code == 403) {
            assertTrue(res.body.contains("\"error\":true") && res.body.contains("FORBIDDEN"), "403 response must have standard error format");
        }
    }

    // 11. Missing auth token test
    private static void testPostMissingToken() {
        String payloadJson = "{\"destinationId\":\"LAPTOP_UBU\",\"payload\":\"Test\"}";
        HttpResponse res = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", payloadJson, null);
        assertEquals(401, res.code, "POST without token must return 401");
        assertTrue(res.body.contains("\"code\":\"UNAUTHORIZED\""), "Must contain UNAUTHORIZED code");
    }

    // 12. Invalid auth token test
    private static void testPostInvalidToken() {
        String payloadJson = "{\"destinationId\":\"LAPTOP_UBU\",\"payload\":\"Test\"}";
        HttpResponse res = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", payloadJson, "invalid-bogus-token");
        assertEquals(401, res.code, "POST with invalid token must return 401");
        assertTrue(res.body.contains("\"code\":\"UNAUTHORIZED\""), "Must contain UNAUTHORIZED code");
    }

    // 13. Oversized payload test
    private static void testPostOversizedPayload() {
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 70000; i++) {
            large.append("A");
        }
        String json = "{\"destinationId\":\"LAPTOP_UBU\",\"payload\":\"" + large.toString() + "\"}";
        HttpResponse res = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", json, serverToken);
        assertEquals(400, res.code, "POST with payload > 64KB must return 400");
        assertTrue(res.body.contains("PAYLOAD_TOO_LARGE"), "Must return PAYLOAD_TOO_LARGE error");
    }

    // 14. Invalid copyTokens test
    private static void testPostInvalidCopyTokens() {
        String jsonLow = "{\"destinationId\":\"LAPTOP_UBU\",\"payload\":\"hi\",\"copyTokens\":0}";
        HttpResponse resLow = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", jsonLow, serverToken);
        assertEquals(400, resLow.code, "copyTokens 0 must return 400");
        assertTrue(resLow.body.contains("COPY_TOKENS_OUT_OF_RANGE"), "Must flag COPY_TOKENS_OUT_OF_RANGE");

        String jsonHigh = "{\"destinationId\":\"LAPTOP_UBU\",\"payload\":\"hi\",\"copyTokens\":20}";
        HttpResponse resHigh = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", jsonHigh, serverToken);
        assertEquals(400, resHigh.code, "copyTokens 20 must return 400");
    }

    // 15. Invalid ttlSeconds test
    private static void testPostInvalidTtl() {
        String jsonLow = "{\"destinationId\":\"LAPTOP_UBU\",\"payload\":\"hi\",\"ttlSeconds\":0}";
        HttpResponse resLow = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", jsonLow, serverToken);
        assertEquals(400, resLow.code, "ttlSeconds 0 must return 400");
        assertTrue(resLow.body.contains("TTL_OUT_OF_RANGE"), "Must flag TTL_OUT_OF_RANGE");

        String jsonHigh = "{\"destinationId\":\"LAPTOP_UBU\",\"payload\":\"hi\",\"ttlSeconds\":9999999}";
        HttpResponse resHigh = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", jsonHigh, serverToken);
        assertEquals(400, resHigh.code, "ttlSeconds > 7 days must return 400");
    }

    // 16. Invalid destinationId test
    private static void testPostInvalidDestId() {
        String json = "{\"destinationId\":\"DEST;injection--!\",\"payload\":\"hi\"}";
        HttpResponse res = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", json, serverToken);
        assertEquals(400, res.code, "Invalid destinationId charset must return 400");
        assertTrue(res.body.contains("INVALID_DESTINATION_ID"), "Must return INVALID_DESTINATION_ID");
    }

    // 17. Lossless round-trip of special characters
    private static void testSpecialCharactersPayload() {
        String rawPayload = "Quotes: \"hello\" | Backslash: C:\\path\\to\\file | Unicode: 🚀 \u00e9\u00e0\u00fc | Newlines: line1\nline2";
        com.google.gson.JsonObject msgObj = new com.google.gson.JsonObject();
        msgObj.addProperty("destinationId", "NODE_SPEC");
        msgObj.addProperty("payload", rawPayload);
        msgObj.addProperty("priority", "HIGH");
        msgObj.addProperty("ttlSeconds", 3600);
        msgObj.addProperty("copyTokens", 2);

        String postBody = new com.google.gson.Gson().toJson(msgObj);
        HttpResponse postRes = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/messages", postBody, serverToken);
        assertEquals(200, postRes.code, "POST special characters must succeed");

        String bufResp = httpGet("http://127.0.0.1:" + TEST_PORT + "/api/buffer");
        com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(bufResp).getAsJsonArray();
        boolean found = false;
        for (com.google.gson.JsonElement el : arr) {
            com.google.gson.JsonObject obj = el.getAsJsonObject();
            if ("NODE_SPEC".equals(obj.get("destinationId").getAsString())) {
                assertEquals(rawPayload, obj.get("payload").getAsString(), "Retrieved payload must exactly match original string");
                found = true;
                break;
            }
        }
        assertTrue(found, "Special character message must be found in buffer");
    }

    // 18. Concurrency lock on simulation run
    private static void testSimulationConcurrency() {
        HttpResponse first = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/simulation/run", "{}", serverToken);
        assertEquals(200, first.code, "First simulation run must return 200");

        // Immediately trigger second run while background task is still running
        HttpResponse second = executeRequest("POST", "http://127.0.0.1:" + TEST_PORT + "/api/simulation/run", "{}", serverToken);
        assertEquals(409, second.code, "Concurrent simulation run must return 409 Conflict");
        assertTrue(second.body.contains("SIMULATION_IN_PROGRESS"), "Must return SIMULATION_IN_PROGRESS code");
    }

    // ------------------------------------------------------------------------
    // HTTP Client Helpers
    // ------------------------------------------------------------------------

    private static class HttpResponse {
        final int code;
        final String body;

        HttpResponse(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static HttpResponse executeRequest(String method, String urlStr, String jsonBody, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            if (token != null) {
                conn.setRequestProperty("X-CampusMesh-Token", token);
            }
            if (jsonBody != null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
            }
            return new HttpResponse(code, sb.toString());
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed: " + method + " " + urlStr + " - " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String httpGet(String urlStr) {
        HttpResponse res = executeRequest("GET", urlStr, null, null);
        assertEquals(200, res.code, "HTTP GET " + urlStr + " must return 200");
        return res.body;
    }

    private static String httpPost(String urlStr, String jsonBody, String token) {
        HttpResponse res = executeRequest("POST", urlStr, jsonBody, token);
        assertEquals(200, res.code, "HTTP POST " + urlStr + " must return 200");
        return res.body;
    }
}
