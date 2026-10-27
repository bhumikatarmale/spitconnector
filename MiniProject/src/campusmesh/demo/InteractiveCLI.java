package campusmesh.demo;

import campusmesh.buffer.MessageBuffer;
import campusmesh.contact.ContactManager;
import campusmesh.logging.EventLogger;
import campusmesh.model.Message;
import campusmesh.model.Peer;
import campusmesh.model.Priority;
import campusmesh.networking.TCPManager;
import campusmesh.discovery.DiscoveredPeer;
import campusmesh.discovery.DiscoveryManager;
import campusmesh.routing.AdaptiveRouter;
import campusmesh.routing.EpidemicRouter;
import campusmesh.routing.RoutingEngine;
import campusmesh.routing.RoutingStrategy;
import campusmesh.routing.SprayAndWaitRouter;

import java.io.File;
import java.net.Socket;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * InteractiveCLI:
 * Interactive console interface for CampusMesh.
 * Allows the user to:
 * 1. Create and send custom messages with custom priorities, TTL, and copy tokens.
 * 2. Select the active routing strategy (Epidemic, Spray-and-Wait, Adaptive).
 * 3. Form and break opportunistic contacts with neighboring peers.
 * 4. Trigger routing evaluations and inspect calculated P, T, B, Utility values.
 * 5. Run hop-by-hop Store-Carry-Forward simulations.
 * 6. Transmit messages over real TCP sockets using 4-byte length prefix framing.
 * 7. Inspect buffer contents and SQLite database event logs.
 */
public class InteractiveCLI {

    private final Peer localPeer;
    private final Map<String, Peer> knownPeers;
    private final MessageBuffer buffer;
    private final Map<String, MessageBuffer> peerBuffers;
    private final ContactManager contactManager;
    private final DiscoveryManager discoveryManager;
    private final EventLogger logger;
    private final Scanner scanner;

    private RoutingStrategy currentStrategy;
    private AdaptiveRouter adaptiveRouter;
    private EpidemicRouter epidemicRouter;
    private SprayAndWaitRouter sprayAndWaitRouter;
    private campusmesh.web.CampusMeshHttpServer webServer;
    private final campusmesh.networking.MultiTransportSender multiTransportSender;

    public InteractiveCLI() {
        Locale.setDefault(Locale.US);
        this.localPeer = new Peer("A", "127.0.0.1", 5001);
        this.knownPeers = new HashMap<>();
        this.knownPeers.put("B", new Peer("B", "127.0.0.1", 5002));
        this.knownPeers.put("C", new Peer("C", "127.0.0.1", 5003));
        this.knownPeers.put("D", new Peer("D", "127.0.0.1", 5004));

        this.buffer = new MessageBuffer(localPeer.getPeerId());
        this.peerBuffers = new HashMap<>();
        this.peerBuffers.put("A", this.buffer);
        this.peerBuffers.put("B", new MessageBuffer("B"));
        this.peerBuffers.put("C", new MessageBuffer("C"));
        this.peerBuffers.put("D", new MessageBuffer("D"));

        this.contactManager = new ContactManager(localPeer);
        this.discoveryManager = new DiscoveryManager(localPeer, contactManager);
        this.logger = new EventLogger("database/campusmesh.db");
        this.multiTransportSender = new campusmesh.networking.MultiTransportSender();
        this.scanner = new Scanner(System.in);

        this.epidemicRouter = new EpidemicRouter();
        this.sprayAndWaitRouter = new SprayAndWaitRouter();
        this.adaptiveRouter = new AdaptiveRouter(0.40, 0.40, 0.20);
        this.currentStrategy = this.adaptiveRouter;

        // Automatically start contact with B by default for convenience
        this.contactManager.startContact(knownPeers.get("B"));
        logger.logPeerStarted(localPeer.getPeerId());
    }

    public static void main(String[] args) {
        InteractiveCLI cli = new InteractiveCLI();

        // Check if command line arguments were provided for non-interactive execution
        if (args.length > 0) {
            cli.handleCommandLineArgs(args);
            return;
        }

        cli.runInteractiveLoop();
    }

    /**
     * Main interactive menu loop.
     */
    public void runInteractiveLoop() {
        System.out.println("==================================================");
        System.out.println("        CAMPUSMESH INTERACTIVE CONSOLE");
        System.out.println("  Opportunistic Store-Carry-Forward Terminal");
        System.out.println("==================================================");

        boolean running = true;
        while (running) {
            printStatusSummary();
            printMenu();
            System.out.print("Enter choice (0-14): ");
            String choice = scanner.nextLine().trim();
            System.out.println();

            switch (choice) {
                case "1":
                    handleCreateMessage();
                    break;
                case "2":
                    handleSelectStrategy();
                    break;
                case "3":
                    handleManageContacts();
                    break;
                case "4":
                    handleEvaluateRouting();
                    break;
                case "5":
                    handleStoreCarryForwardDemo();
                    break;
                case "6":
                    handleSendOverTCP();
                    break;
                case "7":
                    handleViewBuffer();
                    break;
                case "8":
                    handleViewDatabaseLogs();
                    break;
                case "9":
                    handlePurgeExpired();
                    break;
                case "10":
                    handleViewNodeBuffer();
                    break;
                case "11":
                    handleDynamicDiscoveryMonitor();
                    break;
                case "12":
                    handleMultiTransportMonitor();
                    break;
                case "13":
                    handleHeterogeneousMeshDemo();
                    break;
                case "14":
                    handleLaunchWebDashboard();
                    break;
                case "0":
                    System.out.println("Exiting CampusMesh Interactive Console. Goodbye!");
                    running = false;
                    break;
                default:
                    System.out.println("[ERROR] Invalid choice. Please enter a number between 0 and 14.\n");
                    break;
            }
        }

        if (webServer != null) {
            webServer.stop();
        }
        multiTransportSender.close();
        discoveryManager.close();
        logger.close();
    }

    private void printStatusSummary() {
        System.out.println("--------------------------------------------------");
        System.out.printf("  Local Peer: %s%n", localPeer);
        System.out.printf("  Active Routing Strategy: %s%n", currentStrategy.getName());
        if (currentStrategy instanceof AdaptiveRouter) {
            AdaptiveRouter ar = (AdaptiveRouter) currentStrategy;
            System.out.printf("  Weights: wP=%.2f, wT=%.2f, wB=%.2f%n", ar.getWP(), ar.getWT(), ar.getWB());
        }
        System.out.printf("  Active Contacts: %s%n", contactManager.getActiveContacts());
        System.out.printf("  Buffer Status: %d messages | %d bytes used (Pressure: %.1f%%)%n",
                buffer.size(), buffer.getUsedBufferBytes(), buffer.getBufferPressure() * 100.0);
        System.out.println("--------------------------------------------------");
    }

    private void printMenu() {
        System.out.println(" [1]  Create & Store New Message (Custom input)");
        System.out.println(" [2]  Select Active Routing Strategy (Epidemic / Spray-and-Wait / Adaptive)");
        System.out.println(" [3]  Manage Contacts (Toggle encounter with Peer B, C, or D)");
        System.out.println(" [4]  Evaluate & Forward Messages (Show Routing Decision Table)");
        System.out.println(" [5]  Run Step-by-Step Store-Carry-Forward (A -> B -> C -> D)");
        System.out.println(" [6]  Send Message Over Real Local TCP Socket (Length-prefixed framing)");
        System.out.println(" [7]  View Local Node (A) Message Buffer Details");
        System.out.println(" [8]  Query SQLite Database Event Logs");
        System.out.println(" [9]  Purge Expired Messages from Buffer");
        System.out.println(" [10] View Messages Stored in Any Selected Node (A, B, C, D)");
        System.out.println(" [11] Dynamic Wi-Fi Peer Discovery Monitor (mDNS & Subnet Beacon)");
        System.out.println(" [12] Multi-Transport Mesh Monitor (Wi-Fi + BLE Radar & Packet Inspection)");
        System.out.println(" [13] Run Heterogeneous Multi-Hop Simulation (Laptop -> Android -> iPhone -> Ubuntu)");
        System.out.println(" [14] Launch Real-Time Web Dashboard GUI (http://localhost:8080)");
        System.out.println(" [0]  Exit");
    }

    // ------------------------------------------------------------------------
    // [1] Create Message
    // ------------------------------------------------------------------------
    private void handleCreateMessage() {
        System.out.println("=== CREATE NEW MESSAGE ===");

        System.out.print("Enter Message ID [default M" + (buffer.size() + 1) + "]: ");
        String msgId = scanner.nextLine().trim();
        if (msgId.isEmpty()) {
            msgId = "M" + (buffer.size() + 1);
        }

        System.out.print("Enter Destination Peer ID (B, C, D) [default D]: ");
        String destId = scanner.nextLine().trim().toUpperCase();
        if (destId.isEmpty()) {
            destId = "D";
        }

        System.out.println("Select Priority Level:");
        System.out.println("  1. LOW    (Score = 0.33)");
        System.out.println("  2. MEDIUM (Score = 0.67)");
        System.out.println("  3. HIGH   (Score = 1.00)");
        System.out.print("Enter choice (1-3) [default 2]: ");
        String pChoice = scanner.nextLine().trim();
        Priority priority = Priority.MEDIUM;
        if ("1".equals(pChoice)) priority = Priority.LOW;
        else if ("3".equals(pChoice)) priority = Priority.HIGH;

        System.out.print("Enter Message Payload [default 'Campus alert for " + destId + "']: ");
        String payload = scanner.nextLine().trim();
        if (payload.isEmpty()) {
            payload = "Campus alert for " + destId;
        }

        System.out.print("Enter TTL in seconds [default 120]: ");
        String ttlStr = scanner.nextLine().trim();
        long ttlSeconds = 120;
        if (!ttlStr.isEmpty()) {
            try {
                ttlSeconds = Math.max(1, Long.parseLong(ttlStr));
            } catch (NumberFormatException e) {
                System.out.println("  Invalid number, using 120 seconds.");
            }
        }

        System.out.print("Enter Copy Tokens (L) [default 4]: ");
        String tokensStr = scanner.nextLine().trim();
        int tokens = 4;
        if (!tokensStr.isEmpty()) {
            try {
                tokens = Math.max(1, Integer.parseInt(tokensStr));
            } catch (NumberFormatException e) {
                System.out.println("  Invalid number, using 4 tokens.");
            }
        }

        Message newMsg = new Message(msgId, localPeer.getPeerId(), destId, priority, payload, ttlSeconds, tokens);
        System.out.println("\n[CREATED MESSAGE]");
        System.out.printf("  ID:           %s%n", newMsg.getMessageId());
        System.out.printf("  Source:       %s -> Destination: %s%n", newMsg.getSourceId(), newMsg.getDestinationId());
        System.out.printf("  Priority:     %s (P=%.2f)%n", newMsg.getPriority(), newMsg.getPriority().getWeight());
        System.out.printf("  Payload:      '%s'%n", newMsg.getPayload());
        System.out.printf("  TTL:          %d seconds (Expires at: %s)%n", newMsg.getRemainingTTL(), newMsg.getExpiresAt());
        System.out.printf("  Copy Tokens:  %d%n", newMsg.getCopyTokens());

        MessageBuffer.AddResult result = buffer.addMessageWithStatus(newMsg);
        if (result == MessageBuffer.AddResult.SUCCESS) {
            System.out.println("  -> Stored in buffer successfully!");
            logger.logMessageCreated(localPeer.getPeerId(), newMsg.getMessageId(), newMsg.getDestinationId(), newMsg.toJson());
            logger.logMessageStored(localPeer.getPeerId(), newMsg.getMessageId());
        } else if (result == MessageBuffer.AddResult.DUPLICATE) {
            System.out.println("  -> [REJECTED] Duplicate message detected!");
            logger.logMessageDuplicate(localPeer.getPeerId(), newMsg.getMessageId());
        } else if (result == MessageBuffer.AddResult.EXPIRED) {
            System.out.println("  -> [REJECTED] Message has already expired!");
            logger.logMessageExpired(localPeer.getPeerId(), newMsg.getMessageId());
        } else if (result == MessageBuffer.AddResult.BUFFER_FULL) {
            System.out.println("  -> [REJECTED] Buffer is full!");
        }
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // [2] Select Strategy
    // ------------------------------------------------------------------------
    private void handleSelectStrategy() {
        System.out.println("=== SELECT ROUTING STRATEGY ===");
        System.out.println("1. Epidemic Routing       (Flooding - selects all non-expired messages)");
        System.out.println("2. Spray-and-Wait Routing (Binary token splitting, halts in Wait phase)");
        System.out.println("3. Adaptive Forwarding    (Utility U = wP*P + wT*T + wB*B, priority & TTL aware)");
        System.out.print("Enter choice (1-3): ");
        String stratChoice = scanner.nextLine().trim();

        switch (stratChoice) {
            case "1":
                currentStrategy = epidemicRouter;
                System.out.println("-> Active strategy set to: EPIDEMIC");
                break;
            case "2":
                currentStrategy = sprayAndWaitRouter;
                System.out.println("-> Active strategy set to: SPRAY_AND_WAIT");
                break;
            case "3":
                System.out.print("Do you want to customize Adaptive weights (wP, wT, wB)? (y/N): ");
                String custom = scanner.nextLine().trim().toLowerCase();
                if ("y".equals(custom)) {
                    promptAdaptiveWeights();
                } else {
                    currentStrategy = adaptiveRouter;
                    System.out.println("-> Active strategy set to: ADAPTIVE (default weights: wP=0.40, wT=0.40, wB=0.20)");
                }
                break;
            default:
                System.out.println("Invalid selection. Keeping current strategy: " + currentStrategy.getName());
                break;
        }
        System.out.println();
    }

    private void promptAdaptiveWeights() {
        try {
            System.out.print("Enter wP (Priority weight, e.g. 0.4): ");
            double wp = Double.parseDouble(scanner.nextLine().trim());
            System.out.print("Enter wT (TTL Urgency weight, e.g. 0.4): ");
            double wt = Double.parseDouble(scanner.nextLine().trim());
            System.out.print("Enter wB (Buffer Pressure weight, e.g. 0.2): ");
            double wb = Double.parseDouble(scanner.nextLine().trim());

            this.adaptiveRouter = new AdaptiveRouter(wp, wt, wb);
            this.currentStrategy = this.adaptiveRouter;
            System.out.printf("-> Active strategy set to: ADAPTIVE (wP=%.2f, wT=%.2f, wB=%.2f)%n", wp, wt, wb);
        } catch (Exception e) {
            System.out.println("Invalid weight input (" + e.getMessage() + "). Restoring default weights.");
            this.adaptiveRouter = new AdaptiveRouter(0.40, 0.40, 0.20);
            this.currentStrategy = this.adaptiveRouter;
        }
    }

    // ------------------------------------------------------------------------
    // [3] Manage Contacts
    // ------------------------------------------------------------------------
    private void handleManageContacts() {
        System.out.println("=== MANAGE CONTACTS ===");
        System.out.println("Known campus peers: B, C, D");
        System.out.printf("Currently active contacts: %s%n%n", contactManager.getActiveContacts());

        System.out.print("Enter Peer ID to toggle contact (B, C, or D): ");
        String peerId = scanner.nextLine().trim().toUpperCase();
        Peer target = knownPeers.get(peerId);

        if (target == null) {
            System.out.println("Unknown peer ID: " + peerId);
            return;
        }

        if (contactManager.isInContact(target)) {
            contactManager.endContact(target);
            logger.logContactEnded(localPeer.getPeerId(), target.getPeerId());
            System.out.printf("Contact terminated with %s.%n", target.getPeerId());
        } else {
            contactManager.startContact(target);
            logger.logContactStarted(localPeer.getPeerId(), target.getPeerId());
            System.out.printf("Contact initiated with %s! Logical link active.%n", target.getPeerId());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // [4] Evaluate Routing & Show Decision
    // ------------------------------------------------------------------------
    private void handleEvaluateRouting() {
        System.out.println("=== EVALUATE ROUTING DECISION ===");

        if (buffer.size() == 0) {
            System.out.println("Message buffer is empty! Please create at least one message first (Option 1).\n");
            return;
        }

        if (contactManager.getActiveContacts().isEmpty()) {
            System.out.println("No active contacts! Please start a contact first (Option 3).\n");
            return;
        }

        Peer remotePeer = contactManager.getActiveContacts().iterator().next();
        System.out.printf("Evaluating for Contact Peer: %s using strategy [%s]%n%n",
                remotePeer.getPeerId(), currentStrategy.getName());

        if (currentStrategy instanceof EpidemicRouter) {
            epidemicRouter.printDecisionDemo(localPeer, remotePeer, buffer, Collections.emptySet());
        } else if (currentStrategy instanceof SprayAndWaitRouter) {
            System.out.println("=== SPRAY-AND-WAIT EVALUATION ===");
            List<Message> selected = sprayAndWaitRouter.selectMessages(localPeer, remotePeer, buffer, Collections.emptySet());
            System.out.printf("Candidate messages in buffer: %d%n", buffer.size());
            System.out.printf("Selected for forwarding:      %d%n%n", selected.size());

            for (Message m : buffer.getAllMessages()) {
                boolean isDest = remotePeer.getPeerId().equalsIgnoreCase(m.getDestinationId());
                if (isDest) {
                    System.out.printf("  %s -> [SELECTED] Direct destination reached! (Will deliver)%n", m.getMessageId());
                } else if (m.getCopyTokens() > 1) {
                    SprayAndWaitRouter.TokenSplitResult split = SprayAndWaitRouter.splitTokens(m.getCopyTokens());
                    System.out.printf("  %s -> [SELECTED] Spray phase active (Tokens: %d -> Sender keeps %d, Remote gets %d)%n",
                            m.getMessageId(), m.getCopyTokens(), split.senderRemaining, split.receiverAssigned);
                } else {
                    System.out.printf("  %s -> [WAIT PHASE] copyTokens = 1. Cannot replicate to relay %s.%n",
                            m.getMessageId(), remotePeer.getPeerId());
                }
            }
        } else if (currentStrategy instanceof AdaptiveRouter) {
            adaptiveRouter.printEvaluationDemo(localPeer, remotePeer, buffer, Collections.emptySet());
        }

        System.out.print("Do you want to perform forwarding and update token counts? (y/N): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if ("y".equals(confirm)) {
            performForwarding(remotePeer);
        }
        System.out.println();
    }

    private void performForwarding(Peer remotePeer) {
        List<Message> toForward = currentStrategy.selectMessages(localPeer, remotePeer, buffer, Collections.emptySet());
        if (toForward.isEmpty()) {
            System.out.println("No messages eligible for forwarding.");
            return;
        }

        System.out.println("\nExecuting Forwarding Action:");
        for (Message m : toForward) {
            if (remotePeer.getPeerId().equalsIgnoreCase(m.getDestinationId())) {
                System.out.printf("  -> Delivering %s directly to Destination %s!%n", m.getMessageId(), remotePeer.getPeerId());
                MessageBuffer destBuf = peerBuffers.get(remotePeer.getPeerId().toUpperCase());
                if (destBuf != null) {
                    destBuf.addMessage(m.copyWithTokens(1));
                    
                }
                logger.logMessageForwarded(localPeer.getPeerId(), remotePeer.getPeerId(), m.getMessageId(), "DIRECT_DELIVERY", null);
                logger.logMessageDelivered(remotePeer.getPeerId(), m.getMessageId(), localPeer.getPeerId());
            } else if (m.getCopyTokens() > 1) {
                SprayAndWaitRouter.TokenSplitResult split = SprayAndWaitRouter.splitTokens(m.getCopyTokens());
                m.setCopyTokens(split.senderRemaining);
                MessageBuffer relayBuf = peerBuffers.get(remotePeer.getPeerId().toUpperCase());
                if (relayBuf != null) {
                    relayBuf.addMessage(m.copyWithTokens(split.receiverAssigned));
                }
                System.out.printf("  -> Forwarded %s to Relay %s (given: %d tokens, local remaining: %d tokens)%n",
                        m.getMessageId(), remotePeer.getPeerId(), split.receiverAssigned, m.getCopyTokens());
                logger.logMessageForwarded(localPeer.getPeerId(), remotePeer.getPeerId(), m.getMessageId(),
                        currentStrategy.getName(), "{\"tokensGiven\":" + split.receiverAssigned + "}");
            }
        }
        System.out.println("Forwarding actions logged to SQLite database.");
    }

    // ------------------------------------------------------------------------
    // [5] Store-Carry-Forward Full Walkthrough
    // ------------------------------------------------------------------------
    private void handleStoreCarryForwardDemo() {
        System.out.println("=== STORE-CARRY-FORWARD WALKTHROUGH ===");

        Message m;
        List<Message> allMessages = buffer.getAllMessages();
        if (!allMessages.isEmpty()) {
            System.out.println("Available messages in buffer:");
            for (int i = 0; i < allMessages.size(); i++) {
                Message msg = allMessages.get(i);
                System.out.printf("  [%d] ID: %s | Dest: %s | Pri: %s | Tokens: %d | Payload: '%s'%n",
                        i + 1, msg.getMessageId(), msg.getDestinationId(), msg.getPriority(), msg.getCopyTokens(), truncate(msg.getPayload(), 20));
            }
            System.out.print("Select message number [default 1]: ");
            String selStr = scanner.nextLine().trim();
            int selectedIdx = 0;
            if (!selStr.isEmpty()) {
                try {
                    int val = Integer.parseInt(selStr) - 1;
                    if (val >= 0 && val < allMessages.size()) {
                        selectedIdx = val;
                    }
                } catch (NumberFormatException ignored) {}
            }
            m = allMessages.get(selectedIdx);
            System.out.printf("Selected message: %s (Dest: %s, Pri: %s, Tokens: %d)%n",
                    m.getMessageId(), m.getDestinationId(), m.getPriority(), m.getCopyTokens());
        } else {
            m = new Message("M_SCF", "A", "D", Priority.HIGH, "Store-Carry-Forward Interactive Walkthrough", 300, 4);
            buffer.addMessage(m);
            System.out.println("Created walkthrough message: " + m.getMessageId() + " (Dest: D)");
        }

        String destination = m.getDestinationId().toUpperCase();

        System.out.println("\n[STEP 1: CREATION & LOCAL STORAGE]");
        System.out.printf("  Node A stores %s in local memory buffer. Final Destination is %s.%n", m.getMessageId(), destination);

        // Encounter A <-> B
        System.out.println("\n[STEP 2: ENCOUNTER A <-> B]");
        System.out.println("  Node A encounters Node B in Science Block.");
        if ("B".equals(destination)) {
            System.out.printf("  -> Node B IS THE FINAL DESTINATION for %s!%n", m.getMessageId());
            System.out.printf("  -> Node A directly delivers %s to Node B.%n", m.getMessageId());
            System.out.printf("  -> Delivering terminates hop-by-hop forwarding: Node B will NOT forward its own message further!%n");
            peerBuffers.get("B").addMessage(m.copyWithTokens(1));
            logger.logMessageForwarded("A", "B", m.getMessageId(), "DIRECT_DELIVERY", null);
            logger.logMessageDelivered("B", m.getMessageId(), "A");
            System.out.printf("\n>>> [DELIVERED] MESSAGE SUCCESSFULLY REACHED DESTINATION %s! <<<\n%n", destination);
            return;
        }

        // B is an intermediate relay
        SprayAndWaitRouter.TokenSplitResult splitAB = SprayAndWaitRouter.splitTokens(m.getCopyTokens());
        m.setCopyTokens(splitAB.senderRemaining);
        Message mAtB = m.copyWithTokens(splitAB.receiverAssigned);
        peerBuffers.get("B").addMessage(mAtB);
        System.out.printf("  B is a relay. A splits tokens and forwards %s to B (Tokens: A=%d, B=%d).%n",
                m.getMessageId(), splitAB.senderRemaining, splitAB.receiverAssigned);
        logger.logMessageForwarded("A", "B", m.getMessageId(), "SPRAY_AND_WAIT", "{\"tokens\":" + splitAB.receiverAssigned + "}");
        logger.logMessageReceived("B", "A", m.getMessageId());

        System.out.println("\n[STEP 3: CARRY PHASE AT B]");
        System.out.println("  Encounter A-B ends. B disconnects from A.");
        System.out.println("  Node B CARRIES the message while walking to Library block...");

        // Encounter B <-> C
        System.out.println("\n[STEP 4: ENCOUNTER B <-> C]");
        System.out.println("  Node B encounters Node C in Library foyer.");
        if ("C".equals(destination)) {
            System.out.printf("  -> Node C IS THE FINAL DESTINATION for %s!%n", m.getMessageId());
            System.out.printf("  -> Node B directly delivers %s to Node C.%n", m.getMessageId());
            System.out.printf("  -> Delivering terminates hop-by-hop forwarding: Node C will NOT forward its own message further!%n");
            peerBuffers.get("C").addMessage(mAtB.copyWithTokens(1));
            logger.logMessageForwarded("B", "C", m.getMessageId(), "DIRECT_DELIVERY", null);
            logger.logMessageDelivered("C", m.getMessageId(), "A");
            System.out.printf("\n>>> [DELIVERED] MESSAGE SUCCESSFULLY REACHED DESTINATION %s! <<<\n%n", destination);
            return;
        }

        // C is an intermediate relay
        SprayAndWaitRouter.TokenSplitResult splitBC = SprayAndWaitRouter.splitTokens(mAtB.getCopyTokens());
        mAtB.setCopyTokens(splitBC.senderRemaining);
        Message mAtC = mAtB.copyWithTokens(splitBC.receiverAssigned);
        peerBuffers.get("C").addMessage(mAtC);
        System.out.printf("  C is a relay. B splits tokens and forwards %s to C (Tokens: B=%d, C=%d).%n",
                m.getMessageId(), splitBC.senderRemaining, splitBC.receiverAssigned);
        logger.logMessageForwarded("B", "C", m.getMessageId(), "SPRAY_AND_WAIT", "{\"tokens\":" + splitBC.receiverAssigned + "}");
        logger.logMessageReceived("C", "B", m.getMessageId());

        System.out.println("\n[STEP 5: CARRY PHASE AT C]");
        System.out.println("  Encounter B-C ends. Node C CARRIES message to Hostels area...");

        // Encounter C <-> D
        System.out.println("\n[STEP 6: ENCOUNTER C <-> D]");
        Peer peerD = knownPeers.get("D");
        System.out.printf("  Node C encounters Node %s!%n", peerD.getPeerId());
        if ("D".equals(destination)) {
            System.out.printf("  -> Node D IS THE FINAL DESTINATION for %s!%n", m.getMessageId());
            System.out.printf("  -> Node C directly delivers %s to %s.%n", m.getMessageId(), peerD.getPeerId());
            peerBuffers.get("D").addMessage(mAtC.copyWithTokens(1));
            logger.logMessageForwarded("C", "D", m.getMessageId(), "DIRECT_DELIVERY", null);
            logger.logMessageDelivered("D", m.getMessageId(), "A");
            System.out.printf("\n>>> [DELIVERED] MESSAGE SUCCESSFULLY REACHED DESTINATION %s! <<<\n%n", destination);
        } else {
            System.out.printf("  Notice: Final destination is %s. Node D is reached but message is pending destination encounter.%n%n", destination);
        }
    }

    // ------------------------------------------------------------------------
    // [6] TCP Length-Prefixed Transmission
    // ------------------------------------------------------------------------
    private void handleSendOverTCP() {
        System.out.println("=== LIVE TCP SOCKET TRANSMISSION TEST ===");
        int testPort = 5995;
        CountDownLatch latch = new CountDownLatch(1);
        final Message[] receivedMsg = new Message[1];

        Message toSend;
        if (buffer.size() > 0) {
            toSend = buffer.getAllMessages().get(0);
        } else {
            toSend = new Message("TCP_LIVE", "A", "B", Priority.HIGH, "Live TCP socket framed payload", 100, 2);
        }

        System.out.printf("1. Launching ephemeral TCP server on 127.0.0.1:%d...%n", testPort);

        try (TCPManager tcp = new TCPManager()) {
            tcp.startServer(testPort, (msg, socket) -> {
                receivedMsg[0] = msg;
                latch.countDown();
            });

            Thread.sleep(150);

            System.out.printf("2. Connecting client socket to 127.0.0.1:%d...%n", testPort);
            try (Socket client = tcp.connectToPeer("127.0.0.1", testPort)) {
                System.out.printf("3. Sending framed message '%s' with 4-byte prefix...%n", toSend.getMessageId());
                tcp.sendMessage(client, toSend);

                boolean received = latch.await(2, TimeUnit.SECONDS);
                if (received && receivedMsg[0] != null) {
                    System.out.println("4. [TCP RECEIVER CONFIRMATION]");
                    System.out.printf("   Message ID: %s%n", receivedMsg[0].getMessageId());
                    System.out.printf("   Source:     %s -> Destination: %s%n", receivedMsg[0].getSourceId(), receivedMsg[0].getDestinationId());
                    System.out.printf("   Payload:    '%s'%n", receivedMsg[0].getPayload());
                    System.out.printf("   Tokens:     %d%n", receivedMsg[0].getCopyTokens());
                    System.out.println("   [SUCCESS] 4-byte length framing preserved full stream boundaries!");
                    logger.logMessageForwarded("A", "TCP_REMOTE", toSend.getMessageId(), "TCP_SOCKET", "{\"framed\":true}");
                } else {
                    System.out.println("   [FAILED] Did not receive message within timeout.");
                }
            }
        } catch (Exception e) {
            System.err.println("TCP Test error: " + e.getMessage());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // [7] View Buffer
    // ------------------------------------------------------------------------
    private void handleViewBuffer() {
        System.out.println("=== MESSAGE BUFFER CONTENTS ===");
        List<Message> all = buffer.getAllMessages();
        if (all.isEmpty()) {
            System.out.println("  (Buffer is currently empty)\n");
            return;
        }

        System.out.printf("%-8s%-6s%-6s%-8s%-12s%-8s%-8s%-25s%n",
                "MsgID", "Src", "Dest", "Pri", "Rem.TTL", "Expired", "Tokens", "Payload");
        System.out.println("------------------------------------------------------------------------------------");

        for (Message m : all) {
            System.out.printf("%-8s%-6s%-6s%-8s%-12s%-8b%-8d%-25s%n",
                    m.getMessageId(),
                    m.getSourceId(),
                    m.getDestinationId(),
                    m.getPriority(),
                    m.getRemainingTTL() + "s",
                    m.isExpired(),
                    m.getCopyTokens(),
                    truncate(m.getPayload(), 24));
        }

        System.out.println("------------------------------------------------------------------------------------");
        System.out.printf("Total Count: %d | Capacity: %d bytes | Used: %d bytes (%.2f%%)%n%n",
                buffer.size(), buffer.getCapacityBytes(), buffer.getUsedBufferBytes(), buffer.getBufferPressure() * 100.0);
    }

    // ------------------------------------------------------------------------
    // [8] View SQLite Database Logs
    // ------------------------------------------------------------------------
    private void handleViewDatabaseLogs() {
        System.out.println("=== SQLITE EVENT LOGS ===");
        System.out.printf("Database location: %s%n", logger.getDbPath());
        System.out.printf("Total events logged: %d%n%n", logger.getEventCount());
        logger.printRecentEvents(15);
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // [9] Purge Expired Messages
    // ------------------------------------------------------------------------
    private void handlePurgeExpired() {
        System.out.println("=== PURGE EXPIRED MESSAGES ===");
        List<Message> removed = buffer.removeExpiredMessages();
        if (removed.isEmpty()) {
            System.out.println("No expired messages found in buffer.");
        } else {
            System.out.printf("Removed %d expired messages:%n", removed.size());
            for (Message m : removed) {
                System.out.printf("  - %s (expired %s)%n", m.getMessageId(), m.getExpiresAt());
                logger.logMessageExpired(localPeer.getPeerId(), m.getMessageId());
            }
        }
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // [10] View Messages Stored in a Selected Node
    // ------------------------------------------------------------------------
    private void handleViewNodeBuffer() {
        System.out.println("=== VIEW NODE MESSAGE BUFFER ===");
        System.out.println("Available Nodes: A (Local), B, C, D");
        System.out.print("Enter Node ID to inspect [default A]: ");
        String nodeId = scanner.nextLine().trim().toUpperCase();
        if (nodeId.isEmpty()) {
            nodeId = "A";
        }

        MessageBuffer targetBuf = peerBuffers.get(nodeId);
        if (targetBuf == null) {
            System.out.printf("[ERROR] Unknown Node ID: '%s'. Valid options are A, B, C, D.%n%n", nodeId);
            return;
        }

        System.out.printf("%n=== BUFFER CONTENTS FOR NODE [%s] ===%n", nodeId);
        List<Message> msgs = targetBuf.getAllMessages();
        if (msgs.isEmpty()) {
            System.out.printf("  (Node %s currently has 0 messages stored in its buffer)%n%n", nodeId);
            return;
        }

        System.out.printf("%-8s%-6s%-6s%-8s%-12s%-8s%-8s%-25s%n",
                "MsgID", "Src", "Dest", "Pri", "Rem.TTL", "Expired", "Tokens", "Payload");
        System.out.println("------------------------------------------------------------------------------------");

        for (Message m : msgs) {
            System.out.printf("%-8s%-6s%-6s%-8s%-12s%-8b%-8d%-25s%n",
                    m.getMessageId(),
                    m.getSourceId(),
                    m.getDestinationId(),
                    m.getPriority(),
                    m.getRemainingTTL() + "s",
                    m.isExpired(),
                    m.getCopyTokens(),
                    truncate(m.getPayload(), 24));
        }

        System.out.println("------------------------------------------------------------------------------------");
        System.out.printf("Node %s Summary -> Total Messages: %d | Used: %d bytes (%.2f%%) | Capacity: %d bytes%n%n",
                nodeId, targetBuf.size(), targetBuf.getUsedBufferBytes(),
                targetBuf.getBufferPressure() * 100.0, targetBuf.getCapacityBytes());
    }

    // ------------------------------------------------------------------------
    // [11] Dynamic Wi-Fi Discovery Monitor
    // ------------------------------------------------------------------------
    private void handleDynamicDiscoveryMonitor() {
        System.out.println("=== DYNAMIC WI-FI PEER DISCOVERY (mDNS & SUBNET BEACON) ===");
        try {
            discoveryManager.startWifiDiscovery();
            System.out.println("[DISCOVERY ACTIVE] Listening for mDNS and subnet multicast beacons...");
            System.out.println("Scanning network for active peers (sampling for 2.5 seconds)...");
            Thread.sleep(2500);

            List<DiscoveredPeer> discovered = discoveryManager.getRegistry().getAllPeers();
            if (discovered.isEmpty()) {
                System.out.println("  No remote peers discovered yet on this local subnet.");
                System.out.println("  (Tip: Launch another CampusMesh instance on another laptop or port to observe dynamic discovery)");
            } else {
                System.out.printf("Discovered %d active peer(s) on Wi-Fi:%n", discovered.size());
                System.out.printf("%-10s%-16s%-10s%-18s%-8s%-15s%n", "Peer ID", "Name", "Platform", "IP Address", "Port", "Transports");
                System.out.println("----------------------------------------------------------------------------------");
                for (DiscoveredPeer dp : discovered) {
                    System.out.printf("%-10s%-16s%-10s%-18s%-8d%-15s%n",
                            dp.getPeerId(), dp.getDisplayName(), dp.getPlatform(),
                            dp.getIpAddress(), dp.getPort(), dp.getSupportedTransports());
                    this.knownPeers.put(dp.getPeerId().toUpperCase(), dp.toPeer());
                }
                System.out.println("----------------------------------------------------------------------------------");
                System.out.println("Discovered peers automatically synced to active ContactManager encounters!");
            }
        } catch (Exception e) {
            System.err.println("[DISCOVERY ERROR] " + e.getMessage());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // [12] Multi-Transport Mesh Monitor (Wi-Fi + BLE)
    // ------------------------------------------------------------------------
    private void handleMultiTransportMonitor() {
        System.out.println("=== MULTI-TRANSPORT MESH MONITOR (WI-FI + BLE) ===");
        System.out.println("Scanning active network interfaces and BLE GATT advertisements...");

        try {
            discoveryManager.startBleDiscovery();
            Thread.sleep(600); // Allow radio discovery probe

            java.util.Collection<DiscoveredPeer> peers = discoveryManager.getRegistry().getAllPeers();
            System.out.println("\n------------------------------------------------------------------------------------------------------");
            System.out.printf("%-18s %-20s %-10s %-22s %-16s %-10s%n",
                    "PEER ID", "DISPLAY NAME", "PLATFORM", "ENDPOINT", "TRANSPORTS", "STATUS");
            System.out.println("------------------------------------------------------------------------------------------------------");

            if (peers.isEmpty()) {
                System.out.println("  No remote peers discovered yet on Wi-Fi or BLE. (Listening on 0000FE60 & 5354)");
            } else {
                for (DiscoveredPeer dp : peers) {
                    String endpoint = dp.getIpAddress() + ":" + dp.getPort();
                    String status = dp.isExpired(15000) ? "EXPIRED" : "ACTIVE";
                    System.out.printf("%-18s %-20s %-10s %-22s %-16s %-10s%n",
                            dp.getPeerId(), dp.getDisplayName(), dp.getPlatform(),
                            endpoint, dp.getSupportedTransports(), status);
                    this.knownPeers.put(dp.getPeerId().toUpperCase(), dp.toPeer());
                }
            }
            System.out.println("------------------------------------------------------------------------------------------------------");

            // Packet inspector demonstration
            System.out.println("\n[BLE PACKET INSPECTOR]");
            if (buffer.size() == 0) {
                System.out.println("  Local buffer has no messages to slice. Create a message via Option [1] first.");
            } else {
                Message sample = buffer.getAllMessages().get(0);
                System.out.printf("  Sample Message to Slice: %s (%d bytes)%n", sample.getMessageId(), sample.toJson().getBytes().length);
                java.util.List<campusmesh.ble.BlePacket> chunks = campusmesh.ble.BleChunker.chunkMessage(sample, campusmesh.ble.BleConstants.DEFAULT_MAX_CHUNK_PAYLOAD);
                System.out.printf("  MTU 23-byte slicing produced %d chunks with CRC32=0x%08X:%n", chunks.size(), chunks.get(0).getCrc32());
                for (int i = 0; i < Math.min(3, chunks.size()); i++) {
                    campusmesh.ble.BlePacket p = chunks.get(i);
                    System.out.printf("    Chunk #%d/%d: Header=%dB, Payload=%dB, Total=%dB%n",
                            p.getChunkIndex() + 1, p.getTotalChunks(), campusmesh.ble.BlePacket.HEADER_SIZE, p.getPayload().length, p.toBytes().length);
                }
                if (chunks.size() > 3) {
                    System.out.printf("    ... and %d more chunks.%n", chunks.size() - 3);
                }
            }
        } catch (Exception e) {
            System.err.println("[MONITOR ERROR] " + e.getMessage());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // [13] Heterogeneous Multi-Hop Simulation Demo
    // ------------------------------------------------------------------------
    private void handleHeterogeneousMeshDemo() {
        try {
            MultiTransportMeshDemo.main(new String[0]);
        } catch (Exception e) {
            System.err.println("[SIMULATION ERROR] " + e.getMessage());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // [14] Launch Web Dashboard Server
    // ------------------------------------------------------------------------
    private void handleLaunchWebDashboard() {
        System.out.println("=== LAUNCH REAL-TIME WEB DASHBOARD GUI ===");
        try {
            if (webServer == null) {
                campusmesh.routing.RoutingEngine re = new campusmesh.routing.RoutingEngine(currentStrategy);
                webServer = new campusmesh.web.CampusMeshHttpServer(
                        8080, new File("web"), localPeer, buffer, contactManager, discoveryManager, re, logger, multiTransportSender);
                webServer.start();
                System.out.println("  -> [STARTED] Web Server listening at http://localhost:8080");
            } else {
                System.out.println("  -> [ACTIVE] Web Server is already running at http://localhost:8080");
            }

            // Launch default browser
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:8080"));
                } else {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "http://localhost:8080"});
                    }
                }
                System.out.println("  -> Browser opened to http://localhost:8080");
            } catch (Exception ex) {
                System.out.println("  -> Open your browser manually and visit: http://localhost:8080");
            }
        } catch (Exception e) {
            System.err.println("[WEB ERROR] Failed to launch web dashboard: " + e.getMessage());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------------
    // Command-line non-interactive arguments handler
    // ------------------------------------------------------------------------
    private void handleCommandLineArgs(String[] args) {
        String msgId = "M001";
        String dest = "D";
        Priority prio = Priority.HIGH;
        String payload = "CampusMesh automated test payload";
        long ttl = 120;
        int tokens = 4;
        String strat = "ADAPTIVE";
        String contactPeer = "B";

        for (int i = 0; i < args.length; i++) {
            if ("--id".equalsIgnoreCase(args[i]) && i + 1 < args.length) msgId = args[++i];
            else if ("--to".equalsIgnoreCase(args[i]) && i + 1 < args.length) dest = args[++i].toUpperCase();
            else if ("--priority".equalsIgnoreCase(args[i]) && i + 1 < args.length) prio = Priority.fromString(args[++i]);
            else if ("--payload".equalsIgnoreCase(args[i]) && i + 1 < args.length) payload = args[++i];
            else if ("--ttl".equalsIgnoreCase(args[i]) && i + 1 < args.length) ttl = Long.parseLong(args[++i]);
            else if ("--tokens".equalsIgnoreCase(args[i]) && i + 1 < args.length) tokens = Integer.parseInt(args[++i]);
            else if ("--strategy".equalsIgnoreCase(args[i]) && i + 1 < args.length) strat = args[++i].toUpperCase();
            else if ("--contact".equalsIgnoreCase(args[i]) && i + 1 < args.length) contactPeer = args[++i].toUpperCase();
        }

        Peer remote = knownPeers.getOrDefault(contactPeer, new Peer(contactPeer, "127.0.0.1", 5002));
        contactManager.startContact(remote);

        Message m = new Message(msgId, localPeer.getPeerId(), dest, prio, payload, ttl, tokens);
        buffer.addMessage(m);
        logger.logMessageCreated(localPeer.getPeerId(), msgId, dest, m.toJson());

        System.out.println("==================================================");
        System.out.println("      CAMPUSMESH DIRECT COMMAND EXECUTION");
        System.out.println("==================================================");
        System.out.printf("Message: %s | Source: %s -> Dest: %s%n", msgId, localPeer.getPeerId(), dest);
        System.out.printf("Priority: %s (P=%.2f) | TTL: %ds | Tokens: %d%n", prio, prio.getWeight(), ttl, tokens);
        System.out.printf("Payload: '%s'%n", payload);
        System.out.printf("Contact Peer: %s%n", remote.getPeerId());
        System.out.printf("Strategy: %s%n%n", strat);

        if ("EPIDEMIC".equals(strat)) {
            epidemicRouter.printDecisionDemo(localPeer, remote, buffer, Collections.emptySet());
        } else if ("SPRAY_AND_WAIT".equals(strat)) {
            sprayAndWaitRouter.printTokenSplittingDemo(localPeer, remote, m);
        } else {
            adaptiveRouter.printEvaluationDemo(localPeer, remote, buffer, Collections.emptySet());
        }

        logger.close();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }
}
