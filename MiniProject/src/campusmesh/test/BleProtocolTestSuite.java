package campusmesh.test;

import campusmesh.ble.BleChunker;
import campusmesh.ble.BleConstants;
import campusmesh.ble.BlePacket;
import campusmesh.ble.BleTransferAssembler;
import campusmesh.discovery.DiscoveredPeer;
import campusmesh.discovery.PeerRegistry;
import campusmesh.model.Message;
import campusmesh.model.Priority;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive verification suite for Phase 4:
 * Bluetooth Low Energy (BLE) Framing, Chunking, Assembly, Integrity, and Multi-Transport Registry.
 */
public class BleProtocolTestSuite {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("      CAMPUSMESH BLE PROTOCOL TEST SUITE          ");
        System.out.println("==================================================\n");

        runTest("Default 23-byte MTU Slicing & Assembly", BleProtocolTestSuite::testDefaultMtuChunkingAndAssembly);
        runTest("Negotiated 512-byte MTU High-Speed Transfer", BleProtocolTestSuite::testNegotiatedMtuTransfer);
        runTest("CRC32 Checksum Validation & Corrupted Chunk Rejection", BleProtocolTestSuite::testCrcCorruptionDetection);
        runTest("Out-of-Order Chunk Arrival & Sequential Reassembly", BleProtocolTestSuite::testOutOfOrderChunkArrival);
        runTest("Duplicate Chunk Ingestion & Idempotency", BleProtocolTestSuite::testDuplicateChunkHandling);
        runTest("Stale Inbound Transfer Eviction & Timeout", BleProtocolTestSuite::testTransferTimeoutAndEviction);
        runTest("Multi-Transport (Wi-Fi + BLE) Registry Unification", BleProtocolTestSuite::testMultiTransportRegistryUnification);

        System.out.println("\n==================================================");
        System.out.printf("BLE TEST RESULTS: %d / %d PASSED%n", testsPassed, testsRun);
        System.out.println("==================================================");

        if (testsPassed != testsRun) {
            System.err.println("Some BLE protocol tests failed!");
            System.exit(1);
        } else {
            System.out.println("ALL BLE PROTOCOL TESTS PASSED!");
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
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(String.format("%s (expected: '%s', actual: '%s')", message, expected, actual));
    }

    // 1. Default 23-byte MTU slicing (20-byte max write)
    private static void testDefaultMtuChunkingAndAssembly() {
        Message original = new Message("BLE_MSG_1", "NODE_A", "NODE_B", Priority.HIGH,
                "Campus emergency: Severe weather alert! Evacuate north courtyard.", 300, 3);

        // At 20 bytes total packet size, payload per chunk is 20 - 13 = 7 bytes
        List<BlePacket> chunks = BleChunker.chunkMessage(original, 20);
        assertTrue(chunks.size() > 10, "A ~200 byte JSON message sliced at 20 bytes MTU must produce > 10 chunks (got " + chunks.size() + ")");

        AtomicReference<Message> assembledRef = new AtomicReference<>();
        BleTransferAssembler assembler = new BleTransferAssembler(new BleTransferAssembler.AssemblyListener() {
            @Override
            public void onTransferComplete(int transferId, Message message) {
                assembledRef.set(message);
            }
            @Override
            public void onTransferFailed(int transferId, String error) {
                throw new AssertionError("Transfer failed: " + error);
            }
        });

        BlePacket lastAck = null;
        for (BlePacket p : chunks) {
            byte[] raw = p.toBytes();
            BlePacket res = assembler.processIncomingBytes(raw);
            if (res != null) {
                lastAck = res;
            }
        }

        assertTrue(lastAck != null, "Assembler must return ACK upon completion");
        assertEquals(BleConstants.TYPE_ACK, lastAck.getType(), "Response packet must be TYPE_ACK");
        assertEquals((byte) 0x00, lastAck.getPayload()[0], "ACK status must be SUCCESS (0x00)");

        Message assembled = assembledRef.get();
        assertTrue(assembled != null, "Assembled message must not be null");
        assertEquals(original.getMessageId(), assembled.getMessageId(), "Message ID must match");
        assertEquals(original.getSourceId(), assembled.getSourceId(), "Source ID must match");
        assertEquals(original.getPayload(), assembled.getPayload(), "Payload must match exactly");
        assertEquals(original.getPriority(), assembled.getPriority(), "Priority must match");
    }

    // 2. Negotiated 512-byte MTU high-speed transfer
    private static void testNegotiatedMtuTransfer() {
        StringBuilder largePayload = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            largePayload.append("SensorReading[").append(i).append("]: Temp=24.5C, Humidity=60%; ");
        }

        Message largeMessage = new Message("BLE_LARGE_1", "SENSOR_HUB", "SERVER_NODE",
                Priority.MEDIUM, largePayload.toString(), 600, 1);

        // Negotiated MTU 512 -> max write 509 bytes -> payload per chunk ~ 496 bytes
        List<BlePacket> chunks = BleChunker.chunkMessage(largeMessage, 509);

        AtomicReference<Message> assembledRef = new AtomicReference<>();
        BleTransferAssembler assembler = new BleTransferAssembler(new BleTransferAssembler.AssemblyListener() {
            @Override
            public void onTransferComplete(int transferId, Message message) {
                assembledRef.set(message);
            }
            @Override
            public void onTransferFailed(int transferId, String error) {}
        });

        for (BlePacket p : chunks) {
            assembler.processIncomingBytes(p.toBytes());
        }

        Message assembled = assembledRef.get();
        assertTrue(assembled != null, "Large message must be assembled successfully");
        assertEquals(largePayload.toString(), assembled.getPayload(), "Payload content must match");
    }

    // 3. CRC corruption detection
    private static void testCrcCorruptionDetection() {
        Message original = new Message("CRC_TEST_1", "SENDER", "RECEIVER", Priority.LOW, "Integrity Check Payload", 60, 1);
        List<BlePacket> chunks = BleChunker.chunkMessage(original, 50);

        AtomicBoolean failedCalled = new AtomicBoolean(false);
        BleTransferAssembler assembler = new BleTransferAssembler(new BleTransferAssembler.AssemblyListener() {
            @Override
            public void onTransferComplete(int transferId, Message message) {
                throw new AssertionError("Corrupted transfer should never complete successfully");
            }
            @Override
            public void onTransferFailed(int transferId, String error) {
                failedCalled.set(true);
            }
        });

        // Corrupt a byte in the payload of the first chunk
        byte[] firstRaw = chunks.get(0).toBytes();
        firstRaw[firstRaw.length - 1] ^= 0xFF; // Flip all bits of the last payload byte

        BlePacket ackResponse = null;
        for (int i = 0; i < chunks.size(); i++) {
            byte[] raw = (i == 0) ? firstRaw : chunks.get(i).toBytes();
            BlePacket resp = assembler.processIncomingBytes(raw);
            if (resp != null) ackResponse = resp;
        }

        assertTrue(failedCalled.get(), "Failure callback must be triggered on CRC mismatch");
        assertTrue(ackResponse != null, "Assembler must return an ACK with error code");
        assertEquals((byte) 0x01, ackResponse.getPayload()[0], "ACK status must indicate CRC_MISMATCH (0x01)");
    }

    // 4. Out-of-order chunk arrival
    private static void testOutOfOrderChunkArrival() {
        Message original = new Message("OUT_OF_ORDER_1", "NODE_X", "NODE_Y", Priority.HIGH,
                "Testing out of order delivery over opportunistic GATT links.", 120, 2);
        List<BlePacket> chunks = BleChunker.chunkMessage(original, 30);
        assertTrue(chunks.size() >= 4, "Must produce multiple chunks");

        // Reverse the chunk list to simulate completely out-of-order arrival
        Collections.reverse(chunks);

        AtomicReference<Message> assembledRef = new AtomicReference<>();
        BleTransferAssembler assembler = new BleTransferAssembler(new BleTransferAssembler.AssemblyListener() {
            @Override
            public void onTransferComplete(int transferId, Message message) {
                assembledRef.set(message);
            }
            @Override
            public void onTransferFailed(int transferId, String error) {}
        });

        for (BlePacket p : chunks) {
            assembler.processIncomingBytes(p.toBytes());
        }

        Message assembled = assembledRef.get();
        assertTrue(assembled != null, "Reversed chunk sequence must still successfully assemble");
        assertEquals("Testing out of order delivery over opportunistic GATT links.", assembled.getPayload(), "Payload must match");
    }

    // 5. Duplicate chunk handling
    private static void testDuplicateChunkHandling() {
        Message original = new Message("DUP_CHUNK_1", "SRC", "DST", Priority.MEDIUM, "Duplicate chunk resilience", 120, 1);
        List<BlePacket> chunks = BleChunker.chunkMessage(original, 40);

        AtomicReference<Message> assembledRef = new AtomicReference<>();
        BleTransferAssembler assembler = new BleTransferAssembler(new BleTransferAssembler.AssemblyListener() {
            @Override
            public void onTransferComplete(int transferId, Message message) {
                assembledRef.set(message);
            }
            @Override
            public void onTransferFailed(int transferId, String error) {}
        });

        // Feed each chunk twice
        for (BlePacket p : chunks) {
            assembler.processIncomingBytes(p.toBytes());
            assembler.processIncomingBytes(p.toBytes()); // Duplicate write
        }

        Message assembled = assembledRef.get();
        assertTrue(assembled != null, "Duplicate chunks must be absorbed without failure");
        assertEquals("Duplicate chunk resilience", assembled.getPayload(), "Payload must match");
    }

    // 6. Transfer timeout & eviction
    private static void testTransferTimeoutAndEviction() {
        AtomicBoolean timeoutTriggered = new AtomicBoolean(false);
        // Short 50ms timeout for testing
        BleTransferAssembler assembler = new BleTransferAssembler(new BleTransferAssembler.AssemblyListener() {
            @Override
            public void onTransferComplete(int transferId, Message message) {}
            @Override
            public void onTransferFailed(int transferId, String error) {
                timeoutTriggered.set(true);
            }
        }, 50L);

        // Send chunk 0 of 2, then abandon
        BlePacket partial = new BlePacket(BleConstants.TYPE_CHUNK, 999, 0, 2, 0x12345678, new byte[]{0x01, 0x02});
        assembler.processIncomingPacket(partial);
        assertEquals(1, assembler.getActiveTransferCount(), "Active transfer count should be 1");

        try {
            Thread.sleep(70);
        } catch (InterruptedException ignored) {}

        assembler.purgeExpiredTransfers();
        assertEquals(0, assembler.getActiveTransferCount(), "Expired transfer should be purged");
        assertTrue(timeoutTriggered.get(), "Timeout failure callback must be fired");
    }

    // 7. Multi-Transport Registry Unification
    private static void testMultiTransportRegistryUnification() {
        PeerRegistry registry = new PeerRegistry(10_000);

        // 1. Peer observed via Wi-Fi mDNS first
        DiscoveredPeer initialWifiPeer = new DiscoveredPeer("DUAL_NODE", "Dual Device", "ANDROID", "192.168.1.180", 5001);
        initialWifiPeer.addTransport(DiscoveredPeer.TRANSPORT_WIFI);
        registry.registerOrUpdate(initialWifiPeer);

        DiscoveredPeer retrieved1 = registry.getPeer("DUAL_NODE");
        assertTrue(retrieved1.getSupportedTransports().contains(DiscoveredPeer.TRANSPORT_WIFI), "Must have WIFI");
        assertTrue(!retrieved1.getSupportedTransports().contains(DiscoveredPeer.TRANSPORT_BLE), "Must not have BLE initially");

        // 2. Same peer observed via BLE beacon / GATT scan
        DiscoveredPeer bleObservation = new DiscoveredPeer("DUAL_NODE", "Dual Device", "ANDROID", "127.0.0.1", 5001);
        bleObservation.addTransport(DiscoveredPeer.TRANSPORT_BLE);
        registry.registerOrUpdate(bleObservation);

        DiscoveredPeer retrieved2 = registry.getPeer("DUAL_NODE");
        assertTrue(retrieved2.getSupportedTransports().contains(DiscoveredPeer.TRANSPORT_WIFI), "Must retain WIFI transport");
        assertTrue(retrieved2.getSupportedTransports().contains(DiscoveredPeer.TRANSPORT_BLE), "Must now also contain BLE transport");
        assertEquals("192.168.1.180", retrieved2.getIpAddress(), "Must preserve routable IP from Wi-Fi");
    }
}
