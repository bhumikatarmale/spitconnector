package campusmesh.discovery;

import campusmesh.contact.ContactManager;
import campusmesh.discovery.wifi.WifiDiscoveryService;
import campusmesh.model.Peer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Top-level discovery coordinator for a CampusMesh node.
 * Bridges network discovery events to the PeerRegistry and ContactManager.
 */
public class DiscoveryManager implements AutoCloseable {

    private final Peer localPeer;
    private final PeerRegistry registry;
    private final ContactManager contactManager;
    private final List<DiscoveryService> activeServices = new CopyOnWriteArrayList<>();
    private volatile boolean autoSyncContacts = true;

    public DiscoveryManager(Peer localPeer, ContactManager contactManager) {
        if (localPeer == null) {
            throw new IllegalArgumentException("localPeer cannot be null");
        }
        this.localPeer = localPeer;
        this.contactManager = contactManager;
        this.registry = new PeerRegistry();

        // Wire discovery events directly to ContactManager
        this.registry.addListener(new PeerRegistry.PeerRegistryListener() {
            @Override
            public void onPeerDiscovered(DiscoveredPeer peer) {
                if (autoSyncContacts && contactManager != null) {
                    contactManager.startContact(peer.toPeer());
                }
            }

            @Override
            public void onPeerUpdated(DiscoveredPeer peer) {
                if (autoSyncContacts && contactManager != null) {
                    if (!contactManager.isInContact(peer.getPeerId())) {
                        contactManager.startContact(peer.toPeer());
                    }
                }
            }

            @Override
            public void onPeerLost(DiscoveredPeer peer) {
                if (autoSyncContacts && contactManager != null) {
                    contactManager.endContact(peer.toPeer());
                }
            }
        });
    }

    /**
     * Initializes and starts default Wi-Fi discovery (mDNS + subnet beacon).
     */
    public synchronized void startWifiDiscovery() throws IOException {
        WifiDiscoveryService wifiService = new WifiDiscoveryService(localPeer, registry);
        wifiService.start();
        activeServices.add(wifiService);
    }

    /**
     * Initializes and starts BLE discovery.
     */
    public synchronized void startBleDiscovery() throws IOException {
        campusmesh.ble.BleDiscoveryService bleService = new campusmesh.ble.BleDiscoveryService(localPeer.getPeerId(), localPeer.getPeerId(), "WINDOWS", registry);
        addService(bleService);
    }

    public synchronized void addService(DiscoveryService service) throws IOException {
        if (service != null && !activeServices.contains(service)) {
            service.start();
            activeServices.add(service);
        }
    }

    public synchronized void stopAll() {
        for (DiscoveryService service : activeServices) {
            try {
                service.stop();
            } catch (Exception ignored) {}
        }
        activeServices.clear();
        registry.close();
    }

    public PeerRegistry getRegistry() {
        return registry;
    }

    public Peer getLocalPeer() {
        return localPeer;
    }

    public boolean isAutoSyncContacts() {
        return autoSyncContacts;
    }

    public void setAutoSyncContacts(boolean autoSyncContacts) {
        this.autoSyncContacts = autoSyncContacts;
    }

    @Override
    public void close() {
        stopAll();
    }
}
