package campusmesh.ble;

import campusmesh.model.Message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Splits CampusMesh DTN messages into MTU-compliant BLE binary chunks.
 */
public class BleChunker {

    private static final AtomicInteger TRANSFER_COUNTER = new AtomicInteger(1);

    /**
     * Splits a Message instance into BLE packets sized according to the negotiated ATT MTU.
     *
     * @param message       The DTN message to send.
     * @param maxPacketSize Maximum GATT write size (typically MTU - 3). Minimum 14 bytes.
     * @return Ordered list of BlePackets.
     */
    public static List<BlePacket> chunkMessage(Message message, int maxPacketSize) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        String json = message.toJson();
        byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);
        int transferId = TRANSFER_COUNTER.getAndIncrement() & 0xFFFF;
        if (transferId == 0) transferId = TRANSFER_COUNTER.getAndIncrement() & 0xFFFF;

        return chunkData(rawBytes, transferId, maxPacketSize);
    }

    /**
     * Splits raw byte data into BLE packets.
     */
    public static List<BlePacket> chunkData(byte[] data, int transferId, int maxPacketSize) {
        if (data == null) {
            data = new byte[0];
        }

        int maxChunkPayload = maxPacketSize - BlePacket.HEADER_SIZE;
        if (maxChunkPayload < 1) {
            throw new IllegalArgumentException("maxPacketSize must be at least " + (BlePacket.HEADER_SIZE + 1) + " bytes");
        }

        long totalCrc = BlePacket.calculateCrc32(data);
        int totalChunks = (int) Math.ceil((double) data.length / maxChunkPayload);
        if (totalChunks == 0) totalChunks = 1; // Empty payload still requires 1 packet

        List<BlePacket> packets = new ArrayList<>(totalChunks);
        int offset = 0;

        for (int i = 0; i < totalChunks; i++) {
            int chunkSize = Math.min(maxChunkPayload, data.length - offset);
            byte[] chunkPayload = new byte[chunkSize];
            if (chunkSize > 0) {
                System.arraycopy(data, offset, chunkPayload, 0, chunkSize);
                offset += chunkSize;
            }

            packets.add(new BlePacket(
                    BleConstants.TYPE_CHUNK,
                    transferId,
                    i,
                    totalChunks,
                    totalCrc,
                    chunkPayload
            ));
        }

        return packets;
    }

    /**
     * Generates an ACK packet for a completed transfer.
     */
    public static BlePacket createAckPacket(int transferId, long crc32, byte statusCode) {
        return new BlePacket(
                BleConstants.TYPE_ACK,
                transferId,
                0,
                1,
                crc32,
                new byte[]{statusCode}
        );
    }

    /**
     * Generates an ABORT packet for a cancelled/timed-out transfer.
     */
    public static BlePacket createAbortPacket(int transferId, String reason) {
        byte[] payload = (reason != null) ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return new BlePacket(
                BleConstants.TYPE_ABORT,
                transferId,
                0,
                1,
                0,
                payload
        );
    }
}
