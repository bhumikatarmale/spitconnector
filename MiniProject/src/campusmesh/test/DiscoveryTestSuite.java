package campusmesh.test;

import campusmesh.contact.ContactManager;
import campusmesh.discovery.DiscoveredPeer;
import campusmesh.discovery.DiscoveryManager;
import campusmesh.discovery.PeerRegistry;
import campusmesh.discovery.wifi.WifiDiscoveryService;
import campusmesh.model.Message;
import campusmesh.model.Peer;
import campusmesh.model.Priority;
import campusmesh.networking.TCPManager;

import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automated test suite for Phase 1: Wi-Fi Discovery & Peer Registry.
 */
public class DiscoveryTestSuite {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("    CAMPUSMESH DISCOVERY TEST SUITE     ");
        System.out.println("========================================\n");

        runTest("Peer Registry Deduplication", DiscoveryTestSuite::testPeerRegistryDeduplication);
        runTest("Multi-Transport Metadata Merging", DiscoveryTestSuite::testPeerRegistryTransportMerge);
        runTest("Stale Peer Purging & Expiry Callback", DiscoveryTestSuite::testPeerRegistryStaleExpiry);
        runTest("DiscoveredPeer to Legacy Peer Bridge", DiscoveryTestSuite::testDiscoveredPeerBridge);
        runTest("DiscoveryManager Auto-Sync with ContactManager", DiscoveryTestSuite::testContactManagerAutoSync);
        runTest("Live Dynamic Subnet Beacon Discovery", DiscoveryTestSuite::testLiveDiscoveryBetweenTwoNodes);
        runTest("End-to-End Dynamic Discovery & TCP Transfer", DiscoveryTestSuite::testDynamicDiscoveryAndTCPTransfer);

        System.out.println("\n========================================");
        System.out.printf("DISCOVERY TEST RESULTS: %d / %d PASSED%n", testsPassed, testsRun);
        System.out.println("========================================");

        if (testsPassed != testsRun) {
            System.err.println("Some discovery tests failed!");
            System.exit(1);
        } else {
            System.out.println("ALL DISCOVERY TESTS PASSED SUCCESSFULLY!");
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

    // 1. Peer Registry Deduplication Test
    private static void testPeerRegistryDeduplication() {
        try (PeerRegistry registry = new PeerRegistry(10000)) {
            DiscoveredPeer obs1 = new DiscoveredPeer("NODE_X", "Node X Laptop", "WINDOWS", "192.168.1.10", 5001);
            DiscoveredPeer obs2 = new DiscoveredPeer("NODE_X", "Node X Laptop", "WINDOWS", "192.168.1.10", 5001);

            boolean firstAdded = registry.registerOrUpdate(obs1);
            boolean secondAdded = registry.registerOrUpdate(obs2);

            assertTrue(firstAdded, "First observation should register as new peer");
            assertTrue(!secondAdded, "Second observation of same peer ID must not create new entry");
            assertEquals(1, registry.size(), "Registry size must remain 1 after duplicate observation");
        }
    }

    // 2. Multi-Transport Metadata Merging Test
    private static void testPeerRegistryTransportMerge() {
        try (PeerRegistry registry = new PeerRegistry(10000)) {
            DiscoveredPeer wifiObs = new DiscoveredPeer("NODE_Y", "Y Phone", "ANDROID", "192.168.1.25", 5002);
            wifiObs.addTransport("WIFI");
            registry.registerOrUpdate(wifiObs);

            DiscoveredPeer bleObs = new DiscoveredPeer("NODE_Y", "Y Phone", "ANDROID", "192.168.1.25", 5002);
            bleObs.addTransport("BLE");
            registry.registerOrUpdate(bleObs);

            DiscoveredPeer merged = registry.getPeer("NODE_Y");
            assertTrue(merged != null, "Peer must exist in registry");
            assertTrue(merged.getSupportedTransports().contains("WIFI"), "Must retain WIFI transport");
            assertTrue(merged.getSupportedTransports().contains("BLE"), "Must merge BLE transport");
            assertEquals(1, registry.size(), "Merged observations must remain single logical peer");
        }
    }

    // 3. Stale Peer Purging & Expiry Callback Test
    private static void testPeerRegistryStaleExpiry() {
        // Fast expiry: 200 ms
        try (PeerRegistry registry = new PeerRegistry(200)) {
            AtomicReference<String> lostPeerId = new AtomicReference<>();
            registry.addListener(new PeerRegistry.PeerRegistryListener() {
                @Override
                public void onPeerDiscovered(DiscoveredPeer peer) {}
                @Override
                public void onPeerUpdated(DiscoveredPeer peer) {}
                @Override
                public void onPeerLost(DiscoveredPeer peer) {
                    lostPeerId.set(peer.getPeerId());
                }
            });

            DiscoveredPeer peer = new DiscoveredPeer("EXPIRING_NODE", "Expiring", "UBUNTU", "127.0.0.1", 5003);
            registry.registerOrUpdate(peer);
            assertEquals(1, registry.size(), "Should contain 1 peer");

            try {
                Thread.sleep(350); // Exceeds 200 ms timeout
            } catch (InterruptedException ignored) {}

            List<DiscoveredPeer> purged = registry.purgeStalePeers();
            assertEquals(1, purged.size(), "Should have purged 1 stale peer");
            assertEquals(0, registry.size(), "Registry should now be empty");
            assertEquals("EXPIRING_NODE", lostPeerId.get(), "Listener should receive onPeerLost with correct peer ID");
        }
    }

    // 4. DiscoveredPeer to Legacy Peer Bridge Test
    private static void testDiscoveredPeerBridge() {
        DiscoveredPeer dp = new DiscoveredPeer("PEER_BRIDGE", "Display Name", "WINDOWS", "10.0.0.5", 5005);
        Peer legacyPeer = dp.toPeer();

        assertEquals("PEER_BRIDGE", legacyPeer.getPeerId(), "Peer ID must match");
        assertEquals("10.0.0.5", legacyPeer.getIpAddress(), "IP address must match");
        assertEquals(5005, legacyPeer.getPort(), "Port must match");

        Peer fromFactory = Peer.fromDiscoveredPeer(dp);
        assertEquals(legacyPeer, fromFactory, "Factory creation must match toPeer()");
    }

    // 5. DiscoveryManager Auto-Sync with ContactManager Test
    private static void testContactManagerAutoSync() {
        Peer local = new Peer("LOCAL_A", "127.0.0.1", 5001);
        ContactManager cm = new ContactManager(local);

        try (DiscoveryManager dm = new DiscoveryManager(local, cm)) {
            DiscoveredPeer remote = new DiscoveredPeer("REMOTE_B", "Remote B", "UBUNTU", "127.0.0.1", 5002);
            dm.getRegistry().registerOrUpdate(remote);

            assertTrue(cm.isInContact("REMOTE_B"), "ContactManager should automatically reflect discovered peer");

            dm.getRegistry().removePeer("REMOTE_B");
            assertTrue(!cm.isInContact("REMOTE_B"), "ContactManager should automatically disconnect lost peer");
        }
    }

    // 6. Live Dynamic Subnet Beacon Discovery Test
    private static void testLiveDiscoveryBetweenTwoNodes() {
        Peer peer1 = new Peer("NODE_LIVE_1", "127.0.0.1", 5981);
        Peer peer2 = new Peer("NODE_LIVE_2", "127.0.0.1", 5982);

        PeerRegistry reg1 = new PeerRegistry(10000);
        PeerRegistry reg2 = new PeerRegistry(10000);

        CountDownLatch node2DiscoveredBy1 = new CountDownLatch(1);
        reg1.addListener(new PeerRegistry.PeerRegistryListener() {
            @Override
            public void onPeerDiscovered(DiscoveredPeer peer) {
                if ("NODE_LIVE_2".equalsIgnoreCase(peer.getPeerId())) {
                    node2DiscoveredBy1.countDown();
                }
            }
            @Override
            public void onPeerUpdated(DiscoveredPeer peer) {}
            @Override
            public void onPeerLost(DiscoveredPeer peer) {}
        });

        try (WifiDiscoveryService svc1 = new WifiDiscoveryService(peer1, reg1);
             WifiDiscoveryService svc2 = new WifiDiscoveryService(peer2, reg2)) {

            svc1.start();
            svc2.start();

            boolean ok = node2DiscoveredBy1.await(5, TimeUnit.SECONDS);
            assertTrue(ok, "Node 1 should dynamically discover Node 2 via beacon within 5s");

            DiscoveredPeer found = reg1.getPeer("NODE_LIVE_2");
            assertTrue(found != null, "Found peer must be non-null");
            assertEquals(5982, found.getPort(), "Discovered port should match Node 2's TCP port");
        } catch (Exception e) {
            throw new RuntimeException("Live discovery failed: " + e.getMessage(), e);
        } finally {
            reg1.close();
            reg2.close();
        }
    }

    // 7. End-to-End Dynamic Discovery & TCP Transfer Test
    private static void testDynamicDiscoveryAndTCPTransfer() {
        int portB = 5986;
        Peer peerA = new Peer("DISCO_CLIENT_A", "127.0.0.1", 5985);
        Peer peerB = new Peer("DISCO_SERVER_B", "127.0.0.1", portB);

        PeerRegistry regA = new PeerRegistry(10000);
        PeerRegistry regB = new PeerRegistry(10000);

        CountDownLatch serverBDiscoveredLatch = new CountDownLatch(1);
        CountDownLatch msgReceivedAtBLatch = new CountDownLatch(1);
        final Message[] receivedHolder = new Message[1];

        regA.addListener(new PeerRegistry.PeerRegistryListener() {
            @Override
            public void onPeerDiscovered(DiscoveredPeer peer) {
                if ("DISCO_SERVER_B".equalsIgnoreCase(peer.getPeerId())) {
                    serverBDiscoveredLatch.countDown();
                }
            }
            @Override
            public void onPeerUpdated(DiscoveredPeer peer) {}
            @Override
            public void onPeerLost(DiscoveredPeer peer) {}
        });

        try (TCPManager tcpB = new TCPManager();
             TCPManager tcpA = new TCPManager();
             WifiDiscoveryService discA = new WifiDiscoveryService(peerA, regA);
             WifiDiscoveryService discB = new WifiDiscoveryService(peerB, regB)) {

            // Server B starts TCP server
            tcpB.startServer(portB, (msg, socket) -> {
                receivedHolder[0] = msg;
                msgReceivedAtBLatch.countDown();
            });

            // Start discovery on both nodes
            discB.start();
            discA.start();

            // Wait for A to discover B dynamically without knowing B's IP/port in advance!
            boolean discovered = serverBDiscoveredLatch.await(5, TimeUnit.SECONDS);
            assertTrue(discovered, "Node A must discover Node B dynamically via network beacon");

            DiscoveredPeer resolvedB = regA.getPeer("DISCO_SERVER_B");
            assertTrue(resolvedB != null, "Resolved peer B cannot be null");

            // Client A connects to dynamically resolved endpoint
            try (Socket socket = tcpA.connectToPeer(resolvedB.getIpAddress(), resolvedB.getPort())) {
                Message testMsg = new Message("M_DISCO_1", "DISCO_CLIENT_A", "DISCO_SERVER_B",
                        Priority.HIGH, "Autonomous dynamic discovery payload", 60, 2);
                tcpA.sendMessage(socket, testMsg);

                boolean msgOk = msgReceivedAtBLatch.await(3, TimeUnit.SECONDS);
                assertTrue(msgOk, "Server B must receive the message frame sent to dynamically resolved port");
                assertEquals("M_DISCO_1", receivedHolder[0].getMessageId(), "Message ID must match");
                assertEquals("Autonomous dynamic discovery payload", receivedHolder[0].getPayload(), "Payload must match");
            }
        } catch (Exception e) {
            throw new RuntimeException("Dynamic discovery and TCP transfer failed: " + e.getMessage(), e);
        } finally {
            regA.close();
            regB.close();
        }
    }
}
