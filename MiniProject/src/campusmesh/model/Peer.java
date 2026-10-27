package campusmesh.model;

import java.util.Objects;

/**
 * Represents a node/device participating in the CampusMesh P2P network.
 * In a Delay-Tolerant Network (DTN), peers store, carry, and forward messages
 * when opportunistic contacts occur.
 */
public class Peer {
    private final String peerId;
    private final String ipAddress;
    private final int port;

    /**
     * Constructs a Peer instance.
     *
     * @param peerId    Unique identifier for the peer (e.g. "A", "B")
     * @param ipAddress IP address or hostname of the peer (e.g. "127.0.0.1")
     * @param port      TCP port on which the peer listens (e.g. 5001)
     */
    public Peer(String peerId, String ipAddress, int port) {
        if (peerId == null || peerId.trim().isEmpty()) {
            throw new IllegalArgumentException("peerId cannot be null or empty");
        }
        this.peerId = peerId.trim();
        this.ipAddress = (ipAddress != null && !ipAddress.trim().isEmpty()) ? ipAddress.trim() : "127.0.0.1";
        this.port = port;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    /**
     * Creates a Peer from dynamic discovery metadata.
     */
    public static Peer fromDiscoveredPeer(campusmesh.discovery.DiscoveredPeer dp) {
        if (dp == null) return null;
        return dp.toPeer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Peer peer = (Peer) o;
        return Objects.equals(peerId, peer.peerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId);
    }

    @Override
    public String toString() {
        return String.format("Peer[%s @ %s:%d]", peerId, ipAddress, port);
    }
}
