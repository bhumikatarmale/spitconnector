package org.campusmesh.android.model

/**
 * Android peer endpoint representation matching the Java CampusMesh node.
 */
data class Peer(
    val peerId: String,
    val ipAddress: String = "127.0.0.1",
    val port: Int = 5001,
    val displayName: String = peerId,
    val platform: String = "ANDROID",
    val supportedTransports: Set<String> = setOf("WIFI")
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Peer) return false
        return peerId.equals(other.peerId, ignoreCase = true)
    }

    override fun hashCode(): Int {
        return peerId.lowercase().hashCode()
    }

    override fun toString(): String {
        return "Peer[$peerId ('$displayName', $platform) @ $ipAddress:$port]"
    }
}
