package campusmesh.demo;

import campusmesh.buffer.MessageBuffer;
import campusmesh.contact.ContactManager;
import campusmesh.logging.EventLogger;
import campusmesh.model.Message;
import campusmesh.model.Peer;
import campusmesh.model.Priority;
import campusmesh.networking.TCPManager;
import campusmesh.routing.AdaptiveRouter;
import campusmesh.routing.EpidemicRouter;
import campusmesh.routing.RoutingEngine;
import campusmesh.routing.SprayAndWaitRouter;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * BackendDemo:
 * End-to-end terminal demonstration of CampusMesh core backend components:
 * - Peer & Message Models
 * - MessageBuffer & Duplicate Detection
 * - ContactManager (intermittent encounters)
 * - Epidemic, Spray-and-Wait, and Adaptive Routing
 * - Store-Carry-Forward multi-hop delivery
 * - TCP framing and transport verification
 * - SQLite persistent event logging
 */
public class BackendDemo {

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        System.out.println("========================================");
        System.out.println("       CAMPUSMESH BACKEND DEMO");
        System.out.println("  Store-Carry-Forward DTN Messaging");
        System.out.println("========================================\n");

        // Initialize SQLite EventLogger
        try (EventLogger logger = new EventLogger("database/campusmesh.db")) {

            // ----------------------------------------------------
            // [1] PEER INITIALIZATION
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[1] PEER INITIALIZATION");
            System.out.println("----------------------------------------");

            Peer peerA = new Peer("A", "127.0.0.1", 5001);
            Peer peerB = new Peer("B", "127.0.0.1", 5002);
            Peer peerC = new Peer("C", "127.0.0.1", 5003);
            Peer peerD = new Peer("D", "127.0.0.1", 5004);

            System.out.printf("Initialized Peer: %s%n", peerA);
            System.out.printf("Initialized Peer: %s%n", peerB);
            System.out.printf("Initialized Peer: %s%n", peerC);
            System.out.printf("Initialized Peer: %s%n", peerD);

            logger.logPeerStarted(peerA.getPeerId());
            logger.logPeerStarted(peerB.getPeerId());
            logger.logPeerStarted(peerC.getPeerId());
            logger.logPeerStarted(peerD.getPeerId());
            System.out.println();

            // ----------------------------------------------------
            // [2] MESSAGE CREATION
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[2] MESSAGE CREATION");
            System.out.println("----------------------------------------");

            // M001: A -> D, HIGH priority, 4 tokens, TTL 120s
            Message m1 = new Message("M001", "A", "D", Priority.HIGH, "Important lab notes for D", 120, 4);

            // M002: A -> D, MEDIUM priority, 4 tokens, older creation with lower remaining TTL (urgent)
            Instant pastTime = Instant.now().minusSeconds(80);
            Instant expireTime = pastTime.plusSeconds(100); // 20s remaining of 100s original -> urgent!
            Message m2 = new Message("M002", "A", "D", pastTime, expireTime, Priority.MEDIUM, "Assignment submission link", 4);

            // M003: A -> C, LOW priority, 4 tokens, TTL 180s
            Message m3 = new Message("M003", "A", "C", Priority.LOW, "Cafeteria lunch menu", 180, 4);

            System.out.println("[CREATE]");
            System.out.printf("  %s created at %s (Pri: %s, TTL: %ds, Tokens: %d)%n",
                    m1.getMessageId(), m1.getSourceId(), m1.getPriority(), m1.getRemainingTTL(), m1.getCopyTokens());
            System.out.printf("  %s created at %s (Pri: %s, TTL: %ds/%ds, Tokens: %d)%n",
                    m2.getMessageId(), m2.getSourceId(), m2.getPriority(), m2.getRemainingTTL(), m2.getOriginalTTL(), m2.getCopyTokens());
            System.out.printf("  %s created at %s (Pri: %s, TTL: %ds, Tokens: %d)%n",
                    m3.getMessageId(), m3.getSourceId(), m3.getPriority(), m3.getRemainingTTL(), m3.getCopyTokens());

            logger.logMessageCreated("A", m1.getMessageId(), m1.getDestinationId(), m1.toJson());
            logger.logMessageCreated("A", m2.getMessageId(), m2.getDestinationId(), m2.toJson());
            logger.logMessageCreated("A", m3.getMessageId(), m3.getDestinationId(), m3.toJson());
            System.out.println();

            // ----------------------------------------------------
            // [3] BUFFER STORAGE & DUPLICATE DETECTION
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[3] BUFFER STORAGE & DUPLICATE DETECTION");
            System.out.println("----------------------------------------");

            MessageBuffer bufferA = new MessageBuffer("A");
            bufferA.addMessage(m1);
            bufferA.addMessage(m2);
            bufferA.addMessage(m3);

            logger.logMessageStored("A", m1.getMessageId());
            logger.logMessageStored("A", m2.getMessageId());
            logger.logMessageStored("A", m3.getMessageId());

            System.out.println("[BUFFER]");
            System.out.println("  A stored " + m1.getMessageId());
            System.out.println("  A stored " + m2.getMessageId());
            System.out.println("  A stored " + m3.getMessageId());
            System.out.printf("  Buffer A status: %d messages, %d bytes used%n",
                    bufferA.size(), bufferA.getUsedBufferBytes());

            // Duplicate Detection Demonstration
            System.out.println("\nTesting Duplicate Detection:");
            System.out.println("  Attempting to add duplicate message 'M001' into Buffer A...");
            MessageBuffer.AddResult dupResult = bufferA.addMessageWithStatus(m1);
            if (dupResult == MessageBuffer.AddResult.DUPLICATE) {
                System.out.println("  [VERIFIED] Duplicate detected correctly and rejected!");
                logger.logMessageDuplicate("A", m1.getMessageId());
            }

            // Expiry Demonstration
            System.out.println("\nTesting TTL Expiry Detection:");
            Message expiredMsg = new Message("M_EXP", "A", "B", Instant.now().minusSeconds(10),
                    Instant.now().minusSeconds(2), Priority.LOW, "Expired content", 1);
            System.out.printf("  Attempting to store expired message '%s' (isExpired=%b)...%n",
                    expiredMsg.getMessageId(), expiredMsg.isExpired());
            MessageBuffer.AddResult expResult = bufferA.addMessageWithStatus(expiredMsg);
            if (expResult == MessageBuffer.AddResult.EXPIRED) {
                System.out.println("  [VERIFIED] Expired message rejected by buffer!");
                logger.logMessageExpired("A", expiredMsg.getMessageId());
            }
            System.out.println();

            // ----------------------------------------------------
            // [4] CONTACT ESTABLISHMENT
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[4] CONTACT ESTABLISHMENT");
            System.out.println("----------------------------------------");

            ContactManager contactMgrA = new ContactManager(peerA);
            contactMgrA.startContact(peerB);
            logger.logContactStarted("A", "B");
            System.out.printf("[CONTACT] Logical link active: %s <-> %s%n", peerA.getPeerId(), peerB.getPeerId());
            System.out.printf("  Is A in contact with B? %b%n", contactMgrA.isInContact(peerB));
            System.out.printf("  Is A in contact with C? %b (intermittent disconnection)%n", contactMgrA.isInContact(peerC));
            System.out.println();

            // ----------------------------------------------------
            // [5] EPIDEMIC ROUTING
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[5] EPIDEMIC ROUTING");
            System.out.println("----------------------------------------");

            EpidemicRouter epidemic = new EpidemicRouter();
            epidemic.printDecisionDemo(peerA, peerB, bufferA, Collections.emptySet());
            List<Message> epidemicSelected = epidemic.selectMessages(peerA, peerB, bufferA);
            logger.logRoutingDecision("A", "B", "EPIDEMIC", "{\"selectedCount\":" + epidemicSelected.size() + "}");

            // ----------------------------------------------------
            // [6] SPRAY-AND-WAIT ROUTING
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[6] SPRAY-AND-WAIT ROUTING");
            System.out.println("----------------------------------------");

            SprayAndWaitRouter sprayAndWait = new SprayAndWaitRouter();
            sprayAndWait.printTokenSplittingDemo(peerA, peerB, m1);

            List<Message> spraySelected = sprayAndWait.selectMessages(peerA, peerB, bufferA);
            System.out.printf("Spray-and-Wait selected %d candidate messages for relay forwarding.%n", spraySelected.size());
            logger.logRoutingDecision("A", "B", "SPRAY_AND_WAIT", "{\"selectedCount\":" + spraySelected.size() + "}");
            System.out.println();

            // ----------------------------------------------------
            // [7] ADAPTIVE ROUTING
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[7] ADAPTIVE ROUTING");
            System.out.println("----------------------------------------");

            // Configurable weights: wP = 0.40, wT = 0.40, wB = 0.20
            AdaptiveRouter adaptive = new AdaptiveRouter(0.40, 0.40, 0.20);
            adaptive.printEvaluationDemo(peerA, peerB, bufferA, Collections.emptySet());

            List<AdaptiveRouter.UtilityScore> evals = adaptive.evaluateMessages(peerA, peerB, bufferA, Collections.emptySet());
            for (AdaptiveRouter.UtilityScore score : evals) {
                logger.logRoutingDecision("A", "B", "ADAPTIVE", score.toJson());
            }

            // ----------------------------------------------------
            // [8] STORE-CARRY-FORWARD DEMO
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[8] STORE-CARRY-FORWARD WALKTHROUGH");
            System.out.println("----------------------------------------");

            demonstrateStoreCarryForward(peerA, peerB, peerC, peerD, logger);

            // ----------------------------------------------------
            // [9] TCP TRANSPORT VERIFICATION
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[9] TCP SOCKET FRAMING & TRANSPORT");
            System.out.println("----------------------------------------");

            demonstrateTCPTransfer(logger);

            // ----------------------------------------------------
            // [10] SQLITE EVENT LOGGING & VERIFICATION
            // ----------------------------------------------------
            System.out.println("----------------------------------------");
            System.out.println("[10] SQLITE EVENT LOGGING & VERIFICATION");
            System.out.println("----------------------------------------");

            System.out.printf("Total events logged in SQLite database: %d%n%n", logger.getEventCount());
            logger.printRecentEvents(12);

            System.out.println("\n========================================");
            System.out.println("         DEMO COMPLETE - SUCCESS");
            System.out.println("========================================");
        }
    }

    /**
     * Step-by-step terminal demonstration of Store-Carry-Forward DTN transmission.
     */
    private static void demonstrateStoreCarryForward(Peer A, Peer B, Peer C, Peer D, EventLogger logger) {
        System.out.println("=== STORE-CARRY-FORWARD DEMO ===\n");

        Message mDemo = new Message("M001", "A", "D", Priority.HIGH, "Store-Carry-Forward Payload", 300, 4);
        MessageBuffer bufA = new MessageBuffer("A");
        MessageBuffer bufB = new MessageBuffer("B");
        MessageBuffer bufC = new MessageBuffer("C");
        MessageBuffer bufD = new MessageBuffer("D");

        System.out.println("A creates M001");
        bufA.addMessage(mDemo);
        System.out.println("A stores M001\n");

        // Hop 1: A encounters B
        System.out.println("Contact: A-B");
        SprayAndWaitRouter.TokenSplitResult split1 = SprayAndWaitRouter.splitTokens(mDemo.getCopyTokens());
        mDemo.setCopyTokens(split1.senderRemaining);
        Message mForB = mDemo.copyWithTokens(split1.receiverAssigned);

        System.out.printf("A forwards M001 to B (tokens given: %d, A keeps: %d)%n",
                split1.receiverAssigned, split1.senderRemaining);
        bufB.addMessage(mForB);
        System.out.println("B stores M001\n");
        logger.logMessageForwarded("A", "B", "M001", "SPRAY_AND_WAIT", "{\"tokens\":2}");
        logger.logMessageReceived("B", "A", "M001");

        // Carry Phase: Node B moves across campus with intermittent disconnection
        System.out.println("No contact with C...");
        System.out.println("B carries M001 across campus...\n");

        // Hop 2: B encounters C
        System.out.println("Contact: B-C");
        SprayAndWaitRouter.TokenSplitResult split2 = SprayAndWaitRouter.splitTokens(mForB.getCopyTokens());
        mForB.setCopyTokens(split2.senderRemaining);
        Message mForC = mForB.copyWithTokens(split2.receiverAssigned);

        System.out.printf("B forwards M001 to C (tokens given: %d, B keeps: %d)%n",
                split2.receiverAssigned, split2.senderRemaining);
        bufC.addMessage(mForC);
        System.out.println("C stores M001\n");
        logger.logMessageForwarded("B", "C", "M001", "SPRAY_AND_WAIT", "{\"tokens\":1}");
        logger.logMessageReceived("C", "B", "M001");

        // Carry Phase: C carries message to destination area
        System.out.println("No contact with D...");
        System.out.println("C carries M001 until reaching destination vicinity...\n");

        // Hop 3: C encounters Destination D
        System.out.println("Contact: C-D");
        System.out.println("C forwards M001 to D");
        bufD.addMessage(mForC);
        System.out.println("D is destination\n");
        System.out.println(">>> M001 DELIVERED <<<\n");

        logger.logMessageForwarded("C", "D", "M001", "DIRECT_DELIVERY", null);
        logger.logMessageDelivered("D", "M001", "A");
    }

    /**
     * Demonstrates real TCP socket communication with 4-byte length prefix framing.
     */
    private static void demonstrateTCPTransfer(EventLogger logger) {
        System.out.println("Starting ephemeral TCP loopback server on localhost...");

        int testPort = 5991;
        CountDownLatch receivedLatch = new CountDownLatch(1);
        final Message[] receivedHolder = new Message[1];

        try (TCPManager tcp = new TCPManager()) {
            // Start server
            tcp.startServer(testPort, (msg, socket) -> {
                receivedHolder[0] = msg;
                System.out.printf("  [TCP SERVER] Received message frame via TCP: %s%n", msg.getMessageId());
                System.out.printf("               Payload: '%s' | Tokens: %d%n", msg.getPayload(), msg.getCopyTokens());
                receivedLatch.countDown();
            });

            // Small pause for server socket binding
            Thread.sleep(100);

            // Connect client and send message
            System.out.printf("Client connecting to localhost:%d...%n", testPort);
            try (Socket clientSocket = tcp.connectToPeer("127.0.0.1", testPort)) {
                Message testMessage = new Message("M_TCP", "A", "B", Priority.HIGH,
                        "Testing length-prefixed TCP streaming frame", 60, 2);

                System.out.printf("  [TCP CLIENT] Transmitting message '%s' with 4-byte prefix...%n", testMessage.getMessageId());
                tcp.sendMessage(clientSocket, testMessage);

                boolean received = receivedLatch.await(2, TimeUnit.SECONDS);
                if (received && receivedHolder[0] != null) {
                    System.out.println("  [VERIFIED] TCP framing and transfer successful!");
                    logger.logMessageForwarded("CLIENT", "SERVER", "M_TCP", "TCP_SOCKET", "{\"framed\":true}");
                } else {
                    System.err.println("  [ERROR] TCP receive timed out");
                }
            }
        } catch (Exception e) {
            System.err.println("[TCP DEMO ERROR] " + e.getMessage());
        }
        System.out.println();
    }
}
