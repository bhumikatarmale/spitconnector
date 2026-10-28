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
import campusmesh.routing.RoutingEngine;

import java.awt.Desktop;
import campusmesh.networking.TCPManager;
import java.io.File;
import java.net.URI;

/**
 * Main application runner for the CampusMesh Web Dashboard GUI.
 * Initializes the full DTN engine, starts discovery services, and serves the
 * real-time web dashboard at http://localhost:8080.
 */
public class CampusMeshWebRunner {

    public static final int DEFAULT_WEB_PORT = 8080;
    public static final int DEFAULT_TCP_PORT = 5001;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("                 CAMPUSMESH DECENTRALIZED MESH WEB DASHBOARD                    ");
        System.out.println("================================================================================\n");

        boolean isPublic = true;
        boolean mockPeers = true;
        int webPort = DEFAULT_WEB_PORT;
        String peerId = "LAPTOP_WIN";

        for (String arg : args) {
            if ("--public".equalsIgnoreCase(arg)) {
                isPublic = true;
            } else if ("--private".equalsIgnoreCase(arg) || "--local".equalsIgnoreCase(arg)) {
                isPublic = false;
            } else if ("--no-mock".equalsIgnoreCase(arg)) {
                mockPeers = false;
            } else if (arg.startsWith("--port=")) {
                try { webPort = Integer.parseInt(arg.substring(7)); } catch (Exception ignored) {}
            } else if (arg.startsWith("--peer=")) {
                peerId = arg.substring(7);
            } else if (arg.matches("\\d+")) {
                try { webPort = Integer.parseInt(arg); } catch (Exception ignored) {}
            } else if (!arg.startsWith("-")) {
                peerId = arg;
            }
        }

        String bindHost = isPublic ? "0.0.0.0" : "127.0.0.1";
        String lanIpForPeer = CampusMeshHttpServer.getLocalLanIp();
        Peer localPeer = new Peer(peerId, lanIpForPeer, DEFAULT_TCP_PORT);

        MessageBuffer buffer = new MessageBuffer(peerId);
        ContactManager contactManager = new ContactManager(localPeer);
        DiscoveryManager discoveryManager = new DiscoveryManager(localPeer, contactManager);
        RoutingEngine routingEngine = new RoutingEngine(new AdaptiveRouter(0.40, 0.40, 0.20));
        EventLogger logger = new EventLogger("database/campusmesh.db");
        MultiTransportSender sender = new MultiTransportSender();

        if (mockPeers) {
            // Pre-populate with initial demonstration messages
            Message m1 = new Message("M001", peerId, "LAPTOP_UBU", Priority.HIGH,
                    "Campus power outage scheduled at 22:30. Relocate all critical compute nodes.", 3600, 4);
            Message m2 = new Message("M002", peerId, "PHONE_AND", Priority.MEDIUM,
                    "Library study group meeting on second floor north wing.", 7200, 2);
            Message m3 = new Message("M003", peerId, "IPHONE_IOS", Priority.LOW,
                    "Lost keys found near cafeteria; claim at reception.", 1800, 1);

            buffer.addMessage(m1);
            buffer.addMessage(m2);
            buffer.addMessage(m3);

            // Pre-populate mock peers for immediate visualization
            DiscoveredPeer andPeer = new DiscoveredPeer("PHONE_AND", "Galaxy S23", "ANDROID", "192.168.1.155", 5005);
            andPeer.addTransport(DiscoveredPeer.TRANSPORT_WIFI);
            andPeer.addTransport(DiscoveredPeer.TRANSPORT_BLE);
            discoveryManager.getRegistry().registerOrUpdate(andPeer);

            DiscoveredPeer iosPeer = new DiscoveredPeer("IPHONE_IOS", "iPhone 15 Pro", "IOS", "192.168.1.140", 5000);
            iosPeer.addTransport(DiscoveredPeer.TRANSPORT_BLE);
            discoveryManager.getRegistry().registerOrUpdate(iosPeer);

            DiscoveredPeer ubuPeer = new DiscoveredPeer("LAPTOP_UBU", "Lab Workstation", "UBUNTU", "192.168.1.190", 5002);
            ubuPeer.addTransport(DiscoveredPeer.TRANSPORT_WIFI);
            discoveryManager.getRegistry().registerOrUpdate(ubuPeer);
        }

        // Start active Wi-Fi and BLE discovery
        try {
            discoveryManager.startWifiDiscovery();
            discoveryManager.startBleDiscovery();
        } catch (Exception e) {
            System.out.println("[DISCOVERY NOTE] " + e.getMessage());
        }

        // Receive messages from real peers (Android/iOS/other laptops) on the advertised TCP port
        TCPManager tcpServer = new TCPManager();
        try {
            tcpServer.startServer(DEFAULT_TCP_PORT, (msg, socket) -> {
                MessageBuffer.AddResult r = buffer.addMessageWithStatus(msg);
                logger.logMessageCreated(msg.getSourceId(), msg.getMessageId(), msg.getDestinationId(), msg.toJson());
                System.out.println("[TCP RX] " + msg.getMessageId() + " from " + msg.getSourceId() + " -> " + r);
            });
            System.out.println("[TCP] Listening for peer messages on port " + DEFAULT_TCP_PORT);
        } catch (Exception e) {
            System.err.println("[TCP WARNING] Could not listen on port " + DEFAULT_TCP_PORT + ": " + e.getMessage()
                    + " (port in use or blocked by firewall). Peers will not be able to send to this node.");
        }

        File webRoot = new File("web");
        CampusMeshHttpServer server = new CampusMeshHttpServer(
                bindHost, webPort, webRoot, localPeer, buffer, contactManager, discoveryManager, routingEngine, logger, sender);

        try {
            server.start();

            String lanIp = CampusMeshHttpServer.getLocalLanIp();
            String url = "http://localhost:" + webPort;
            String mobileUrl = "http://" + lanIp + ":" + webPort;
            System.out.printf("[SUCCESS] CampusMesh Web Dashboard is LIVE!%n");
            System.out.printf("  Local Peer ID:      %s%n", peerId);
            System.out.printf("  Bound Host:         %s%n", bindHost);
            System.out.printf("  Web URL (Laptop):   %s%n", url);
            System.out.printf("  Mobile URL (Phone): %s%n", mobileUrl);
            System.out.printf("  API Auth Token:     %s%n", server.getApiToken());
            System.out.printf("  API Endpoints:      %s/api/status | %s/api/peers%n", url, url);
            System.out.println("  [SECURITY] POST endpoints require 'X-CampusMesh-Token: <token>'");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("  HOW TO CONNECT YOUR MOBILE PHONE OR TABLET:");
            System.out.println("  1. Connect your phone to the same Wi-Fi (or turn on Laptop Mobile Hotspot).");
            System.out.printf("  2. Open Chrome or Safari on your phone and go to:%n");
            System.out.printf("     >>  %s/#token=%s%n", mobileUrl, server.getApiToken());
            System.out.println("  3. Your phone instantly appears on the live Radar as an online node!");
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Press Ctrl+C to stop the dashboard server.\n");

            // Automatically open browser on desktop
            openBrowser(url);

            // Keep main thread alive
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Stopping CampusMesh Web Dashboard...");
                server.stop();
                discoveryManager.close();
                logger.close();
                sender.close();
                tcpServer.close();
            }));

            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("[SERVER ERROR] Failed to start web dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                // Windows fallback
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                } else if (os.contains("nix") || os.contains("nux")) {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                }
            }
        } catch (Exception ignored) {}
    }
}
