package org.campusmesh.android.transport

import org.campusmesh.android.model.Message
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Android TCP transport implementation maintaining exact 4-byte length-prefixed
 * binary framing compatible with CampusMesh TCPManager.java on laptops.
 */
class AndroidTCPTransport : AutoCloseable {

    companion object {
        const val MAX_FRAME_SIZE = 10 * 1024 * 1024 // 10 MB maximum frame
        const val CONNECT_TIMEOUT_MS = 3000
    }

    fun interface MessageHandler {
        fun onMessageReceived(message: Message, clientSocket: Socket)
    }

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    @Volatile private var running = false
    private val activeClients = ConcurrentHashMap.newKeySet<Socket>()

    @Synchronized
    fun startServer(port: Int, handler: MessageHandler) {
        if (running) {
            throw IllegalStateException("TCP server is already running")
        }

        serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
        running = true

        serverThread = Thread({
            while (running && serverSocket?.isClosed == false) {
                try {
                    val client = serverSocket!!.accept()
                    handleIncomingConnection(client, handler)
                } catch (e: IOException) {
                    if (running) {
                        System.err.println("[ANDROID TCP ERROR] Accept error: ${e.message}")
                    }
                }
            }
        }, "CampusMesh-AndroidTCPServer-$port").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleIncomingConnection(client: Socket, handler: MessageHandler) {
        activeClients.add(client)
        Thread({
            client.use {
                while (running && !client.isClosed) {
                    try {
                        val msg = receiveMessage(client) ?: break
                        handler.onMessageReceived(msg, client)
                    } catch (eof: EOFException) {
                        break
                    } catch (e: IOException) {
                        break
                    }
                }
            }
            activeClients.remove(client)
        }, "CampusMesh-AndroidClientHandler").start()
    }

    @Synchronized
    fun stopServer() {
        running = false
        try {
            serverSocket?.close()
        } catch (ignored: IOException) {}
        serverSocket = null

        for (s in activeClients) {
            try {
                if (!s.isClosed) s.close()
            } catch (ignored: IOException) {}
        }
        activeClients.clear()
    }

    fun connectToPeer(host: String, port: Int): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        return socket
    }

    fun sendMessage(socket: Socket, message: Message) {
        val json = message.toJson()
        val payloadBytes = json.toByteArray(StandardCharsets.UTF_8)
        writeFrame(socket.getOutputStream(), payloadBytes)
    }

    fun receiveMessage(socket: Socket): Message? {
        val payload = readFrame(socket.getInputStream()) ?: return null
        val json = String(payload, StandardCharsets.UTF_8)
        return Message.fromJson(json)
    }

    private fun writeFrame(out: OutputStream, payload: ByteArray) {
        val dos = DataOutputStream(out)
        dos.writeInt(payload.size)
        dos.write(payload)
        dos.flush()
    }

    private fun readFrame(`in`: InputStream): ByteArray? {
        val dis = DataInputStream(`in`)
        val length = try {
            dis.readInt()
        } catch (e: EOFException) {
            return null
        }

        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw IOException("Frame size $length exceeds limits [0, $MAX_FRAME_SIZE]")
        }

        val data = ByteArray(length)
        dis.readFully(data)
        return data
    }

    override fun close() {
        stopServer()
    }
}
