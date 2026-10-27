//
//  IOSBlePacket.swift
//  CampusMesh
//
//  Compact binary framing, chunking, and assembly for iOS CoreBluetooth transfers.
//

import Foundation
import zlib

public struct IOSBlePacket: Equatable {
    public static let headerSize = 13

    public let type: UInt8
    public let transferId: UInt16
    public let chunkIndex: UInt16
    public let totalChunks: UInt16
    public let crc32: UInt32
    public let payload: Data

    public init(type: UInt8, transferId: UInt16, chunkIndex: UInt16, totalChunks: UInt16, crc32: UInt32, payload: Data) {
        self.type = type
        self.transferId = transferId
        self.chunkIndex = chunkIndex
        self.totalChunks = totalChunks
        self.crc32 = crc32
        self.payload = payload
    }

    public static func calculateCrc32(data: Data) -> UInt32 {
        var crc: uLong = crc32(0, nil, 0)
        data.withUnsafeBytes { buffer in
            if let baseAddress = buffer.bindMemory(to: Bytef.self).baseAddress {
                crc = zlib.crc32(crc, baseAddress, uInt(data.count))
            }
        }
        return UInt32(crc)
    }

    public func toData() -> Data {
        var data = Data(capacity: Self.headerSize + payload.count)
        data.append(IOSBleConstants.magicByte1)
        data.append(IOSBleConstants.magicByte2)
        data.append(type)

        var tId = transferId.bigEndian
        data.append(Data(bytes: &tId, count: 2))

        var cIdx = chunkIndex.bigEndian
        data.append(Data(bytes: &cIdx, count: 2))

        var tot = totalChunks.bigEndian
        data.append(Data(bytes: &tot, count: 2))

        var crc = crc32.bigEndian
        data.append(Data(bytes: &crc, count: 4))

        data.append(payload)
        return data
    }

    public static func fromData(_ data: Data) -> IOSBlePacket? {
        guard data.count >= headerSize else { return nil }

        let m1 = data[0]
        let m2 = data[1]
        guard m1 == IOSBleConstants.magicByte1, m2 == IOSBleConstants.magicByte2 else { return nil }

        let type = data[2]
        let tId = data.subdata(in: 3..<5).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
        let cIdx = data.subdata(in: 5..<7).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
        let tot = data.subdata(in: 7..<9).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
        let crc = data.subdata(in: 9..<13).withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }

        let payload = data.subdata(in: 13..<data.count)

        return IOSBlePacket(type: type, transferId: tId, chunkIndex: cIdx, totalChunks: tot, crc32: crc, payload: payload)
    }
}

public final class IOSBleChunker {
    private static var transferCounter: UInt16 = 1

    public static func chunkMessage(_ message: Message, maxPacketSize: Int) -> [IOSBlePacket] {
        let json = message.toJson()
        guard let data = json.data(using: .utf8) else { return [] }

        let transferId = transferCounter
        transferCounter = (transferCounter == UInt16.max) ? 1 : (transferCounter + 1)

        let maxPayload = max(1, maxPacketSize - IOSBlePacket.headerSize)
        let totalCrc = IOSBlePacket.calculateCrc32(data: data)
        let totalChunks = UInt16(ceil(Double(data.count) / Double(maxPayload)))
        let effectiveTotal = max(1, totalChunks)

        var packets: [IOSBlePacket] = []
        var offset = 0

        for i in 0..<effectiveTotal {
            let chunkSize = min(maxPayload, data.count - offset)
            let chunkData: Data
            if chunkSize > 0 {
                chunkData = data.subdata(in: offset..<(offset + chunkSize))
                offset += chunkSize
            } else {
                chunkData = Data()
            }

            packets.append(IOSBlePacket(
                type: IOSBleConstants.typeChunk,
                transferId: transferId,
                chunkIndex: i,
                totalChunks: effectiveTotal,
                crc32: totalCrc,
                payload: chunkData
            ))
        }

        return packets
    }
}

public final class IOSBleAssembler {
    private class InboundSession {
        let transferId: UInt16
        let totalChunks: UInt16
        let expectedCrc: UInt32
        var receivedChunks: [UInt16: Data] = [:]
        let createdAt = Date()

        init(transferId: UInt16, totalChunks: UInt16, expectedCrc: UInt32) {
            self.transferId = transferId
            self.totalChunks = totalChunks
            self.expectedCrc = expectedCrc
        }

        var isComplete: Bool {
            return receivedChunks.count == Int(totalChunks)
        }
    }

    private var sessions: [UInt16: InboundSession] = [:]
    private let lock = NSLock()

    public var onMessageComplete: ((Message) -> Void)?
    public var onTransferFailed: ((UInt16, String) -> Void)?

    public init() {}

    public func processIncomingData(_ data: Data) -> IOSBlePacket? {
        guard let packet = IOSBlePacket.fromData(data) else { return nil }
        guard packet.type == IOSBleConstants.typeChunk else { return nil }

        lock.lock()
        defer { lock.unlock() }

        let session: InboundSession
        if let existing = sessions[packet.transferId] {
            session = existing
        } else {
            session = InboundSession(transferId: packet.transferId, totalChunks: packet.totalChunks, expectedCrc: packet.crc32)
            sessions[packet.transferId] = session
        }

        session.receivedChunks[packet.chunkIndex] = packet.payload

        if session.isComplete {
            sessions.removeValue(forKey: packet.transferId)
            var completeData = Data()
            for i in 0..<session.totalChunks {
                if let part = session.receivedChunks[i] {
                    completeData.append(part)
                }
            }

            let actualCrc = IOSBlePacket.calculateCrc32(data: completeData)
            if actualCrc != session.expectedCrc {
                onTransferFailed?(session.transferId, "CRC Mismatch")
                return IOSBlePacket(type: IOSBleConstants.typeAck, transferId: session.transferId, chunkIndex: 0, totalChunks: 1, crc32: actualCrc, payload: Data([0x01]))
            }

            guard let json = String(data: completeData, encoding: .utf8),
                  let msg = try? Message.fromJson(json) else {
                onTransferFailed?(session.transferId, "JSON Parse Error")
                return IOSBlePacket(type: IOSBleConstants.typeAck, transferId: session.transferId, chunkIndex: 0, totalChunks: 1, crc32: actualCrc, payload: Data([0x02]))
            }

            DispatchQueue.main.async {
                self.onMessageComplete?(msg)
            }

            return IOSBlePacket(type: IOSBleConstants.typeAck, transferId: session.transferId, chunkIndex: 0, totalChunks: 1, crc32: actualCrc, payload: Data([0x00]))
        }

        return nil
    }
}
