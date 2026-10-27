package campusmesh.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe registry that maintains all active discovered peers.
 * Handles deduplication across transports, updates last-seen timestamps,
 * and automatically evicts stale peers whose beacons have ceased.
 */
public class PeerRegistry implements AutoCloseable {

    public interface PeerRegistryListener {
        void onPeerDiscovered(DiscoveredPeer peer);
        void onPeerUpdated(DiscoveredPeer peer);
        void onPeerLost(DiscoveredPeer peer);
    }

    public static final long DEFAULT_EXPIRY_TIMEOUT_MS = 12000L; // 12 seconds
    public static final long DEFAULT_REAPER_INTERVAL_MS = 2000L;  // 2 seconds

    private final Map<String, DiscoveredPeer> peers = new ConcurrentHashMap<>();
    private final List<PeerRegistryListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService reaperExecutor;
    private final long expiryTimeoutMs;

    public PeerRegistry() {
        this(DEFAULT_EXPIRY_TIMEOUT_MS);
    }

    public PeerRegistry(long expiryTimeoutMs) {
        this.expiryTimeoutMs = expiryTimeoutMs > 0 ? expiryTimeoutMs : DEFAULT_EXPIRY_TIMEOUT_MS;
        this.reaperExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CampusMesh-PeerRegistryReaper");
            t.setDaemon(true);
            return t;
        });

        this.reaperExecutor.scheduleAtFixedRate(
                this::purgeStalePeers,
                DEFAULT_REAPER_INTERVAL_MS,
                DEFAULT_REAPER_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public void addListener(PeerRegistryListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(PeerRegistryListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Registers a new peer observation or updates an existing one without creating duplicate entries.
     *
     * @param incoming Observed peer metadata
     * @return true if the peer is newly discovered; false if an existing entry was updated
     */
    public boolean registerOrUpdate(DiscoveredPeer incoming) {
        if (incoming == null) {
            return false;
        }

        String key = incoming.getPeerId().toUpperCase();
        DiscoveredPeer existing = peers.get(key);

        if (existing == null) {
            DiscoveredPeer prev = peers.putIfAbsent(key, incoming);
            if (prev == null) {
                for (PeerRegistryListener listener : listeners) {
                    try {
                        listener.onPeerDiscovered(incoming);
                    } catch (Exception e) {
                        System.err.println("[PEER REGISTRY] Listener error on discover: " + e.getMessage());
                    }
                }
                return true;
            } else {
                prev.updateFrom(incoming);
                for (PeerRegistryListener listener : listeners) {
                    try {
                        listener.onPeerUpdated(prev);
                    } catch (Exception e) {
                        System.err.println("[PEER REGISTRY] Listener error on update: " + e.getMessage());
                    }
                }
                return false;
            }
        } else {
            existing.updateFrom(incoming);
            for (PeerRegistryListener listener : listeners) {
                try {
                    listener.onPeerUpdated(existing);
                } catch (Exception e) {
                    System.err.println("[PEER REGISTRY] Listener error on update: " + e.getMessage());
                }
            }
            return false;
        }
    }

    /**
     * Explicitly removes a peer (e.g. on clean peer shutdown announcement).
     */
    public DiscoveredPeer removePeer(String peerId) {
        if (peerId == null) return null;
        DiscoveredPeer removed = peers.remove(peerId.toUpperCase());
        if (removed != null) {
            for (PeerRegistryListener listener : listeners) {
                try {
                    listener.onPeerLost(removed);
                } catch (Exception e) {
                    System.err.println("[PEER REGISTRY] Listener error on peer lost: " + e.getMessage());
                }
            }
        }
        return removed;
    }

    /**
     * Evaluates all stored peers and evicts any exceeding the expiry timeout.
     */
    public synchronized List<DiscoveredPeer> purgeStalePeers() {
        List<DiscoveredPeer> expiredList = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, DiscoveredPeer> entry : peers.entrySet()) {
            DiscoveredPeer peer = entry.getValue();
            if ((now - peer.getLastSeenTimestamp()) > expiryTimeoutMs) {
                expiredList.add(peer);
            }
        }

        for (DiscoveredPeer expired : expiredList) {
            peers.remove(expired.getPeerId().toUpperCase());
            for (PeerRegistryListener listener : listeners) {
                try {
                    listener.onPeerLost(expired);
                } catch (Exception e) {
                    System.err.println("[PEER REGISTRY] Listener error on stale peer eviction: " + e.getMessage());
                }
            }
        }

        return expiredList;
    }

    public DiscoveredPeer getPeer(String peerId) {
        if (peerId == null) return null;
        return peers.get(peerId.toUpperCase());
    }

    public List<DiscoveredPeer> getAllPeers() {
        return Collections.unmodifiableList(new ArrayList<>(peers.values()));
    }

    public int size() {
        return peers.size();
    }

    public void clear() {
        peers.clear();
    }

    @Override
    public void close() {
        reaperExecutor.shutdownNow();
    }
}
