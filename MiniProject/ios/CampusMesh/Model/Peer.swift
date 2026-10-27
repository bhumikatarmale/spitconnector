//
//  Peer.swift
//  CampusMesh
//
//  Represents a local or discovered remote peer node in the mesh network.
//

import Foundation

public struct Peer: Identifiable, Hashable, Codable {
    public var id: String { peerId }
    public let peerId: String
    public var displayName: String
    public var platform: String
    public var ip: String
    public var port: Int
    public var lastSeen: Date
    public var transports: Set<String>

    public init(
        peerId: String,
        displayName: String,
        platform: String = "IOS",
        ip: String,
        port: Int,
        lastSeen: Date = Date(),
        transports: Set<String> = ["WIFI"]
    ) {
        self.peerId = peerId
        self.displayName = displayName
        self.platform = platform
        self.ip = ip
        self.port = port
        self.lastSeen = lastSeen
        self.transports = transports
    }

    public var isReachable: Bool {
        return !ip.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && port > 0
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(peerId)
    }

    public static func == (lhs: Peer, rhs: Peer) -> Bool {
        return lhs.peerId == rhs.peerId
    }
}
