package campusmesh.ble;

import campusmesh.discovery.DiscoveredPeer;
import campusmesh.discovery.DiscoveryService;
import campusmesh.discovery.PeerRegistry;

import java.io.IOException;

/**
 * Bluetooth Low Energy discovery coordinator.
 * Advertises local CampusMesh GATT service (0000FE60) and scans for nearby BLE peers,
 * populating the unified PeerRegistry with multi-transport records.
 */
public class BleDiscoveryService implements DiscoveryService {

    private final String localPeerId;
    private final String localDisplayName;
    private final String localPlatform;
    private final PeerRegistry registry;
    private final BleNativeBridge bridge;

    private volatile boolean running = false;

    public BleDiscoveryService(String peerId, String displayName, String platform, PeerRegistry registry) {
        this(peerId, displayName, platform, registry, null);
    }

    public BleDiscoveryService(String peerId, String displayName, String platform, PeerRegistry registry, BleNativeBridge bridge) {
        this.localPeerId = peerId;
        this.localDisplayName = displayName;
        this.localPlatform = platform != null ? platform : "WINDOWS";
        this.registry = registry;
        this.bridge = (bridge != null) ? bridge : new DefaultSimulationBridge();
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) return;

        if (bridge.isAvailable()) {
            bridge.startAdvertising(localPeerId, localDisplayName, localPlatform);
            bridge.startScanning(discovered -> {
                if (discovered != null && !discovered.getPeerId().equalsIgnoreCase(localPeerId)) {
                    discovered.addTransport(DiscoveredPeer.TRANSPORT_BLE);
                    registry.registerOrUpdate(discovered);
                }
            });
        }

        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        if (bridge.isAvailable()) {
            bridge.stopScanning();
            bridge.stopAdvertising();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public PeerRegistry getRegistry() {
        return registry;
    }

    public BleNativeBridge getBridge() {
        return bridge;
    }

    /**
     * Default simulation bridge used for cross-platform unit testing and fallback when
     * hardware BLE controller is not accessible via standard JVM.
     */
    public static class DefaultSimulationBridge implements BleNativeBridge {
        private boolean active = false;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void startAdvertising(String peerId, String displayName, String platform) {
            active = true;
        }

        @Override
        public void stopAdvertising() {
            active = false;
        }

        @Override
        public void startScanning(java.util.function.Consumer<DiscoveredPeer> callback) {
            active = true;
        }

        @Override
        public void stopScanning() {
            active = false;
        }

        @Override
        public boolean sendGattChunk(String targetPeerId, byte[] chunkData) {
            return active;
        }
    }
}
