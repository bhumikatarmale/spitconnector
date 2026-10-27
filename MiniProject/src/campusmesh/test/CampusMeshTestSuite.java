package campusmesh.test;

import campusmesh.buffer.MessageBuffer;
import campusmesh.contact.ContactManager;
import campusmesh.logging.EventLogger;
import campusmesh.model.Message;
import campusmesh.model.Peer;
import campusmesh.model.Priority;
import campusmesh.networking.TCPManager;
import campusmesh.routing.AdaptiveRouter;
import campusmesh.routing.EpidemicRouter;
import campusmesh.routing.SprayAndWaitRouter;

import java.io.File;
import java.net.Socket;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CampusMeshTestSuite:
 * Comprehensive, self-contained test suite verifying all required components for
 * Pranav + Bhumika's implementation:
 * - Message & TTL
 * - Buffer & Duplicate Detection
 * - ContactManager
 * - Epidemic Routing
 * - Spray-and-Wait Routing & Token Splitting
 * - Adaptive Forwarding Utility & Ranking
 * - SQLite EventLogger
 * - TCP Socket Framing
 */
public class CampusMeshTestSuite {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("    CAMPUSMESH BACKEND TEST SUITE");
        System.out.println("========================================\n");

        runTest("Message Creation & Timestamp TTL", CampusMeshTestSuite::testMessageCreationAndTTL);
        runTest("Message Buffer Operations", CampusMeshTestSuite::testMessageBufferOperations);
        runTest("Buffer Duplicate Detection", CampusMeshTestSuite::testBufferDuplicateDetection);
        runTest("Buffer Expired Message Handling", CampusMeshTestSuite::testBufferExpiredMessageHandling);
        runTest("ContactManager Operations", CampusMeshTestSuite::testContactManager);
        runTest("Epidemic Routing Selection", CampusMeshTestSuite::testEpidemicRouter);
        runTest("Spray-and-Wait Copy Splitting & Wait Phase", CampusMeshTestSuite::testSprayAndWaitRouter);
        runTest("Adaptive Forwarding P, T, B & Utility Scoring", CampusMeshTestSuite::testAdaptiveRouter);
        runTest("Adaptive Forwarding Weight Validation", CampusMeshTestSuite::testAdaptiveWeightValidation);
        runTest("Message JSON Serialization & Parsing", CampusMeshTestSuite::testMessageJsonSerialization);
        runTest("SQLite EventLogger Persistence & Querying", CampusMeshTestSuite::testEventLogger);
        runTest("TCP Socket Length-Prefixed Framing", CampusMeshTestSuite::testTCPFraming);

        System.out.println("\n========================================");
        System.out.printf("TEST RESULTS: %d / %d PASSED%n", testsPassed, testsRun);
        System.out.println("========================================");

        if (testsPassed != testsRun) {
            System.err.println("Some tests failed!");
            System.exit(1);
        } else {
            System.out.println("ALL TESTS PASSED SUCCESSFULLY!");
        }
    }

    private static void runTest(String testName, Runnable testMethod) {
        testsRun++;
        try {
            testMethod.run();
            testsPassed++;
            System.out.printf("  [PASS] %s%n", testName);
        } catch (Throwable t) {
            System.err.printf("  [FAIL] %s: %s%n", testName, t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(String.format("%s [Expected: %s, Actual: %s]", message, expected, actual));
        }
    }

    private static void assertDoubleEquals(double expected, double actual, double epsilon, String message) {
        if (Math.abs(expected - actual) > epsilon) {
            throw new AssertionError(String.format("%s [Expected: %.4f, Actual: %.4f]", message, expected, actual));
        }
    }

    // 1. Message & TTL Test
    private static void testMessageCreationAndTTL() {
        Message msg = new Message("T01", "A", "B", Priority.HIGH, "Hello", 60, 4);
        assertEquals("T01", msg.getMessageId(), "Message ID should match");
        assertEquals("A", msg.getSourceId(), "Source should match");
        assertEquals("B", msg.getDestinationId(), "Destination should match");
        assertEquals(Priority.HIGH, msg.getPriority(), "Priority should match");
        assertEquals(4, msg.getCopyTokens(), "Copy tokens should match");
        assertTrue(!msg.isExpired(), "Fresh message should not be expired");
        assertTrue(msg.getRemainingTTL() > 55, "Remaining TTL should be around 60 seconds");

        // Expired message
        Instant past = Instant.now().minusSeconds(30);
        Message expMsg = new Message("T02", "A", "B", past.minusSeconds(10), past, Priority.LOW, "Expired", 1);
        assertTrue(expMsg.isExpired(), "Past message should be expired");
        assertEquals(0L, expMsg.getRemainingTTL(), "Expired message remaining TTL should be 0");
    }

    // 2. Buffer Operations Test
    private static void testMessageBufferOperations() {
        MessageBuffer buffer = new MessageBuffer("NODE_1", 1024 * 1024);
        Message m1 = new Message("M01", "A", "B", Priority.MEDIUM, "Payload 1", 100, 2);
        Message m2 = new Message("M02", "A", "B", Priority.HIGH, "Payload 2", 100, 2);

        assertTrue(buffer.addMessage(m1), "Should successfully add m1");
        assertTrue(buffer.addMessage(m2), "Should successfully add m2");
        assertEquals(2, buffer.size(), "Buffer size should be 2");
        assertTrue(buffer.containsMessage("M01"), "Buffer should contain M01");
        assertTrue(buffer.containsMessage("M02"), "Buffer should contain M02");

        Message removed = buffer.removeMessage("M01");
        assertEquals(m1, removed, "Removed message should match m1");
        assertEquals(1, buffer.size(), "Buffer size should be 1 after removal");
        assertTrue(!buffer.containsMessage("M01"), "M01 should no longer be in buffer");
    }

    // 3. Buffer Duplicate Detection
    private static void testBufferDuplicateDetection() {
        MessageBuffer buffer = new MessageBuffer("NODE_DUP");
        Message m = new Message("DUP_1", "A", "B", Priority.HIGH, "Unique Payload", 100, 2);

        MessageBuffer.AddResult r1 = buffer.addMessageWithStatus(m);
        assertEquals(MessageBuffer.AddResult.SUCCESS, r1, "First add should succeed");

        MessageBuffer.AddResult r2 = buffer.addMessageWithStatus(m);
        assertEquals(MessageBuffer.AddResult.DUPLICATE, r2, "Second add should detect DUPLICATE");
        assertEquals(1, buffer.size(), "Buffer size should remain 1");
    }

    // 4. Buffer Expired Message Handling
    private static void testBufferExpiredMessageHandling() {
        MessageBuffer buffer = new MessageBuffer("NODE_EXP");

        // Attempt to add pre-expired message
        Instant past = Instant.now().minusSeconds(10);
        Message alreadyExpired = new Message("E01", "A", "B", past.minusSeconds(10), past, Priority.LOW, "Expired", 1);
        MessageBuffer.AddResult res = buffer.addMessageWithStatus(alreadyExpired);
        assertEquals(MessageBuffer.AddResult.EXPIRED, res, "Pre-expired message should be rejected");

        // Add valid message then scan for expired
        Message valid = new Message("V01", "A", "B", Priority.HIGH, "Valid", 200, 1);
        buffer.addMessage(valid);
        List<Message> purged = buffer.removeExpiredMessages();
        assertEquals(0, purged.size(), "No messages should be purged when all valid");
        assertEquals(1, buffer.size(), "Valid message remains in buffer");
    }

    // 5. ContactManager Test
    private static void testContactManager() {
        Peer local = new Peer("A", "127.0.0.1", 5001);
        Peer peerB = new Peer("B", "127.0.0.1", 5002);
        Peer peerC = new Peer("C", "127.0.0.1", 5003);

        ContactManager cm = new ContactManager(local);
        assertTrue(!cm.isInContact(peerB), "Initially not in contact with B");

        cm.startContact(peerB);
        assertTrue(cm.isInContact(peerB), "Should be in contact with B");
        assertTrue(cm.isInContact("B"), "isInContact by ID should be true");
        assertTrue(!cm.isInContact(peerC), "Should NOT be in contact with C");
        assertEquals(1, cm.getActiveContacts().size(), "Active contacts should be 1");

        cm.endContact(peerB);
        assertTrue(!cm.isInContact(peerB), "After endContact, should not be in contact");
        assertEquals(0, cm.getActiveContacts().size(), "Active contacts should be 0");
    }

    // 6. Epidemic Router Test
    private static void testEpidemicRouter() {
        Peer local = new Peer("A", "127.0.0.1", 5001);
        Peer remote = new Peer("B", "127.0.0.1", 5002);
        MessageBuffer buf = new MessageBuffer("A");

        Message m1 = new Message("EP01", "A", "C", Priority.HIGH, "P1", 100, 1);
        Message m2 = new Message("EP02", "A", "D", Priority.LOW, "P2", 100, 4);
        Message m3 = new Message("EP03", "A", "E", Priority.MEDIUM, "P3", 100, 2);

        buf.addMessage(m1);
        buf.addMessage(m2);
        buf.addMessage(m3);

        Set<String> remoteKnown = new HashSet<>();
        remoteKnown.add("EP02"); // Remote already has EP02

        EpidemicRouter router = new EpidemicRouter();
        List<Message> selected = router.selectMessages(local, remote, buf, remoteKnown);

        assertEquals(2, selected.size(), "Epidemic should select exactly 2 messages (skipping EP02)");
        assertTrue(selected.contains(m1), "Should contain EP01");
        assertTrue(selected.contains(m3), "Should contain EP03");
        assertTrue(!selected.contains(m2), "Should NOT contain remote-known EP02");
    }

    // 7. Spray-and-Wait Router & Token Splitting Test
    private static void testSprayAndWaitRouter() {
        // Test Token Splitting logic
        SprayAndWaitRouter.TokenSplitResult split4 = SprayAndWaitRouter.splitTokens(4);
        assertEquals(2, split4.senderRemaining, "4 tokens -> sender keeps 2");
        assertEquals(2, split4.receiverAssigned, "4 tokens -> receiver gets 2");

        SprayAndWaitRouter.TokenSplitResult split3 = SprayAndWaitRouter.splitTokens(3);
        assertEquals(2, split3.senderRemaining, "3 tokens -> sender keeps 2");
        assertEquals(1, split3.receiverAssigned, "3 tokens -> receiver gets 1");

        SprayAndWaitRouter.TokenSplitResult split1 = SprayAndWaitRouter.splitTokens(1);
        assertEquals(1, split1.senderRemaining, "1 token -> sender keeps 1");
        assertEquals(0, split1.receiverAssigned, "1 token -> receiver gets 0 (wait phase)");

        // Test Routing selection
        Peer local = new Peer("A", "127.0.0.1", 5001);
        Peer relay = new Peer("B", "127.0.0.1", 5002);
        Peer destination = new Peer("D", "127.0.0.1", 5004);

        MessageBuffer buf = new MessageBuffer("A");
        Message sprayable = new Message("SW01", "A", "D", Priority.HIGH, "Sprayable", 100, 4);
        Message waitPhase = new Message("SW02", "A", "D", Priority.LOW, "Wait phase", 100, 1);

        buf.addMessage(sprayable);
        buf.addMessage(waitPhase);

        SprayAndWaitRouter router = new SprayAndWaitRouter();

        // Encountering relay B: only sprayable can be forwarded
        List<Message> toRelay = router.selectMessages(local, relay, buf);
        assertEquals(1, toRelay.size(), "Relay encounter should only select sprayable message");
        assertEquals("SW01", toRelay.get(0).getMessageId(), "Selected message should be SW01");

        // Encountering destination D: BOTH can be forwarded because D is destination!
        List<Message> toDest = router.selectMessages(local, destination, buf);
        assertEquals(2, toDest.size(), "Destination encounter should select both spray and wait messages");
    }

    // 8. Adaptive Router Test
    private static void testAdaptiveRouter() {
        AdaptiveRouter router = new AdaptiveRouter(0.40, 0.40, 0.20);

        MessageBuffer buf = new MessageBuffer("A", 1000);
        // Priority tests
        Message lowMsg = new Message("A_LOW", "A", "D", Priority.LOW, "L", 100, 2);
        Message highMsg = new Message("A_HIGH", "A", "D", Priority.HIGH, "H", 100, 2);

        assertDoubleEquals(0.33, router.calculateP(lowMsg), 0.01, "Low priority score");
        assertDoubleEquals(1.00, router.calculateP(highMsg), 0.01, "High priority score");

        // TTL urgency test: fresh vs near-expiry
        Message fresh = new Message("FRESH", "A", "D", Priority.HIGH, "F", 100, 2);
        double urgencyFresh = router.calculateT(fresh);
        assertTrue(urgencyFresh < 0.1, "Fresh message urgency should be close to 0.0");

        Instant past = Instant.now().minusSeconds(90);
        Instant nearExpiry = past.plusSeconds(100); // 10s left of 100s
        Message dying = new Message("DYING", "A", "D", past, nearExpiry, Priority.HIGH, "D", 2);
        double urgencyDying = router.calculateT(dying);
        assertTrue(urgencyDying > 0.85, "Near-expiry message urgency should be high (> 0.85)");

        // Buffer pressure
        buf.addMessage(fresh);
        double pressure = router.calculateB(buf);
        assertTrue(pressure >= 0.0 && pressure <= 1.0, "Buffer pressure must be clamped [0, 1]");

        // Sorting / Ranking
        Peer local = new Peer("A", "127.0.0.1", 5001);
        Peer remote = new Peer("B", "127.0.0.1", 5002);

        buf.clear();
        buf.addMessage(lowMsg);
        buf.addMessage(highMsg);
        buf.addMessage(dying);

        List<AdaptiveRouter.UtilityScore> scores = router.evaluateMessages(local, remote, buf, Collections.emptySet());
        assertEquals(3, scores.size(), "Should evaluate 3 messages");
        // Dying high-priority message should have highest utility (high P + high T)
        assertEquals("DYING", scores.get(0).message.getMessageId(), "Highest utility should be ranked first");
    }

    // 9. SQLite EventLogger Test
    private static void testEventLogger() {
        String testDb = "database/test_campusmesh.db";
        File f = new File(testDb);
        if (f.exists()) f.delete();

        try (EventLogger logger = new EventLogger(testDb)) {
            assertTrue(logger.isInitialized(), "Logger should initialize SQLite database successfully");

            assertTrue(logger.logPeerStarted("TEST_PEER"), "Should log PEER_STARTED");
            assertTrue(logger.logMessageCreated("A", "M_TEST", "B", "{\"note\":\"test\"}"), "Should log MESSAGE_CREATED");
            assertTrue(logger.logMessageStored("A", "M_TEST"), "Should log MESSAGE_STORED");
            assertTrue(logger.logMessageDuplicate("A", "M_TEST"), "Should log MESSAGE_DUPLICATE");

            assertEquals(4, logger.getEventCount(), "Event count in database should be 4");

            List<EventLogger.EventRecord> recent = logger.getRecentEvents(10);
            assertEquals(4, recent.size(), "Should retrieve 4 records");
            assertEquals("MESSAGE_DUPLICATE", recent.get(0).eventType, "Most recent event should be MESSAGE_DUPLICATE");
        } finally {
            if (f.exists()) f.delete();
        }
    }

    // 10. TCP Socket Framing Test
    private static void testTCPFraming() {
        int testPort = 5992;
        CountDownLatch latch = new CountDownLatch(1);
        final Message[] receivedMsg = new Message[1];

        try (TCPManager tcp = new TCPManager()) {
            tcp.startServer(testPort, (msg, socket) -> {
                receivedMsg[0] = msg;
                latch.countDown();
            });

            Thread.sleep(100);

            try (Socket client = tcp.connectToPeer("127.0.0.1", testPort)) {
                Message sent = new Message("TCP_TEST_1", "A", "B", Priority.MEDIUM, "Payload for framing test", 100, 2);
                tcp.sendMessage(client, sent);

                boolean ok = latch.await(2, TimeUnit.SECONDS);
                assertTrue(ok, "Message should be received over TCP within 2s");
                assertEquals("TCP_TEST_1", receivedMsg[0].getMessageId(), "Received message ID should match sent");
                assertEquals("Payload for framing test", receivedMsg[0].getPayload(), "Payload should match sent");
            }
        } catch (Exception e) {
            throw new RuntimeException("TCP framing test failed: " + e.getMessage(), e);
        }
    }

    // 11. Adaptive Router Weight Validation Test
    private static void testAdaptiveWeightValidation() {
        boolean threwNegative = false;
        try {
            new AdaptiveRouter(-0.1, 0.6, 0.5);
        } catch (IllegalArgumentException e) {
            threwNegative = true;
        }
        assertTrue(threwNegative, "Negative weights must throw IllegalArgumentException");

        boolean threwSum = false;
        try {
            new AdaptiveRouter(0.5, 0.5, 0.5);
        } catch (IllegalArgumentException e) {
            threwSum = true;
        }
        assertTrue(threwSum, "Weights not summing to 1.0 must throw IllegalArgumentException");
    }

    // 12. Message JSON Serialization & Parsing Test
    private static void testMessageJsonSerialization() {
        Message msg = new Message("J01", "A", "B", Priority.HIGH, "Hello \"CampusMesh\" \nworld!", 120, 4);
        String json = msg.toJson();
        Message parsed = Message.fromJson(json);
        assertEquals("J01", parsed.getMessageId(), "Parsed ID should match");
        assertEquals("A", parsed.getSourceId(), "Parsed Source should match");
        assertEquals("B", parsed.getDestinationId(), "Parsed Dest should match");
        assertEquals(Priority.HIGH, parsed.getPriority(), "Parsed Priority should match");
        assertEquals(4, parsed.getCopyTokens(), "Parsed tokens should match");
        assertEquals("Hello \"CampusMesh\" \nworld!", parsed.getPayload(), "Parsed payload should match");

        // Formatted JSON with arbitrary whitespace and newlines
        String formattedJson = "{\n"
                + "  \"messageId\": \"J02\",\n"
                + "  \"sourceId\": \"X\",\n"
                + "  \"destinationId\": \"Y\",\n"
                + "  \"priority\": \"LOW\",\n"
                + "  \"copyTokens\": 2,\n"
                + "  \"payload\": \"Formatted JSON test\"\n"
                + "}";
        Message parsedFormatted = Message.fromJson(formattedJson);
        assertEquals("J02", parsedFormatted.getMessageId(), "Parsed formatted ID should match");
        assertEquals("X", parsedFormatted.getSourceId(), "Parsed formatted Source should match");
        assertEquals("Y", parsedFormatted.getDestinationId(), "Parsed formatted Dest should match");
        assertEquals(Priority.LOW, parsedFormatted.getPriority(), "Parsed formatted Priority should match");
        assertEquals(2, parsedFormatted.getCopyTokens(), "Parsed formatted tokens should match");
        assertEquals("Formatted JSON test", parsedFormatted.getPayload(), "Parsed formatted payload should match");
    }
}
