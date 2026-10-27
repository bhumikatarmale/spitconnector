package campusmesh.networking;

import campusmesh.model.Message;
import campusmesh.model.Peer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCPManager provides hardened TCP socket transport, application-level framing, and connection lifecycle management.
 *
 * Framing Protocol:
 *   [Optional 6-byte Handshake Header: 'CMTP' (4B) + Version (2B short)]
 *   [4-byte Big-Endian Length Prefix] + [UTF-8 JSON Payload Bytes]
 *
 * Bounded Resource Guarantees:
 *   - MAX_FRAME_SIZE capped at 2 MB (2,097,152 bytes) to defend against memory exhaustion.
 *   - Bounded thread pool (max 16 concurrent workers) prevents thread starvation under high connection rates.
 *   - Sockets enforce SO_TIMEOUT (5,000 ms) preventing hung connections on dead links.
 */
public class TCPManager implements AutoCloseable {

    /** Maximum allowed frame payload size: 2 MB. Larger frames are rejected to prevent memory exhaustion. */
    public static final int MAX_FRAME_SIZE = 2 * 1024 * 1024;
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int SOCKET_SO_TIMEOUT_MS = 5000;

    /** 4-byte magic signature for CampusMesh Transport Protocol handshake. */
    public static final byte[] HANDSHAKE_MAGIC = new byte[] { (byte)'C', (byte)'M', (byte)'T', (byte)'P' };
    public static final short CURRENT_HANDSHAKE_VERSION = 1;

    /**
     * Callback interface invoked when a message is received over TCP.
     */
    @FunctionalInterface
    public interface MessageHandler {
        void onMessageReceived(Message message, Socket clientSocket);
    }

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    private final java.util.Set<Socket> activeClients = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Bounded worker pool for incoming client connections (max 16 concurrent workers, queue 64). */
    private final ExecutorService clientWorkerPool = new ThreadPoolExecutor(
            2,
            16,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(64),
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "CampusMesh-TCPWorker-" + count.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    /**
     * Starts a background TCP server listening on the specified port.
     */
    public synchronized void startServer(int port, MessageHandler handler) throws IOException {
        if (running) {
            throw new IllegalStateException("TCP server is already running");
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        running = true;

        serverThread = new Thread(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    handleIncomingConnection(client, handler);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[TCP SERVER ERROR] Connection error: " + e.getMessage());
                    }
                }
            }
        }, "CampusMesh-TCPServer-" + port);

        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleIncomingConnection(Socket client, MessageHandler handler) {
        activeClients.add(client);
        try {
            clientWorkerPool.execute(() -> {
                try (client) {
                    client.setSoTimeout(SOCKET_SO_TIMEOUT_MS);
                    PushbackInputStream pis = new PushbackInputStream(client.getInputStream(), 8);

                    // Check for optional handshake header
                    checkAndConsumeHandshake(pis);

                    while (running && !client.isClosed()) {
                        try {
                            Message msg = receiveMessageFromStream(pis);
                            if (msg == null) {
                                break; // Normal EOF
                            }
                            if (handler != null) {
                                handler.onMessageReceived(msg, client);
                            }
                        } catch (SocketTimeoutException ste) {
                            // Heartbeat/idle timeout; continue listening if running
                            break;
                        } catch (EOFException eof) {
                            break; // Normal connection close
                        }
                    }
                } catch (IOException ignored) {
                    // Connection closed or timed out
                } finally {
                    activeClients.remove(client);
                    try {
                        if (!client.isClosed()) client.close();
                    } catch (IOException ignored) {}
                }
            });
        } catch (Exception rejected) {
            // Pool saturated - close client safely to avoid resource leaks
            activeClients.remove(client);
            try {
                client.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Inspects stream for handshake magic 'CMTP'. If present, reads 2-byte version; otherwise unreads.
     */
    private static void checkAndConsumeHandshake(PushbackInputStream pis) throws IOException {
        byte[] magic = new byte[4];
        int read = pis.read(magic, 0, 4);
        if (read == 4 && magic[0] == HANDSHAKE_MAGIC[0] && magic[1] == HANDSHAKE_MAGIC[1]
                && magic[2] == HANDSHAKE_MAGIC[2] && magic[3] == HANDSHAKE_MAGIC[3]) {
            // Handshake confirmed; read 2-byte short version
            DataInputStream dis = new DataInputStream(pis);
            short version = dis.readShort();
            if (version < 1) {
                throw new IOException("Unsupported handshake protocol version: " + version);
            }
        } else if (read > 0) {
            // Not a handshake frame; unread the bytes so readFrame can parse as length prefix
            pis.unread(magic, 0, read);
        }
    }

    /**
     * Stops the running server and closes open sockets.
     */
    public synchronized void stopServer() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        for (Socket s : activeClients) {
            try {
                if (!s.isClosed()) {
                    s.close();
                }
            } catch (IOException ignored) {}
        }
        activeClients.clear();
        clientWorkerPool.shutdownNow();
    }

    /**
     * Establishes a TCP connection to a remote peer and performs handshake.
     */
    public Socket connectToPeer(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(SOCKET_SO_TIMEOUT_MS);
        return socket;
    }

    /**
     * Convenience method to connect to a Peer instance.
     */
    public Socket connectToPeer(Peer peer) throws IOException {
        return connectToPeer(peer.getIpAddress(), peer.getPort());
    }

    /**
     * Sends the 6-byte handshake frame [ 'C','M','T','P' ] + [ version 1 ].
     */
    public static void sendHandshake(Socket socket) throws IOException {
        if (socket == null || socket.isClosed()) return;
        OutputStream out = socket.getOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(HANDSHAKE_MAGIC);
        dos.writeShort(CURRENT_HANDSHAKE_VERSION);
        dos.flush();
    }

    /**
     * Sends a Message over an existing socket using length-prefixed framing.
     */
    public void sendMessage(Socket socket, Message message) throws IOException {
        if (socket == null || message == null) {
            throw new IllegalArgumentException("Socket and Message cannot be null");
        }
        String json = message.toJson();
        byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
        writeFrame(socket.getOutputStream(), payloadBytes);
    }

    /**
     * Reads a framed Message from an open socket.
     */
    public Message receiveMessage(Socket socket) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null");
        }
        byte[] payload = readFrame(socket.getInputStream());
        if (payload == null) {
            return null;
        }
        String json = new String(payload, StandardCharsets.UTF_8);
        return Message.fromJson(json);
    }

    private Message receiveMessageFromStream(InputStream in) throws IOException {
        byte[] payload = readFrame(in);
        if (payload == null) {
            return null;
        }
        String json = new String(payload, StandardCharsets.UTF_8);
        return Message.fromJson(json);
    }

    /**
     * Writes length-prefixed frame: [4 bytes length] + [payload bytes].
     */
    public static void writeFrame(OutputStream out, byte[] payload) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(payload.length);
        dos.write(payload);
        dos.flush();
    }

    /**
     * Reads length-prefixed frame using readFully to guarantee complete frame retrieval.
     */
    public static byte[] readFrame(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int length;
        try {
            length = dis.readInt();
        } catch (EOFException e) {
            return null;
        }

        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw new IOException("Frame size " + length + " violates bounds [0, " + MAX_FRAME_SIZE + "]");
        }

        byte[] data = new byte[length];
        dis.readFully(data);
        return data;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stopServer();
    }
}
