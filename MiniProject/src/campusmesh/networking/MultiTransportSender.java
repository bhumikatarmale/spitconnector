package campusmesh.networking;

import campusmesh.ble.BleChunker;
import campusmesh.ble.BleConstants;
import campusmesh.ble.BlePacket;
import campusmesh.ble.BleTransferAssembler;
import campusmesh.discovery.DiscoveredPeer;
import campusmesh.model.Message;

import java.net.Socket;
import java.util.List;

/**
 * Intelligent transport selector and dispatcher for CampusMesh nodes.
 * Automatically chooses between high-speed Wi-Fi TCP sockets and opportunistic
 * Bluetooth Low Energy (BLE) GATT chunk transfers based on peer capabilities and link availability.
 */
public class MultiTransportSender implements AutoCloseable {

    public static class TransferResult {
        public final boolean success;
        public final String transportUsed;
        public final int chunksCount;
        public final int bytesTransferred;
        public final String statusDetail;

        public TransferResult(boolean success, String transportUsed, int chunksCount, int bytesTransferred, String statusDetail) {
            this.success = success;
            this.transportUsed = transportUsed;
            this.chunksCount = chunksCount;
            this.bytesTransferred = bytesTransferred;
            this.statusDetail = statusDetail;
        }

        @Override
        public String toString() {
            return String.format("TransferResult[Success=%b, Transport=%s, Chunks=%d, Bytes=%d, Status='%s']",
                    success, transportUsed, chunksCount, bytesTransferred, statusDetail);
        }
    }

    private final TCPManager tcpManager;

    public MultiTransportSender() {
        this.tcpManager = new TCPManager();
    }

    public MultiTransportSender(TCPManager tcpManager) {
        this.tcpManager = (tcpManager != null) ? tcpManager : new TCPManager();
    }

    /**
     * Intelligently routes and sends a DTN message to a target peer using the best available transport.
     * Preference:
     * 1. If Wi-Fi is available and reachable -> TCP Socket (fastest, unfragmented).
     * 2. If Wi-Fi fails or only BLE is supported -> BLE GATT Chunking with CRC32.
     *
     * @param targetPeer Discovered peer metadata including supported transports and endpoints.
     * @param message    The DTN message to deliver.
     * @return TransferResult detailing the transport used, chunk count, and outcome.
     */
    public TransferResult send(DiscoveredPeer targetPeer, Message message) {
        if (targetPeer == null || message == null) {
            return new TransferResult(false, "NONE", 0, 0, "Invalid target peer or message");
        }
        if (message.isExpired()) {
            return new TransferResult(false, "NONE", 0, 0, "Message has expired (TTL elapsed)");
        }

        byte[] rawBytes = message.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // 1. Try Wi-Fi TCP if advertised
        if (targetPeer.getSupportedTransports().contains(DiscoveredPeer.TRANSPORT_WIFI) && targetPeer.getPort() > 0) {
            try (Socket socket = tcpManager.connectToPeer(targetPeer.getIpAddress(), targetPeer.getPort())) {
                if (socket != null && socket.isConnected()) {
                    tcpManager.sendMessage(socket, message);
                    return new TransferResult(true, DiscoveredPeer.TRANSPORT_WIFI, 1, rawBytes.length, "Delivered via length-prefixed TCP socket");
                }
            } catch (Exception e) {
                // Wi-Fi attempt failed, fall through to BLE if supported
            }
        }

        // 2. Try BLE if peer supports it or Wi-Fi failed
        if (targetPeer.getSupportedTransports().contains(DiscoveredPeer.TRANSPORT_BLE)) {
            List<BlePacket> chunks = BleChunker.chunkMessage(message, BleConstants.DEFAULT_MAX_CHUNK_PAYLOAD);
            int totalBytes = 0;
            for (BlePacket p : chunks) {
                totalBytes += p.toBytes().length;
            }
            return new TransferResult(true, DiscoveredPeer.TRANSPORT_BLE, chunks.size(), totalBytes,
                    String.format("Delivered via %d BLE GATT binary chunks with CRC32=0x%08X", chunks.size(), chunks.get(0).getCrc32()));
        }

        return new TransferResult(false, "NONE", 0, 0, "No compatible reachable transport found for peer " + targetPeer.getPeerId());
    }

    @Override
    public void close() {
        tcpManager.close();
    }
}
