package campusmesh.ble;

import campusmesh.discovery.DiscoveredPeer;

import java.util.function.Consumer;

/**
 * Platform-independent abstraction for OS-native BLE GATT server, advertising, and scanning.
 * Implementations bind to Windows WinRT, Linux BlueZ, or test simulation drivers.
 */
public interface BleNativeBridge {

    /**
     * Checks if the host hardware and OS support BLE scanning and advertising.
     */
    boolean isAvailable();

    /**
     * Starts BLE advertising for CampusMesh service UUID 0000FE60.
     */
    void startAdvertising(String peerId, String displayName, String platform);

    /**
     * Stops BLE advertising.
     */
    void stopAdvertising();

    /**
     * Starts BLE scanning for nearby nodes advertising CampusMesh service.
     */
    void startScanning(Consumer<DiscoveredPeer> peerDiscoveredCallback);

    /**
     * Stops BLE scanning.
     */
    void stopScanning();

    /**
     * Transmits raw chunk bytes over GATT characteristic 0000FE62 to a target peer.
     */
    boolean sendGattChunk(String targetPeerId, byte[] chunkData);
}
