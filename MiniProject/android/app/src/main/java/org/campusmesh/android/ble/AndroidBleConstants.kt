package org.campusmesh.android.ble

import java.util.UUID

object AndroidBleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("0000fe60-0000-1000-8000-00805f9b34fb")
    val CHAR_PEER_IDENTITY: UUID = UUID.fromString("0000fe61-0000-1000-8000-00805f9b34fb")
    val CHAR_MESSAGE_CHUNK: UUID = UUID.fromString("0000fe62-0000-1000-8000-00805f9b34fb")
    val CHAR_TRANSFER_ACK: UUID = UUID.fromString("0000fe63-0000-1000-8000-00805f9b34fb")

    const val MAGIC_BYTE_1: Byte = 0x43 // 'C'
    const val MAGIC_BYTE_2: Byte = 0x4D // 'M'

    const val TYPE_START: Byte = 0x01
    const val TYPE_CHUNK: Byte = 0x02
    const val TYPE_ACK: Byte   = 0x03
    const val TYPE_ABORT: Byte = 0x04

    const val DEFAULT_ATT_MTU = 23
    const val ATT_HEADER_SIZE = 3
    const val DEFAULT_MAX_CHUNK_PAYLOAD = DEFAULT_ATT_MTU - ATT_HEADER_SIZE // 20 bytes
    const val NEGOTIATED_MAX_ATT_MTU = 512
    const val TRANSFER_TIMEOUT_MS = 15_000L
}
