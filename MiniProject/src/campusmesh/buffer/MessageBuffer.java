package campusmesh.buffer;

import campusmesh.model.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MessageBuffer represents the local storage of a peer in the DTN network.
 * It manages the Store-Carry-Forward queue, enforces storage capacity,
 * detects and prevents duplicates, and purges expired messages.
 */
public class MessageBuffer {
    /** Default capacity: 10 MB (10 * 1024 * 1024 bytes) */
    public static final long DEFAULT_CAPACITY_BYTES = 10L * 1024L * 1024L;

    private final String peerId;
    private final long capacityBytes;
    private final Map<String, Message> messages;
    private long usedBytes;

    public enum AddResult {
        SUCCESS,
        DUPLICATE,
        BUFFER_FULL,
        EXPIRED
    }

    /**
     * Constructs a MessageBuffer with the default 10 MB capacity.
     */
    public MessageBuffer(String peerId) {
        this(peerId, DEFAULT_CAPACITY_BYTES);
    }

    /**
     * Constructs a MessageBuffer with a custom capacity in bytes.
     */
    public MessageBuffer(String peerId, long capacityBytes) {
        this.peerId = (peerId != null) ? peerId : "UNKNOWN";
        this.capacityBytes = capacityBytes > 0 ? capacityBytes : DEFAULT_CAPACITY_BYTES;
        this.messages = new LinkedHashMap<>();
        this.usedBytes = 0L;
    }

    /**
     * Attempts to store a message in the local buffer.
     * Prevents duplicate message IDs and enforces buffer limits.
     *
     * @param message Message to store
     * @return AddResult indicating success, duplicate, buffer full, or expired
     */
    public synchronized AddResult addMessageWithStatus(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }

        // Check for duplicate message
        if (containsMessage(message.getMessageId())) {
            System.out.printf("[BUFFER WARNING] DUPLICATE MESSAGE: Message '%s' already exists in buffer of Peer %s%n",
                    message.getMessageId(), peerId);
            return AddResult.DUPLICATE;
        }

        // Check if message is already expired
        if (message.isExpired()) {
            System.out.printf("[BUFFER WARNING] EXPIRED MESSAGE: Cannot add already expired message '%s'%n",
                    message.getMessageId());
            return AddResult.EXPIRED;
        }

        long messageSize = message.getSizeBytes();
        if (usedBytes + messageSize > capacityBytes) {
            System.out.printf("[BUFFER WARNING] BUFFER OVERFLOW: Message '%s' (%d bytes) exceeds capacity (%d/%d bytes)%n",
                    message.getMessageId(), messageSize, usedBytes, capacityBytes);
            return AddResult.BUFFER_FULL;
        }

        messages.put(message.getMessageId(), message);
        usedBytes += messageSize;
        return AddResult.SUCCESS;
    }

    /**
     * Standard addMessage method returning true if successfully stored, false otherwise.
     */
    public synchronized boolean addMessage(Message message) {
        return addMessageWithStatus(message) == AddResult.SUCCESS;
    }

    /**
     * Removes and returns a message by its ID.
     */
    public synchronized Message removeMessage(String messageId) {
        if (messageId == null) return null;
        Message removed = messages.remove(messageId);
        if (removed != null) {
            usedBytes = Math.max(0L, usedBytes - removed.getSizeBytes());
        }
        return removed;
    }

    /**
     * Checks if a message ID is already present in this buffer.
     */
    public synchronized boolean containsMessage(String messageId) {
        return messageId != null && messages.containsKey(messageId);
    }

    /**
     * Retrieves a message by its ID without removing it.
     */
    public synchronized Message getMessage(String messageId) {
        return messages.get(messageId);
    }

    /**
     * Returns a snapshot list of all messages currently stored in the buffer.
     */
    public synchronized List<Message> getAllMessages() {
        return new ArrayList<>(messages.values());
    }

    /**
     * Returns the count of messages stored.
     */
    public synchronized int size() {
        return messages.size();
    }

    /**
     * Returns total capacity in bytes.
     */
    public long getCapacityBytes() {
        return capacityBytes;
    }

    /**
     * Returns total used buffer bytes.
     */
    public synchronized long getUsedBufferBytes() {
        return usedBytes;
    }

    /**
     * Calculates the normalized buffer pressure B in the range [0.0, 1.0].
     * Used by Adaptive Forwarding.
     */
    public synchronized double getBufferPressure() {
        if (capacityBytes <= 0) return 1.0;
        double pressure = (double) usedBytes / (double) capacityBytes;
        return Math.max(0.0, Math.min(1.0, pressure));
    }

    /**
     * Scans the buffer and removes all messages whose TTL has expired.
     *
     * @return List of removed expired messages
     */
    public synchronized List<Message> removeExpiredMessages() {
        List<Message> expired = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (Message m : messages.values()) {
            if (m.isExpired()) {
                toRemove.add(m.getMessageId());
                expired.add(m);
            }
        }

        for (String id : toRemove) {
            removeMessage(id);
        }

        return expired;
    }

    /**
     * Clears all messages from the buffer.
     */
    public synchronized void clear() {
        messages.clear();
        usedBytes = 0L;
    }

    public String getPeerId() {
        return peerId;
    }

    @Override
    public synchronized String toString() {
        return String.format("MessageBuffer[Peer=%s, Count=%d, Used=%d/%d bytes (%.2f%%)]",
                peerId, size(), usedBytes, capacityBytes, getBufferPressure() * 100.0);
    }
}
