package org.campusmesh.android.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import org.campusmesh.android.model.Peer
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android NSD (Network Service Discovery) & Subnet Multicast Discovery Service.
 * Implements mDNS service advertisement/discovery for _campusmesh._tcp,
 * fully interoperable with JmDNS on Windows and Ubuntu laptops.
 */
class AndroidNsdDiscoveryService(
    private val context: Context,
    private val localPeer: Peer
) : AutoCloseable {

    interface DiscoveryListener {
        fun onPeerDiscovered(peer: Peer)
        fun onPeerLost(peerId: String)
    }

    companion object {
        const val SERVICE_TYPE = "_campusmesh._tcp"
        const val MULTICAST_GROUP = "239.255.77.88"
        const val MULTICAST_PORT = 5354
        const val BEACON_INTERVAL_MS = 2500L
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val listeners = CopyOnWriteArrayList<DiscoveryListener>()
    private val running = AtomicBoolean(false)

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var beaconSocket: MulticastSocket? = null
    private var beaconGroup: InetAddress? = null
    private var beaconScheduler: ScheduledExecutorService? = null
    private var beaconListenerThread: Thread? = null

    fun addListener(listener: DiscoveryListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DiscoveryListener) {
        listeners.remove(listener)
    }

    @Synchronized
    fun start() {
        if (!running.compareAndSet(false, true)) return

        // 1. Acquire Wi-Fi Multicast Lock to receive mDNS & multicast packets on Android
        try {
            multicastLock = wifiManager.createMulticastLock("CampusMeshMulticastLock").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            System.err.println("[ANDROID NSD] Could not acquire MulticastLock: ${e.message}")
        }

        // 2. Start Native Android NSD (mDNS)
        startNsdRegistration()
        startNsdDiscovery()

        // 3. Start Complementary Subnet Multicast Beacon (Fallback)
        startUdpMulticastBeacon()
    }

    private fun startNsdRegistration() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "campusmesh-${localPeer.peerId}"
            serviceType = SERVICE_TYPE
            port = localPeer.port
            setAttribute("peerId", localPeer.peerId)
            setAttribute("platform", "ANDROID")
            setAttribute("ver", "1.0")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                System.out.println("[ANDROID NSD] Service successfully registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                System.err.println("[ANDROID NSD] Service registration failed with code: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            System.err.println("[ANDROID NSD] Error registering service: ${e.message}")
        }
    }

    private fun startNsdDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                System.out.println("[ANDROID NSD] Discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("campusmesh")) {
                    val name = service.serviceName
                    if (name.contains(localPeer.peerId)) return // Ignore own service

                    try {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val host = serviceInfo.host.hostAddress ?: "127.0.0.1"
                                val port = serviceInfo.port
                                var peerId = serviceInfo.attributes["peerId"]?.let { String(it, StandardCharsets.UTF_8) }
                                if (peerId.isNullOrBlank()) {
                                    peerId = serviceInfo.serviceName.removePrefix("campusmesh-")
                                }
                                val plat = serviceInfo.attributes["platform"]?.let { String(it, StandardCharsets.UTF_8) } ?: "JAVA"

                                if (peerId.equals(localPeer.peerId, ignoreCase = true)) return

                                val discovered = Peer(
                                    peerId = peerId,
                                    ipAddress = host,
                                    port = port,
                                    displayName = peerId,
                                    platform = plat
                                )
                                notifyDiscovered(discovered)
                            }
                        })
                    } catch (e: Exception) {
                        System.err.println("[ANDROID NSD] Resolve error: ${e.message}")
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val peerId = service.serviceName.removePrefix("campusmesh-")
                notifyLost(peerId)
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            System.err.println("[ANDROID NSD] Error discovering services: ${e.message}")
        }
    }

    private fun startUdpMulticastBeacon() {
        try {
            beaconGroup = InetAddress.getByName(MULTICAST_GROUP)
            beaconSocket = MulticastSocket(MULTICAST_PORT).apply {
                reuseAddress = true
                try {
                    joinGroup(beaconGroup)
                } catch (ignored: Exception) {}
            }

            beaconListenerThread = Thread({
                val buffer = ByteArray(2048)
                while (running.get() && beaconSocket?.isClosed == false) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        beaconSocket?.receive(packet)
                        val msg = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                        handleIncomingBeacon(msg, packet.address.hostAddress ?: "127.0.0.1")
                    } catch (e: IOException) {
                        if (!running.get()) break
                    }
                }
            }, "CampusMesh-AndroidBeaconListener").apply {
                isDaemon = true
                start()
            }

            beaconScheduler = Executors.newSingleThreadScheduledExecutor().apply {
                scheduleAtFixedRate({
                    broadcastBeacon()
                }, 100, BEACON_INTERVAL_MS, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            System.err.println("[ANDROID BEACON] Error starting subnet beacon: ${e.message}")
        }
    }

    private fun broadcastBeacon() {
        if (!running.get() || beaconSocket == null || beaconSocket?.isClosed == true) return
        try {
            val json = String.format(
                "{\"type\":\"BEACON\",\"peerId\":\"%s\",\"platform\":\"ANDROID\",\"port\":%d,\"ts\":%d}",
                localPeer.peerId, localPeer.port, System.currentTimeMillis()
            )
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, beaconGroup, MULTICAST_PORT)
            beaconSocket?.send(packet)
        } catch (ignored: IOException) {}
    }

    private fun handleIncomingBeacon(json: String, senderIp: String) {
        if (!json.contains("\"type\":\"BEACON\"")) return
        val peerId = extractField(json, "peerId") ?: return
        if (peerId.equals(localPeer.peerId, ignoreCase = true)) return

        val plat = extractField(json, "platform") ?: "JAVA"
        val port = extractInt(json, "port", 5001)

        val peer = Peer(peerId, senderIp, port, peerId, plat)
        notifyDiscovered(peer)
    }

    private fun notifyDiscovered(peer: Peer) {
        listeners.forEach {
            try {
                it.onPeerDiscovered(peer)
            } catch (ignored: Exception) {}
        }
    }

    private fun notifyLost(peerId: String) {
        listeners.forEach {
            try {
                it.onPeerLost(peerId)
            } catch (ignored: Exception) {}
        }
    }

    private fun extractField(json: String, key: String): String? {
        val marker = "\"$key\":\""
        val start = json.indexOf(marker)
        if (start == -1) return null
        val end = json.indexOf("\"", start + marker.length)
        if (end == -1) return null
        return json.substring(start + marker.length, end)
    }

    private fun extractInt(json: String, key: String, defVal: Int): Int {
        val marker = "\"$key\":"
        val start = json.indexOf(marker)
        if (start == -1) return defVal
        var end = json.indexOf(",", start + marker.length)
        if (end == -1) end = json.indexOf("}", start + marker.length)
        if (end == -1) return defVal
        return try {
            json.substring(start + marker.length, end).trim().toInt()
        } catch (e: NumberFormatException) {
            defVal
        }
    }

    @Synchronized
    override fun close() {
        if (!running.compareAndSet(true, false)) return

        beaconScheduler?.shutdownNow()
        beaconScheduler = null

        try {
            beaconSocket?.close()
        } catch (ignored: Exception) {}
        beaconSocket = null

        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (ignored: Exception) {}
        }
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (ignored: Exception) {}
        }

        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }
}
