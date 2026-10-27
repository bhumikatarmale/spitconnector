package org.campusmesh.android.model

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.regex.Pattern

/**
 * Message model matching the CampusMesh Delay-Tolerant Networking (DTN) specification.
 * Fully wire-compatible with Java nodes.
 */
data class Message(
    val version: Int = 1,
    val messageId: String,
    val sourceId: String,
    val destinationId: String,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now().plusSeconds(120),
    val priority: Priority = Priority.MEDIUM,
    val payload: String = "",
    var copyTokens: Int = 1
) {
    init {
        require(messageId.isNotBlank()) { "messageId cannot be empty" }
        require(sourceId.isNotBlank()) { "sourceId cannot be empty" }
        require(destinationId.isNotBlank()) { "destinationId cannot be empty" }
        require(!expiresAt.isBefore(createdAt)) { "expiresAt cannot be before createdAt" }
    }

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun getRemainingTTL(): Long {
        val now = Instant.now()
        if (now.isAfter(expiresAt)) return 0L
        return maxOf(0L, Duration.between(now, expiresAt).seconds)
    }

    fun getSizeBytes(): Long {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8).size.toLong()
        val metadataOverhead = 128L
        return payloadBytes + metadataOverhead
    }

    fun toJson(): String {
        val escaped = payload.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return String.format(
            "{\"version\":%d,\"messageId\":\"%s\",\"sourceId\":\"%s\",\"destinationId\":\"%s\",\"createdAt\":\"%s\",\"expiresAt\":\"%s\",\"priority\":\"%s\",\"copyTokens\":%d,\"payload\":\"%s\"}",
            version, messageId, sourceId, destinationId, createdAt, expiresAt, priority.name, copyTokens, escaped
        )
    }

    companion object {
        fun fromJson(json: String): Message {
            require(json.isNotBlank()) { "JSON cannot be empty" }

            val ver = extractIntField(json, "version", 1)
            val msgId = extractStringField(json, "messageId") ?: throw IllegalArgumentException("Missing messageId")
            val srcId = extractStringField(json, "sourceId") ?: throw IllegalArgumentException("Missing sourceId")
            val destId = extractStringField(json, "destinationId") ?: throw IllegalArgumentException("Missing destinationId")
            val createdStr = extractStringField(json, "createdAt")
            val expiresStr = extractStringField(json, "expiresAt")
            val prioStr = extractStringField(json, "priority")
            val tokens = extractIntField(json, "copyTokens", 1)
            val pld = extractStringField(json, "payload") ?: ""

            val created = try {
                if (createdStr != null) Instant.parse(createdStr) else Instant.now()
            } catch (e: Exception) {
                Instant.now()
            }

            val expires = try {
                if (expiresStr != null) Instant.parse(expiresStr) else created.plusSeconds(60)
            } catch (e: Exception) {
                created.plusSeconds(60)
            }

            val priority = Priority.fromString(prioStr)

            return Message(ver, msgId, srcId, destId, created, expires, priority, pld, tokens)
        }

        private fun extractStringField(json: String, key: String): String? {
            val pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"")
            val matcher = pattern.matcher(json)
            if (!matcher.find()) return null
            val valueStart = matcher.end()
            val sb = StringBuilder()
            var escape = false
            for (i in valueStart until json.length) {
                val c = json[i]
                if (escape) {
                    when (c) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        else -> sb.append(c)
                    }
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    return sb.toString()
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun extractIntField(json: String, key: String, defaultValue: Int): Int {
            val matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json)
            if (matcher.find()) {
                return try {
                    matcher.group(1)?.toInt() ?: defaultValue
                } catch (ignored: NumberFormatException) {
                    defaultValue
                }
            }
            return defaultValue
        }
    }
}
