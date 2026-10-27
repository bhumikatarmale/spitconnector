package campusmesh.ble;

import campusmesh.model.Message;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Thread-safe assembler that collects, validates, and reassembles incoming BLE chunks
 * into complete DTN messages.
 */
public class BleTransferAssembler {

    public interface AssemblyListener {
        void onTransferComplete(int transferId, Message message);
        void onTransferFailed(int transferId, String error);
    }

    private static class InboundTransfer {
        final int transferId;
        final int totalChunks;
        final long expectedCrc;
        final long createdAt;
        final Map<Integer, byte[]> receivedChunks = new ConcurrentHashMap<>();

        InboundTransfer(int transferId, int totalChunks, long expectedCrc) {
            this.transferId = transferId;
            this.totalChunks = totalChunks;
            this.expectedCrc = expectedCrc;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isComplete() {
            return receivedChunks.size() == totalChunks;
        }

        boolean isExpired(long timeoutMs) {
            return (System.currentTimeMillis() - createdAt) > timeoutMs;
        }
    }

    private final Map<Integer, InboundTransfer> activeTransfers = new ConcurrentHashMap<>();
    private final AssemblyListener listener;
    private final long timeoutMs;

    public BleTransferAssembler(AssemblyListener listener) {
        this(listener, BleConstants.TRANSFER_TIMEOUT_MS);
    }

    public BleTransferAssembler(AssemblyListener listener, long timeoutMs) {
        this.listener = listener;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Ingests an incoming raw BLE byte frame.
     *
     * @param rawBytes Raw GATT packet bytes.
     * @return BlePacket response (ACK, NACK, or null if transfer still in progress).
     */
    public BlePacket processIncomingBytes(byte[] rawBytes) {
        BlePacket packet = BlePacket.fromBytes(rawBytes);
        if (packet == null) {
            return null; // Malformed or invalid magic bytes
        }
        return processIncomingPacket(packet);
    }

    /**
     * Ingests a parsed BlePacket.
     */
    public synchronized BlePacket processIncomingPacket(BlePacket packet) {
        purgeExpiredTransfers();

        int txId = packet.getTransferId();

        if (packet.getType() == BleConstants.TYPE_ABORT) {
            activeTransfers.remove(txId);
            if (listener != null) {
                listener.onTransferFailed(txId, "Transfer aborted by remote peer: " + new String(packet.getPayload(), StandardCharsets.UTF_8));
            }
            return null;
        }

        if (packet.getType() == BleConstants.TYPE_ACK) {
            return null; // Handled by outbound sender
        }

        if (packet.getType() != BleConstants.TYPE_CHUNK && packet.getType() != BleConstants.TYPE_START) {
            return null; // Unrecognized type
        }

        InboundTransfer transfer = activeTransfers.computeIfAbsent(txId, id ->
                new InboundTransfer(id, packet.getTotalChunks(), packet.getCrc32()));

        // Store chunk payload
        transfer.receivedChunks.put(packet.getChunkIndex(), packet.getPayload());

        // Check completion
        if (transfer.isComplete()) {
            activeTransfers.remove(txId);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < transfer.totalChunks; i++) {
                byte[] chunk = transfer.receivedChunks.get(i);
                if (chunk != null) {
                    baos.write(chunk, 0, chunk.length);
                }
            }

            byte[] fullBytes = baos.toByteArray();
            long actualCrc = BlePacket.calculateCrc32(fullBytes);

            if (actualCrc != transfer.expectedCrc) {
                if (listener != null) {
                    listener.onTransferFailed(txId, String.format("CRC mismatch: expected 0x%08X, actual 0x%08X", transfer.expectedCrc, actualCrc));
                }
                return BleChunker.createAckPacket(txId, actualCrc, (byte) 0x01); // 0x01 = CRC_MISMATCH
            }

            // CRC Verified - Parse JSON Message
            try {
                String json = new String(fullBytes, StandardCharsets.UTF_8);
                Message msg = Message.fromJson(json);
                if (listener != null) {
                    listener.onTransferComplete(txId, msg);
                }
                return BleChunker.createAckPacket(txId, actualCrc, (byte) 0x00); // 0x00 = SUCCESS
            } catch (Exception e) {
                if (listener != null) {
                    listener.onTransferFailed(txId, "JSON parsing error: " + e.getMessage());
                }
                return BleChunker.createAckPacket(txId, actualCrc, (byte) 0x02); // 0x02 = PARSE_ERROR
            }
        }

        return null; // Transfer in progress, awaiting more chunks
    }

    public void purgeExpiredTransfers() {
        activeTransfers.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(timeoutMs)) {
                if (listener != null) {
                    listener.onTransferFailed(entry.getKey(), "Transfer timed out after " + timeoutMs + "ms");
                }
                return true;
            }
            return false;
        });
    }

    public int getActiveTransferCount() {
        return activeTransfers.size();
    }

    public void clear() {
        activeTransfers.clear();
    }
}
