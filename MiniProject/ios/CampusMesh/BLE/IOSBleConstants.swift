//
//  IOSBleConstants.swift
//  CampusMesh
//
//  Constants and UUIDs for CoreBluetooth BLE operations.
//

import Foundation
import CoreBluetooth

public struct IOSBleConstants {
    public static let serviceUUID = CBUUID(string: "0000FE60-0000-1000-8000-00805F9B34FB")
    public static let charPeerIdentity = CBUUID(string: "0000FE61-0000-1000-8000-00805F9B34FB")
    public static let charMessageChunk = CBUUID(string: "0000FE62-0000-1000-8000-00805F9B34FB")
    public static let charTransferAck = CBUUID(string: "0000FE63-0000-1000-8000-00805F9B34FB")

    public static let magicByte1: UInt8 = 0x43 // 'C'
    public static let magicByte2: UInt8 = 0x4D // 'M'

    public static let typeStart: UInt8 = 0x01
    public static let typeChunk: UInt8 = 0x02
    public static let typeAck: UInt8   = 0x03
    public static let typeAbort: UInt8 = 0x04

    public static let defaultAttMtu = 23
    public static let attHeaderSize = 3
    public static let defaultMaxChunkPayload = defaultAttMtu - attHeaderSize // 20 bytes
    public static let negotiatedMaxAttMtu = 512
    public static let transferTimeoutSeconds: TimeInterval = 15.0
}
