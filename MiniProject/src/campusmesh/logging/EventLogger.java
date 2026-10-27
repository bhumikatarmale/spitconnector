package campusmesh.logging;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * EventLogger manages persistent lifecycle tracking into an SQLite database.
 * Uses a controlled, thread-safe connection to avoid SQLite database locks.
 *
 * Database Location: database/campusmesh.db
 */
public class EventLogger implements AutoCloseable {

    public static final String DEFAULT_DB_PATH = "database/campusmesh.db";

    public enum EventType {
        PEER_STARTED,
        PEER_STOPPED,
        CONTACT_STARTED,
        CONTACT_ENDED,
        MESSAGE_CREATED,
        MESSAGE_STORED,
        MESSAGE_FORWARDED,
        MESSAGE_RECEIVED,
        MESSAGE_DELIVERED,
        MESSAGE_EXPIRED,
        MESSAGE_DROPPED,
        MESSAGE_DUPLICATE,
        FORWARD_FAILED,
        ROUTING_DECISION
    }

    public static class EventRecord {
        public final long id;
        public final String timestamp;
        public final String eventType;
        public final String peerId;
        public final String messageId;
        public final String remotePeerId;
        public final String routing;
        public final String metadataJson;

        public EventRecord(long id, String timestamp, String eventType, String peerId,
                           String messageId, String remotePeerId, String routing, String metadataJson) {
            this.id = id;
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.peerId = peerId;
            this.messageId = messageId;
            this.remotePeerId = remotePeerId;
            this.routing = routing;
            this.metadataJson = metadataJson;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s | %-18s | Peer: %-4s | Msg: %-6s | Remote: %-4s | Routing: %-12s | Meta: %s",
                    id, timestamp, eventType,
                    (peerId != null ? peerId : "-"),
                    (messageId != null ? messageId : "-"),
                    (remotePeerId != null ? remotePeerId : "-"),
                    (routing != null ? routing : "-"),
                    (metadataJson != null ? metadataJson : "{}"));
        }
    }

    private final String dbPath;
    private Connection connection;
    private boolean initialized = false;

    public EventLogger() {
        this(DEFAULT_DB_PATH);
    }

    public EventLogger(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private synchronized void initDatabase() {
        try {
            // Ensure parent directory exists
            File dbFile = new File(dbPath);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Load SQLite driver
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Create events table if not already present
            String sql = "CREATE TABLE IF NOT EXISTS events ("
                    + "    id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "    timestamp TEXT NOT NULL,"
                    + "    event_type TEXT NOT NULL,"
                    + "    peer_id TEXT,"
                    + "    message_id TEXT,"
                    + "    remote_peer_id TEXT,"
                    + "    routing TEXT,"
                    + "    metadata_json TEXT"
                    + ");";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA busy_timeout = 5000;");
                stmt.execute(sql);
            }
            this.initialized = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[EVENT LOGGER] SQLite JDBC driver not found on classpath: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("[EVENT LOGGER] Failed to initialize SQLite database: " + e.getMessage());
        }
    }

    /**
     * Primary thread-safe logging method.
     */
    public synchronized boolean logEvent(
            EventType type,
            String peerId,
            String messageId,
            String remotePeerId,
            String routing,
            String metadataJson) {

        if (!initialized || connection == null) {
            return false;
        }

        String sql = "INSERT INTO events (timestamp, event_type, peer_id, message_id, remote_peer_id, routing, metadata_json) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, Instant.now().toString());
            pstmt.setString(2, type.name());
            pstmt.setString(3, peerId);
            pstmt.setString(4, messageId);
            pstmt.setString(5, remotePeerId);
            pstmt.setString(6, routing);
            pstmt.setString(7, metadataJson != null ? metadataJson : "{}");
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[EVENT LOGGER ERROR] Failed to record event: " + e.getMessage());
            return false;
        }
    }

    // Convenience helpers

    public boolean logPeerStarted(String peerId) {
        return logEvent(EventType.PEER_STARTED, peerId, null, null, null, null);
    }

    public boolean logPeerStopped(String peerId) {
        return logEvent(EventType.PEER_STOPPED, peerId, null, null, null, null);
    }

    public boolean logContactStarted(String localPeerId, String remotePeerId) {
        return logEvent(EventType.CONTACT_STARTED, localPeerId, null, remotePeerId, null, null);
    }

    public boolean logContactEnded(String localPeerId, String remotePeerId) {
        return logEvent(EventType.CONTACT_ENDED, localPeerId, null, remotePeerId, null, null);
    }

    public boolean logMessageCreated(String peerId, String messageId, String destId, String meta) {
        return logEvent(EventType.MESSAGE_CREATED, peerId, messageId, destId, null, meta);
    }

    public boolean logMessageStored(String peerId, String messageId) {
        return logEvent(EventType.MESSAGE_STORED, peerId, messageId, null, null, null);
    }

    public boolean logMessageDuplicate(String peerId, String messageId) {
        return logEvent(EventType.MESSAGE_DUPLICATE, peerId, messageId, null, null, "{\"error\":\"DUPLICATE_MESSAGE\"}");
    }

    public boolean logMessageExpired(String peerId, String messageId) {
        return logEvent(EventType.MESSAGE_EXPIRED, peerId, messageId, null, null, null);
    }

    public boolean logMessageForwarded(String fromPeer, String toPeer, String messageId, String routing, String meta) {
        return logEvent(EventType.MESSAGE_FORWARDED, fromPeer, messageId, toPeer, routing, meta);
    }

    public boolean logMessageReceived(String receiverPeer, String senderPeer, String messageId) {
        return logEvent(EventType.MESSAGE_RECEIVED, receiverPeer, messageId, senderPeer, null, null);
    }

    public boolean logMessageDelivered(String destPeer, String messageId, String sourcePeer) {
        return logEvent(EventType.MESSAGE_DELIVERED, destPeer, messageId, sourcePeer, null, "{\"status\":\"DELIVERED\"}");
    }

    public boolean logRoutingDecision(String peerId, String remotePeerId, String routingName, String metaJson) {
        return logEvent(EventType.ROUTING_DECISION, peerId, null, remotePeerId, routingName, metaJson);
    }

    /**
     * Retrieves the most recent events from SQLite database.
     */
    public synchronized List<EventRecord> getRecentEvents(int limit) {
        List<EventRecord> records = new ArrayList<>();
        if (!initialized || connection == null) return records;

        String sql = "SELECT id, timestamp, event_type, peer_id, message_id, remote_peer_id, routing, metadata_json "
                   + "FROM events ORDER BY id DESC LIMIT ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, Math.max(1, limit));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new EventRecord(
                            rs.getLong("id"),
                            rs.getString("timestamp"),
                            rs.getString("event_type"),
                            rs.getString("peer_id"),
                            rs.getString("message_id"),
                            rs.getString("remote_peer_id"),
                            rs.getString("routing"),
                            rs.getString("metadata_json")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[EVENT LOGGER ERROR] Query error: " + e.getMessage());
        }
        return records;
    }

    /**
     * Returns total count of events in the SQLite database.
     */
    public synchronized int getEventCount() {
        if (!initialized || connection == null) return 0;
        String sql = "SELECT COUNT(*) FROM events";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[EVENT LOGGER ERROR] Count error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Clears all events from the table.
     */
    public synchronized void clearAllEvents() {
        if (!initialized || connection == null) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM events");
        } catch (SQLException e) {
            System.err.println("[EVENT LOGGER ERROR] Delete error: " + e.getMessage());
        }
    }

    /**
     * Pretty-prints recent events to standard output.
     */
    public synchronized void printRecentEvents(int limit) {
        List<EventRecord> records = getRecentEvents(limit);
        System.out.printf("--- SQLite Events Log (%s) - Showing up to %d events ---%n", dbPath, limit);
        if (records.isEmpty()) {
            System.out.println("  (No events recorded)");
        } else {
            for (EventRecord rec : records) {
                System.out.println("  " + rec);
            }
        }
        System.out.println("----------------------------------------------------------------------");
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
        this.initialized = false;
    }

    public String getDbPath() {
        return dbPath;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
