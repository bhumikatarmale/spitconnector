package org.campusmesh.android.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.campusmesh.android.R
import org.campusmesh.android.buffer.AndroidMessageBuffer
import org.campusmesh.android.discovery.AndroidNsdDiscoveryService
import org.campusmesh.android.model.Message
import org.campusmesh.android.model.Peer
import org.campusmesh.android.model.Priority
import org.campusmesh.android.transport.AndroidTCPTransport
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var localPeer: Peer
    private lateinit var tcpTransport: AndroidTCPTransport
    private lateinit var discoveryService: AndroidNsdDiscoveryService
    private lateinit var messageBuffer: AndroidMessageBuffer

    private val discoveredPeers = CopyOnWriteArrayList<Peer>()
    private lateinit var peerListAdapter: ArrayAdapter<String>
    private val peerDisplayStrings = ArrayList<String>()
    private var selectedPeer: Peer? = null

    private lateinit var tvNodeStatus: TextView
    private lateinit var lvDiscoveredPeers: ListView
    private lateinit var etPayload: EditText
    private lateinit var btnSend: Button
    private lateinit var tvLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvNodeStatus = findViewById(R.id.tvNodeStatus)
        lvDiscoveredPeers = findViewById(R.id.lvDiscoveredPeers)
        etPayload = findViewById(R.id.etPayload)
        btnSend = findViewById(R.id.btnSend)
        tvLogs = findViewById(R.id.tvLogs)

        peerListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, peerDisplayStrings)
        lvDiscoveredPeers.adapter = peerListAdapter
        lvDiscoveredPeers.choiceMode = ListView.CHOICE_MODE_SINGLE
        lvDiscoveredPeers.setOnItemClickListener { _, _, position, _ ->
            if (position in discoveredPeers.indices) {
                selectedPeer = discoveredPeers[position]
                appendLog("[SELECT] Target peer: ${selectedPeer?.peerId} @ ${selectedPeer?.ipAddress}:${selectedPeer?.port}")
            }
        }

        initializeCampusMeshNode()

        btnSend.setOnClickListener {
            handleSendMessage()
        }
    }

    private fun initializeCampusMeshNode() {
        val shortId = "AND_" + UUID.randomUUID().toString().substring(0, 4).uppercase()
        val localPort = 5005
        localPeer = Peer(peerId = shortId, port = localPort, displayName = "Android-$shortId", platform = "ANDROID")
        messageBuffer = AndroidMessageBuffer(localPeer.peerId)

        tvNodeStatus.text = "Local Node: ${localPeer.peerId} (Port: ${localPeer.port})"
        appendLog("[INIT] Initialized Node: ${localPeer.peerId} on port $localPort")

        // 1. Start TCP Server (Framed Socket Listener)
        tcpTransport = AndroidTCPTransport()
        try {
            tcpTransport.startServer(localPort) { msg, _ ->
                runOnUiThread {
                    messageBuffer.addMessage(msg)
                    appendLog("[TCP RECEIVED] From ${msg.sourceId}: '${msg.payload}' (Tokens: ${msg.copyTokens}, Pri: ${msg.priority})")
                    Toast.makeText(this, "Received message from ${msg.sourceId}", Toast.LENGTH_SHORT).show()
                }
            }
            appendLog("[TCP] Server listening on port $localPort")
        } catch (e: Exception) {
            appendLog("[TCP ERROR] Failed to start server: ${e.message}")
        }

        // 2. Start NSD & Subnet Discovery
        discoveryService = AndroidNsdDiscoveryService(this, localPeer)
        discoveryService.addListener(object : AndroidNsdDiscoveryService.DiscoveryListener {
            override fun onPeerDiscovered(peer: Peer) {
                runOnUiThread {
                    val existingIdx = discoveredPeers.indexOfFirst { it.peerId.equals(peer.peerId, ignoreCase = true) }
                    if (existingIdx != -1) {
                        discoveredPeers[existingIdx] = peer
                        peerDisplayStrings[existingIdx] = "${peer.peerId} (${peer.platform}) @ ${peer.ipAddress}:${peer.port}"
                    } else {
                        discoveredPeers.add(peer)
                        peerDisplayStrings.add("${peer.peerId} (${peer.platform}) @ ${peer.ipAddress}:${peer.port}")
                        appendLog("[DISCOVERED] ${peer.peerId} [${peer.platform}] at ${peer.ipAddress}:${peer.port}")
                    }
                    peerListAdapter.notifyDataSetChanged()
                }
            }

            override fun onPeerLost(peerId: String) {
                runOnUiThread {
                    val idx = discoveredPeers.indexOfFirst { it.peerId.equals(peerId, ignoreCase = true) }
                    if (idx != -1) {
                        discoveredPeers.removeAt(idx)
                        peerDisplayStrings.removeAt(idx)
                        peerListAdapter.notifyDataSetChanged()
                        appendLog("[LOST] Peer $peerId departed")
                    }
                }
            }
        })
        discoveryService.start()
        appendLog("[DISCOVERY] mDNS and Subnet Multicast discovery active")
    }

    private fun handleSendMessage() {
        val target = selectedPeer
        if (target == null) {
            Toast.makeText(this, "Please select a discovered peer from the list first", Toast.LENGTH_SHORT).show()
            return
        }

        val text = etPayload.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter a message payload", Toast.LENGTH_SHORT).show()
            return
        }

        val msgId = "AND_M_" + System.currentTimeMillis() % 10000
        val msg = Message(
            messageId = msgId,
            sourceId = localPeer.peerId,
            destinationId = target.peerId,
            priority = Priority.HIGH,
            payload = text,
            copyTokens = 2
        )

        appendLog("[SENDING] Transmitting '$msgId' to ${target.peerId} at ${target.ipAddress}:${target.port}...")

        Thread {
            try {
                tcpTransport.connectToPeer(target.ipAddress, target.port).use { socket ->
                    tcpTransport.sendMessage(socket, msg)
                    runOnUiThread {
                        appendLog("[SUCCESS] Frame '$msgId' delivered to ${target.peerId} over TCP!")
                        etPayload.text.clear()
                        Toast.makeText(this, "Message sent successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendLog("[SEND FAILED] Could not connect/send: ${e.message}")
                    Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun appendLog(msg: String) {
        val current = tvLogs.text.toString()
        tvLogs.text = "$current$msg\n"
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryService.close()
        tcpTransport.close()
    }
}
