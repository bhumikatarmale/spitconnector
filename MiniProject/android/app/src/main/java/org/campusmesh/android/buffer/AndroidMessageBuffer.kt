package org.campusmesh.android.buffer

import org.campusmesh.android.model.Message
import java.util.Collections

/**
 * Thread-safe message buffer for CampusMesh Android.
 * Enforces a 10 MB maximum capacity, rejects duplicate message IDs,
 * and purges expired messages based on timestamp TTL.
 */
class AndroidMessageBuffer(
    val peerId: String,
    val capacityBytes: Long = DEFAULT_CAPACITY_BYTES
) {
    companion object {
        const val DEFAULT_CAPACITY_BYTES = 10L * 1024L * 1024L // 10 MB
    }

    enum class AddResult {
        SUCCESS,
        DUPLICATE,
        BUFFER_FULL,
        EXPIRED
    }

    private val messages = LinkedHashMap<String, Message>()
    private var usedBytes = 0L

    @Synchronized
    fun addMessageWithStatus(message: Message?): AddResult {
        if (message == null) throw IllegalArgumentException("message cannot be null")

        if (messages.containsKey(message.messageId)) {
            return AddResult.DUPLICATE
        }

        if (message.isExpired()) {
            return AddResult.EXPIRED
        }

        val size = message.getSizeBytes()
        if (usedBytes + size > capacityBytes) {
            return AddResult.BUFFER_FULL
        }

        messages[message.messageId] = message
        usedBytes += size
        return AddResult.SUCCESS
    }

    @Synchronized
    fun addMessage(message: Message): Boolean {
        return addMessageWithStatus(message) == AddResult.SUCCESS
    }

    @Synchronized
    fun removeMessage(messageId: String): Message? {
        val removed = messages.remove(messageId)
        if (removed != null) {
            usedBytes = maxOf(0L, usedBytes - removed.getSizeBytes())
        }
        return removed
    }

    @Synchronized
    fun containsMessage(messageId: String): Boolean = messages.containsKey(messageId)

    @Synchronized
    fun getMessage(messageId: String): Message? = messages[messageId]

    @Synchronized
    fun getAllMessages(): List<Message> = ArrayList(messages.values)

    @Synchronized
    fun size(): Int = messages.size

    @Synchronized
    fun getUsedBytes(): Long = usedBytes

    @Synchronized
    fun getBufferPressure(): Double {
        if (capacityBytes <= 0) return 1.0
        return (usedBytes.toDouble() / capacityBytes.toDouble()).coerceIn(0.0, 1.0)
    }

    @Synchronized
    fun removeExpiredMessages(): List<Message> {
        val expired = mutableListOf<Message>()
        val it = messages.values.iterator()
        while (it.hasNext()) {
            val msg = it.next()
            if (msg.isExpired()) {
                expired.add(msg)
                usedBytes = maxOf(0L, usedBytes - msg.getSizeBytes())
                it.remove()
            }
        }
        return expired
    }
}
