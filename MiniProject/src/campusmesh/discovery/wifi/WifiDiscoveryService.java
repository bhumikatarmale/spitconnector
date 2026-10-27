package campusmesh.discovery.wifi;

import campusmesh.discovery.DiscoveredPeer;
import campusmesh.discovery.DiscoveryService;
import campusmesh.discovery.PeerRegistry;
import campusmesh.model.Peer;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hybrid Wi-Fi Discovery Service for CampusMesh.
 * 
 * Implements:
 * 1. Standard mDNS / DNS-SD (_campusmesh._tcp.local.) via JmDNS for cross-platform
 *    interoperability with Android NSD and iOS Bonjour.
 * 2. UDP Subnet Multicast Beacon (239.255.77.88:5354) as a guaranteed fallback
 *    when OS-level mDNS port 5353 is restricted or loopback instances are active.
 */
public class WifiDiscoveryService implements DiscoveryService {

    public static final String MDNS_SERVICE_TYPE = "_campusmesh._tcp.local.";
    public static final String MULTICAST_GROUP = "239.255.77.88";
    public static final int MULTICAST_PORT = 5354;
    public static final int BEACON_INTERVAL_MS = 2500;

    private final Peer localPeer;
    private final PeerRegistry registry;
    private final String platformName;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private JmDNS jmdns;
    private ServiceInfo registeredServiceInfo;

    private MulticastSocket beaconSocket;
    private InetAddress beaconGroup;
    private ScheduledExecutorService beaconScheduler;
    private Thread listenerThread;

    public WifiDiscoveryService(Peer localPeer, PeerRegistry registry) {
        if (localPeer == null || registry == null) {
            throw new IllegalArgumentException("localPeer and registry cannot be null");
        }
        this.localPeer = localPeer;
        this.registry = registry;
        this.platformName = detectPlatform();
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "WINDOWS";
        if (os.contains("mac")) return "MACOS";
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) return "UBUNTU";
        return "JAVA";
    }

    @Override
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }
        running.set(true);

        startUdpMulticastBeacon();
        startMdnsService();
    }

    private void startMdnsService() {
        try {
            InetAddress localAddr = getPreferredLocalAddress();
            this.jmdns = JmDNS.create(localAddr, localPeer.getPeerId() + "-campusmesh");

            Map<String, String> txtProps = new HashMap<>();
            txtProps.put("peerId", localPeer.getPeerId());
            txtProps.put("name", localPeer.getPeerId());
            txtProps.put("platform", platformName);
            txtProps.put("port", String.valueOf(localPeer.getPort()));
            txtProps.put("ver", "1.0");
            txtProps.put("caps", "WIFI,DTN");

            String serviceName = "campusmesh-" + localPeer.getPeerId();
            this.registeredServiceInfo = ServiceInfo.create(
                    MDNS_SERVICE_TYPE,
                    serviceName,
                    localPeer.getPort(),
                    0, 0,
                    txtProps
            );

            jmdns.registerService(registeredServiceInfo);

            jmdns.addServiceListener(MDNS_SERVICE_TYPE, new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    jmdns.requestServiceInfo(event.getType(), event.getName(), 1000);
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    String name = event.getName();
                    if (name != null && name.startsWith("campusmesh-")) {
                        String peerId = name.substring("campusmesh-".length());
                        if (!peerId.equalsIgnoreCase(localPeer.getPeerId())) {
                            registry.removePeer(peerId);
                        }
                    }
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    ServiceInfo info = event.getInfo();
                    if (info == null) return;

                    String peerId = info.getPropertyString("peerId");
                    if (peerId == null || peerId.isEmpty()) {
                        String n = info.getName();
                        if (n != null && n.startsWith("campusmesh-")) {
                            peerId = n.substring("campusmesh-".length());
                        }
                    }

                    if (peerId == null || peerId.equalsIgnoreCase(localPeer.getPeerId())) {
                        return; // Ignore own announcement
                    }

                    String host = (info.getHostAddresses().length > 0) ? info.getHostAddresses()[0] : "127.0.0.1";
                    int port = info.getPort();
                    String plat = info.getPropertyString("platform");

                    DiscoveredPeer dp = new DiscoveredPeer(peerId, peerId, plat, host, port);
                    dp.addTransport("WIFI");
                    registry.registerOrUpdate(dp);
                }
            });
        } catch (Exception e) {
            System.err.println("[MDNS WARNING] Standard mDNS binding skipped (" + e.getMessage() + "), using subnet multicast beacon.");
        }
    }

    private void startUdpMulticastBeacon() {
        try {
            this.beaconGroup = InetAddress.getByName(MULTICAST_GROUP);
            this.beaconSocket = new MulticastSocket(MULTICAST_PORT);
            this.beaconSocket.setReuseAddress(true);

            // Join multicast group across active network interfaces
            try {
                beaconSocket.joinGroup(beaconGroup);
            } catch (Exception e) {
                // In some OS/network configurations, joinGroup on specific interface is needed
            }

            // Start background receiver thread
            this.listenerThread = new Thread(this::listenForBeacons, "CampusMesh-BeaconListener-" + localPeer.getPeerId());
            this.listenerThread.setDaemon(true);
            this.listenerThread.start();

            // Start periodic announcer
            this.beaconScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CampusMesh-BeaconEmitter-" + localPeer.getPeerId());
                t.setDaemon(true);
                return t;
            });

            this.beaconScheduler.scheduleAtFixedRate(
                    this::broadcastBeacon,
                    100,
                    BEACON_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
        } catch (IOException e) {
            System.err.println("[BEACON ERROR] Could not start UDP beacon listener: " + e.getMessage());
        }
    }

    private void broadcastBeacon() {
        if (!running.get() || beaconSocket == null || beaconSocket.isClosed()) {
            return;
        }
        try {
            String json = String.format(
                    "{\"type\":\"BEACON\",\"peerId\":\"%s\",\"platform\":\"%s\",\"port\":%d,\"ts\":%d}",
                    localPeer.getPeerId(), platformName, localPeer.getPort(), System.currentTimeMillis()
            );
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, beaconGroup, MULTICAST_PORT);
            beaconSocket.send(packet);
        } catch (IOException ignored) {}
    }

    private void listenForBeacons() {
        byte[] buffer = new byte[2048];
        while (running.get() && beaconSocket != null && !beaconSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                beaconSocket.receive(packet);

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handleIncomingBeacon(msg, packet.getAddress().getHostAddress());
            } catch (IOException e) {
                if (!running.get()) break;
            }
        }
    }

    private void handleIncomingBeacon(String json, String senderIp) {
        if (json == null || !json.contains("\"type\":\"BEACON\"")) {
            return;
        }

        String remotePeerId = extractField(json, "peerId");
        if (remotePeerId == null || remotePeerId.equalsIgnoreCase(localPeer.getPeerId())) {
            return; // Ignore own beacon
        }

        String plat = extractField(json, "platform");
        int port = extractInt(json, "port", 5001);

        DiscoveredPeer dp = new DiscoveredPeer(remotePeerId, remotePeerId, plat, senderIp, port);
        dp.addTransport("WIFI");
        registry.registerOrUpdate(dp);
    }

    private static String extractField(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start == -1) return null;
        int end = json.indexOf("\"", start + marker.length());
        if (end == -1) return null;
        return json.substring(start + marker.length(), end);
    }

    private static int extractInt(String json, String key, int defVal) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start == -1) return defVal;
        int end = json.indexOf(",", start + marker.length());
        if (end == -1) end = json.indexOf("}", start + marker.length());
        if (end == -1) return defVal;
        try {
            return Integer.parseInt(json.substring(start + marker.length(), end).trim());
        } catch (NumberFormatException e) {
            return defVal;
        }
    }

    private static InetAddress getPreferredLocalAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') == -1) {
                        return addr;
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            return InetAddress.getLocalHost();
        } catch (Exception e) {
            return InetAddress.getLoopbackAddress();
        }
    }

    @Override
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (beaconScheduler != null) {
            beaconScheduler.shutdownNow();
        }

        if (jmdns != null) {
            try {
                if (registeredServiceInfo != null) {
                    jmdns.unregisterService(registeredServiceInfo);
                }
                jmdns.close();
            } catch (Exception ignored) {}
            jmdns = null;
        }

        if (beaconSocket != null) {
            try {
                if (beaconGroup != null) {
                    beaconSocket.leaveGroup(beaconGroup);
                }
                beaconSocket.close();
            } catch (Exception ignored) {}
            beaconSocket = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public PeerRegistry getRegistry() {
        return registry;
    }
}
