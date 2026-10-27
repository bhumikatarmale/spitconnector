package campusmesh.discovery;

import campusmesh.model.Peer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates dynamic peer metadata discovered across network transports.
 * Represents a single logical node regardless of whether it was observed
 * via Wi-Fi mDNS, local subnet broadcast, or Bluetooth Low Energy.
 */
public class DiscoveredPeer {
    public static final String TRANSPORT_WIFI = "WIFI";
    public static final String TRANSPORT_BLE = "BLE";

    private final String peerId;
    private volatile String displayName;
    private volatile String platform;
    private volatile String ipAddress;
    private volatile int port;
    private final Set<String> supportedTransports;
    private volatile long lastSeenTimestamp;
    private final Map<String, String> attributes;

    public DiscoveredPeer(String peerId, String displayName, String platform, String ipAddress, int port) {
        if (peerId == null || peerId.trim().isEmpty()) {
            throw new IllegalArgumentException("peerId cannot be null or empty");
        }
        this.peerId = peerId.trim();
        this.displayName = (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : this.peerId;
        this.platform = (platform != null && !platform.trim().isEmpty()) ? platform.trim().toUpperCase() : "UNKNOWN";
        this.ipAddress = (ipAddress != null && !ipAddress.trim().isEmpty()) ? ipAddress.trim() : "127.0.0.1";
        this.port = port;
        this.supportedTransports = ConcurrentHashMap.newKeySet();
        this.supportedTransports.add("WIFI");
        this.lastSeenTimestamp = System.currentTimeMillis();
        this.attributes = new ConcurrentHashMap<>();
    }

    /**
     * Converts this DiscoveredPeer into a standard Peer instance for backward compatibility
     * with MessageBuffer, ContactManager, RoutingEngine, and TCPManager.
     */
    public Peer toPeer() {
        return new Peer(peerId, ipAddress, port);
    }

    /**
     * Merges incoming observation metadata into this logical peer.
     */
    public synchronized void updateFrom(DiscoveredPeer other) {
        if (other == null || !this.peerId.equalsIgnoreCase(other.peerId)) {
            return;
        }
        this.lastSeenTimestamp = System.currentTimeMillis();
        if (other.displayName != null && !other.displayName.isEmpty()) {
            this.displayName = other.displayName;
        }
        if (other.platform != null && !"UNKNOWN".equalsIgnoreCase(other.platform)) {
            this.platform = other.platform;
        }
        if (other.ipAddress != null && !other.ipAddress.isEmpty()) {
            boolean otherIsLoopback = "127.0.0.1".equals(other.ipAddress) || "localhost".equalsIgnoreCase(other.ipAddress);
            boolean thisIsLoopback = this.ipAddress == null || "127.0.0.1".equals(this.ipAddress) || "localhost".equalsIgnoreCase(this.ipAddress);
            if (!otherIsLoopback || thisIsLoopback) {
                this.ipAddress = other.ipAddress;
            }
        }
        if (other.port > 0) {
            this.port = other.port;
        }
        this.supportedTransports.addAll(other.supportedTransports);
        this.attributes.putAll(other.attributes);
    }

    public synchronized void addTransport(String transport) {
        if (transport != null && !transport.trim().isEmpty()) {
            this.supportedTransports.add(transport.trim().toUpperCase());
        }
    }

    public synchronized void addAttribute(String key, String value) {
        if (key != null && value != null) {
            this.attributes.put(key, value);
        }
    }

    public boolean isExpired(long maxAgeMillis) {
        return (System.currentTimeMillis() - lastSeenTimestamp) > maxAgeMillis;
    }

    // Getters

    public String getPeerId() {
        return peerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPlatform() {
        return platform;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public Set<String> getSupportedTransports() {
        return Collections.unmodifiableSet(new HashSet<>(supportedTransports));
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void touch() {
        this.lastSeenTimestamp = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoveredPeer that = (DiscoveredPeer) o;
        return Objects.equals(peerId.toLowerCase(), that.peerId.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId.toLowerCase());
    }

    @Override
    public String toString() {
        return String.format("DiscoveredPeer[%s ('%s', %s) @ %s:%d | Transports: %s | Age: %d ms]",
                peerId, displayName, platform, ipAddress, port, supportedTransports,
                System.currentTimeMillis() - lastSeenTimestamp);
    }
}
