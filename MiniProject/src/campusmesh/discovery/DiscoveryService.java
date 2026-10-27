package campusmesh.discovery;

import java.io.IOException;

/**
 * Universal interface for pluggable network discovery mechanisms (mDNS, Subnet Beacon, BLE).
 */
public interface DiscoveryService extends AutoCloseable {

    /**
     * Starts advertising the local node and actively discovering remote peers.
     */
    void start() throws IOException;

    /**
     * Stops advertising and discovery, releasing network/radio resources.
     */
    void stop();

    /**
     * Checks if discovery is currently active.
     */
    boolean isRunning();

    /**
     * Returns the peer registry storing discovered devices.
     */
    PeerRegistry getRegistry();

    @Override
    default void close() {
        stop();
    }
}
