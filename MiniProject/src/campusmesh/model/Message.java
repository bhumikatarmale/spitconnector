package campusmesh.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a discrete message transmitted across the CampusMesh network.
 * Supports Delay-Tolerant Networking (DTN) semantics:
 * - Store-Carry-Forward
 * - Expiration via timestamps (no tick decrements)
 * - Copy tokens for Spray-and-Wait replication control
 * - Priority levels for Adaptive Forwarding
 * - Explicit protocol versioning and robust JSON serialization via Gson
 */
public class Message {

    public static final int CURRENT_PROTOCOL_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final int version;
    private final String messageId;
    private final String sourceId;
    private final String destinationId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Priority priority;
    private final String payload;
    private int copyTokens;

    /**
     * Primary constructor specifying explicit protocol version and Instant timestamps.
     */
    public Message(int version,
                   String messageId,
                   String sourceId,
                   String destinationId,
                   Instant createdAt,
                   Instant expiresAt,
                   Priority priority,
                   String payload,
                   int copyTokens) {
        if (messageId == null || messageId.trim().isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null or empty");
        }
        if (sourceId == null || destinationId == null) {
            throw new IllegalArgumentException("sourceId and destinationId cannot be null");
        }
        if (createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("timestamps cannot be null");
        }
        if (expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("expiresAt cannot be before createdAt");
        }

        this.version = (version > 0) ? version : CURRENT_PROTOCOL_VERSION;
        this.messageId = messageId.trim();
        this.sourceId = sourceId.trim();
        this.destinationId = destinationId.trim();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.priority = (priority != null) ? priority : Priority.MEDIUM;
        this.payload = (payload != null) ? payload : "";
        this.copyTokens = Math.max(1, copyTokens);
    }

    /**
     * Legacy constructor defaulting protocol version to CURRENT_PROTOCOL_VERSION (1).
     */
    public Message(String messageId,
                   String sourceId,
                   String destinationId,
                   Instant createdAt,
                   Instant expiresAt,
                   Priority priority,
                   String payload,
                   int copyTokens) {
        this(CURRENT_PROTOCOL_VERSION, messageId, sourceId, destinationId, createdAt, expiresAt, priority, payload, copyTokens);
    }

    /**
     * Convenience constructor creating a message with TTL in seconds from current time.
     *
     * @param messageId      Unique message ID (e.g. "M001")
     * @param sourceId       Originating peer ID (e.g. "A")
     * @param destinationId  Final destination peer ID (e.g. "D")
     * @param priority       Priority level (LOW, MEDIUM, HIGH)
     * @param payload        Message payload text
     * @param ttlSeconds     Time-To-Live in seconds from now
     * @param copyTokens     Initial replication copy budget (e.g. 4)
     */
    public Message(String messageId,
                   String sourceId,
                   String destinationId,
                   Priority priority,
                   String payload,
                   long ttlSeconds,
                   int copyTokens) {
        this(CURRENT_PROTOCOL_VERSION,
             messageId,
             sourceId,
             destinationId,
             Instant.now(),
             Instant.now().plusSeconds(Math.max(1, ttlSeconds)),
             priority,
             payload,
             copyTokens);
    }

    /**
     * Returns true if current wall-clock time has exceeded expiresAt.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Calculates remaining TTL in seconds based on current time.
     * Returns 0 if already expired.
     */
    public long getRemainingTTL() {
        Instant now = Instant.now();
        if (now.isAfter(expiresAt)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, expiresAt).getSeconds());
    }

    /**
     * Calculates total original TTL in seconds (expiresAt - createdAt).
     */
    public long getOriginalTTL() {
        long original = Duration.between(createdAt, expiresAt).getSeconds();
        return Math.max(1L, original);
    }

    /**
     * Calculates the estimated memory/buffer size in bytes of this message.
     */
    public long getSizeBytes() {
        long payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        // Overhead estimate for IDs, timestamps, metadata
        long metadataOverhead = 128L;
        return payloadBytes + metadataOverhead;
    }

    // Getters and Mutators

    public int getVersion() {
        return version;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getPayload() {
        return payload;
    }

    public int getCopyTokens() {
        return copyTokens;
    }

    public void setCopyTokens(int copyTokens) {
        this.copyTokens = Math.max(1, copyTokens);
    }

    /**
     * Creates a copy of this message with an assigned number of copy tokens.
     */
    public Message copyWithTokens(int tokens) {
        return new Message(
            this.version,
            this.messageId,
            this.sourceId,
            this.destinationId,
            this.createdAt,
            this.expiresAt,
            this.priority,
            this.payload,
            tokens
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(messageId, message.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }

    @Override
    public String toString() {
        return String.format(
            "Message[v=%d, ID=%s, %s->%s, Pri=%s, Tokens=%d, TTL=%ds, Expired=%b, Payload='%s']",
            version, messageId, sourceId, destinationId, priority, copyTokens, getRemainingTTL(), isExpired(), payload
        );
    }

    /**
     * Serializes this message into a JSON string using Gson.
     */
    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", version);
        obj.addProperty("messageId", messageId);
        obj.addProperty("sourceId", sourceId);
        obj.addProperty("destinationId", destinationId);
        obj.addProperty("createdAt", createdAt.toString());
        obj.addProperty("expiresAt", expiresAt.toString());
        obj.addProperty("priority", priority.name());
        obj.addProperty("copyTokens", copyTokens);
        obj.addProperty("payload", payload);
        return GSON.toJson(obj);
    }

    /**
     * Parses a Message instance from a JSON string using Gson.
     */
    public static Message fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be empty");
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            int ver = (obj.has("version") && !obj.get("version").isJsonNull())
                    ? obj.get("version").getAsInt()
                    : CURRENT_PROTOCOL_VERSION;

            String msgId = (obj.has("messageId") && !obj.get("messageId").isJsonNull())
                    ? obj.get("messageId").getAsString()
                    : null;

            String srcId = (obj.has("sourceId") && !obj.get("sourceId").isJsonNull())
                    ? obj.get("sourceId").getAsString()
                    : null;

            String destId = (obj.has("destinationId") && !obj.get("destinationId").isJsonNull())
                    ? obj.get("destinationId").getAsString()
                    : null;

            String pld = (obj.has("payload") && !obj.get("payload").isJsonNull())
                    ? obj.get("payload").getAsString()
                    : "";

            int tokens = (obj.has("copyTokens") && !obj.get("copyTokens").isJsonNull())
                    ? obj.get("copyTokens").getAsInt()
                    : 1;

            Priority prio = Priority.MEDIUM;
            if (obj.has("priority") && !obj.get("priority").isJsonNull()) {
                prio = Priority.fromString(obj.get("priority").getAsString());
            }

            Instant created = Instant.now();
            if (obj.has("createdAt") && !obj.get("createdAt").isJsonNull()) {
                try {
                    created = Instant.parse(obj.get("createdAt").getAsString());
                } catch (Exception ignored) {}
            }

            Instant expires = created.plusSeconds(3600);
            if (obj.has("expiresAt") && !obj.get("expiresAt").isJsonNull()) {
                try {
                    expires = Instant.parse(obj.get("expiresAt").getAsString());
                } catch (Exception ignored) {}
            }

            return new Message(ver, msgId, srcId, destId, created, expires, prio, pld, tokens);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("Failed to parse Message JSON: " + e.getMessage(), e);
        }
    }
}
