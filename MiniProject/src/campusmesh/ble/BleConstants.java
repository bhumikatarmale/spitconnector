package campusmesh.ble;

import java.util.UUID;

/**
 * Common Bluetooth Low Energy UUIDs, opcodes, and protocol constraints
 * for CampusMesh cross-platform mesh discovery and messaging.
 */
public final class BleConstants {

    private BleConstants() {}

    // Primary CampusMesh GATT Service
    public static final UUID SERVICE_UUID = UUID.fromString("0000fe60-0000-1000-8000-00805f9b34fb");

    // Characteristic 1: Peer Identity & Capabilities (Read, Notify)
    public static final UUID CHAR_PEER_IDENTITY = UUID.fromString("0000fe61-0000-1000-8000-00805f9b34fb");

    // Characteristic 2: Message Chunk Ingestion (Write Without Response / Write)
    public static final UUID CHAR_MESSAGE_CHUNK = UUID.fromString("0000fe62-0000-1000-8000-00805f9b34fb");

    // Characteristic 3: Transfer Acknowledgment & Flow Control (Notify, Indicate)
    public static final UUID CHAR_TRANSFER_ACK = UUID.fromString("0000fe63-0000-1000-8000-00805f9b34fb");

    // Packet Magic Identifier (0x434D = 'C' 'M')
    public static final byte MAGIC_BYTE_1 = (byte) 0x43; // 'C'
    public static final byte MAGIC_BYTE_2 = (byte) 0x4D; // 'M'

    // Packet Types
    public static final byte TYPE_START = 0x01;  // Transfer metadata & total size
    public static final byte TYPE_CHUNK = 0x02;  // Data chunk
    public static final byte TYPE_ACK   = 0x03;  // Chunk or transfer acknowledgment
    public static final byte TYPE_ABORT = 0x04;  // Transfer cancellation/timeout

    // MTU constraints
    public static final int DEFAULT_ATT_MTU = 23;
    public static final int ATT_HEADER_SIZE = 3;
    public static final int DEFAULT_MAX_CHUNK_PAYLOAD = DEFAULT_ATT_MTU - ATT_HEADER_SIZE; // 20 bytes

    public static final int NEGOTIATED_MAX_ATT_MTU = 512;
    public static final int NEGOTIATED_CHUNK_PAYLOAD = NEGOTIATED_MAX_ATT_MTU - ATT_HEADER_SIZE; // 509 bytes

    // Transfer timeouts
    public static final long TRANSFER_TIMEOUT_MS = 15_000L; // 15 seconds max per transfer
}
