package campusmesh.ble;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Compact binary frame envelope for BLE GATT characteristic transfers.
 *
 * Wire Structure (13 bytes header + variable payload):
 * +--------+--------+------+------------+------------+-------------+---------+---------------+
 * | Magic1 | Magic2 | Type | TransferId | ChunkIndex | TotalChunks |  CRC32  |    Payload    |
 * | (0x43) | (0x4D) | (1B) |   (2B BE)  |   (2B BE)  |   (2B BE)   | (4B BE) |  (0..N bytes) |
 * +--------+--------+------+------------+------------+-------------+---------+---------------+
 */
public class BlePacket {

    public static final int HEADER_SIZE = 13;

    private final byte type;
    private final int transferId;   // 16-bit unsigned (0..65535)
    private final int chunkIndex;   // 16-bit unsigned (0-indexed)
    private final int totalChunks;  // 16-bit unsigned (>= 1)
    private final long crc32;       // 32-bit unsigned CRC of the whole payload
    private final byte[] payload;

    public BlePacket(byte type, int transferId, int chunkIndex, int totalChunks, long crc32, byte[] payload) {
        this.type = type;
        this.transferId = transferId & 0xFFFF;
        this.chunkIndex = chunkIndex & 0xFFFF;
        this.totalChunks = totalChunks & 0xFFFF;
        this.crc32 = crc32 & 0xFFFFFFFFL;
        this.payload = (payload != null) ? payload : new byte[0];
    }

    public byte getType() {
        return type;
    }

    public int getTransferId() {
        return transferId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public long getCrc32() {
        return crc32;
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * Serializes this packet into a binary byte array.
     */
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(BleConstants.MAGIC_BYTE_1);
        buf.put(BleConstants.MAGIC_BYTE_2);
        buf.put(type);
        buf.putShort((short) transferId);
        buf.putShort((short) chunkIndex);
        buf.putShort((short) totalChunks);
        buf.putInt((int) crc32);
        buf.put(payload);
        return buf.array();
    }

    /**
     * Deserializes a BlePacket from raw bytes. Returns null if invalid or corrupt.
     */
    public static BlePacket fromBytes(byte[] raw) {
        if (raw == null || raw.length < HEADER_SIZE) {
            return null;
        }

        ByteBuffer buf = ByteBuffer.wrap(raw);
        buf.order(ByteOrder.BIG_ENDIAN);

        byte m1 = buf.get();
        byte m2 = buf.get();
        if (m1 != BleConstants.MAGIC_BYTE_1 || m2 != BleConstants.MAGIC_BYTE_2) {
            return null; // Invalid magic bytes
        }

        byte type = buf.get();
        int transferId = buf.getShort() & 0xFFFF;
        int chunkIndex = buf.getShort() & 0xFFFF;
        int totalChunks = buf.getShort() & 0xFFFF;
        long crc32 = buf.getInt() & 0xFFFFFFFFL;

        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);

        return new BlePacket(type, transferId, chunkIndex, totalChunks, crc32, payload);
    }

    /**
     * Helper to compute CRC32 checksum over an entire unfragmented byte payload.
     */
    public static long calculateCrc32(byte[] data) {
        if (data == null || data.length == 0) return 0;
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlePacket blePacket = (BlePacket) o;
        return type == blePacket.type &&
                transferId == blePacket.transferId &&
                chunkIndex == blePacket.chunkIndex &&
                totalChunks == blePacket.totalChunks &&
                crc32 == blePacket.crc32 &&
                Arrays.equals(payload, blePacket.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, transferId, chunkIndex, totalChunks, crc32);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return String.format("BlePacket[Type=0x%02X, Tx=%d, Chunk=%d/%d, CRC=0x%08X, PayloadLen=%d]",
                type, transferId, chunkIndex, totalChunks, crc32, payload.length);
    }
}
