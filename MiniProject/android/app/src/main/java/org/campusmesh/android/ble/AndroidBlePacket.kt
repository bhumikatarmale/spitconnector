package org.campusmesh.android.ble

import org.campusmesh.android.model.Message
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32

data class AndroidBlePacket(
    val type: Byte,
    val transferId: Int,
    val chunkIndex: Int,
    val totalChunks: Int,
    val crc32: Long,
    val payload: ByteArray
) {
    companion object {
        const val HEADER_SIZE = 13

        fun calculateCrc32(data: ByteArray): Long {
            val crc = CRC32()
            crc.update(data)
            return crc.value
        }

        fun fromBytes(raw: ByteArray?): AndroidBlePacket? {
            if (raw == null || raw.size < HEADER_SIZE) return null
            val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
            val m1 = buf.get()
            val m2 = buf.get()
            if (m1 != AndroidBleConstants.MAGIC_BYTE_1 || m2 != AndroidBleConstants.MAGIC_BYTE_2) return null

            val type = buf.get()
            val transferId = buf.short.toInt() and 0xFFFF
            val chunkIndex = buf.short.toInt() and 0xFFFF
            val totalChunks = buf.short.toInt() and 0xFFFF
            val crc32 = buf.int.toLong() and 0xFFFFFFFFL

            val payload = ByteArray(buf.remaining())
            buf.get(payload)

            return AndroidBlePacket(type, transferId, chunkIndex, totalChunks, crc32, payload)
        }
    }

    fun toBytes(): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(AndroidBleConstants.MAGIC_BYTE_1)
        buf.put(AndroidBleConstants.MAGIC_BYTE_2)
        buf.put(type)
        buf.putShort(transferId.toShort())
        buf.putShort(chunkIndex.toShort())
        buf.putShort(totalChunks.toShort())
        buf.putInt(crc32.toInt())
        buf.put(payload)
        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AndroidBlePacket
        return type == other.type &&
                transferId == other.transferId &&
                chunkIndex == other.chunkIndex &&
                totalChunks == other.totalChunks &&
                crc32 == other.crc32 &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.toInt()
        result = 31 * result + transferId
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + crc32.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object AndroidBleChunker {
    private val counter = AtomicInteger(1)

    fun chunkMessage(message: Message, maxPacketSize: Int): List<AndroidBlePacket> {
        val json = message.toJson()
        val data = json.toByteArray(StandardCharsets.UTF_8)
        val transferId = counter.getAndIncrement() and 0xFFFF
        val maxPayload = (maxPacketSize - AndroidBlePacket.HEADER_SIZE).coerceAtLeast(1)

        val totalCrc = AndroidBlePacket.calculateCrc32(data)
        val totalChunks = Math.ceil(data.size.toDouble() / maxPayload).toInt().coerceAtLeast(1)
        val list = mutableListOf<AndroidBlePacket>()

        var offset = 0
        for (i in 0 until totalChunks) {
            val size = Math.min(maxPayload, data.size - offset)
            val chunk = ByteArray(size)
            if (size > 0) {
                System.arraycopy(data, offset, chunk, 0, size)
                offset += size
            }
            list.add(AndroidBlePacket(AndroidBleConstants.TYPE_CHUNK, transferId, i, totalChunks, totalCrc, chunk))
        }
        return list
    }
}

class AndroidBleAssembler(
    private val onMessageComplete: (Message) -> Unit,
    private val onFailed: (Int, String) -> Unit
) {
    private class Session(val transferId: Int, val totalChunks: Int, val expectedCrc: Long) {
        val chunks = ConcurrentHashMap<Int, ByteArray>()
        val createdAt = System.currentTimeMillis()
        fun isComplete(): Boolean = chunks.size == totalChunks
    }

    private val sessions = ConcurrentHashMap<Int, Session>()

    fun processBytes(raw: ByteArray): AndroidBlePacket? {
        val packet = AndroidBlePacket.fromBytes(raw) ?: return null
        if (packet.type != AndroidBleConstants.TYPE_CHUNK) return null

        val session = sessions.computeIfAbsent(packet.transferId) {
            Session(packet.transferId, packet.totalChunks, packet.crc32)
        }
        session.chunks[packet.chunkIndex] = packet.payload

        if (session.isComplete()) {
            sessions.remove(packet.transferId)
            val baos = ByteArrayOutputStream()
            for (i in 0 until session.totalChunks) {
                session.chunks[i]?.let { baos.write(it) }
            }
            val completeBytes = baos.toByteArray()
            val actualCrc = AndroidBlePacket.calculateCrc32(completeBytes)

            if (actualCrc != session.expectedCrc) {
                onFailed(session.transferId, "CRC mismatch")
                return AndroidBlePacket(AndroidBleConstants.TYPE_ACK, session.transferId, 0, 1, actualCrc, byteArrayOf(0x01))
            }

            try {
                val json = String(completeBytes, StandardCharsets.UTF_8)
                val msg = Message.fromJson(json)
                onMessageComplete(msg)
                return AndroidBlePacket(AndroidBleConstants.TYPE_ACK, session.transferId, 0, 1, actualCrc, byteArrayOf(0x00))
            } catch (e: Exception) {
                onFailed(session.transferId, "JSON parsing error: ${e.message}")
                return AndroidBlePacket(AndroidBleConstants.TYPE_ACK, session.transferId, 0, 1, actualCrc, byteArrayOf(0x02))
            }
        }
        return null
    }
}
