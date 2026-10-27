//
//  Message.swift
//  CampusMesh
//
//  Represents a DTN store-and-forward message payload across the mesh network.
//

import Foundation

public struct Message: Identifiable, Codable, Hashable {
    public var id: String { messageId }
    public let version: Int
    public let messageId: String
    public let sourceId: String
    public let destinationId: String
    public let createdAt: Date
    public let expiresAt: Date
    public let priority: Priority
    public var copyTokens: Int
    public let payload: String

    public init(
        version: Int = 1,
        messageId: String = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12).uppercased(),
        sourceId: String,
        destinationId: String,
        createdAt: Date = Date(),
        ttlSeconds: TimeInterval = 3600,
        priority: Priority = .MEDIUM,
        copyTokens: Int = 1,
        payload: String
    ) {
        self.version = version
        self.messageId = messageId
        self.sourceId = sourceId
        self.destinationId = destinationId
        self.createdAt = createdAt
        self.expiresAt = createdAt.addingTimeInterval(ttlSeconds)
        self.priority = priority
        self.copyTokens = copyTokens
        self.payload = payload
    }

    public init(
        version: Int = 1,
        messageId: String,
        sourceId: String,
        destinationId: String,
        createdAt: Date,
        expiresAt: Date,
        priority: Priority,
        copyTokens: Int,
        payload: String
    ) {
        self.version = version
        self.messageId = messageId
        self.sourceId = sourceId
        self.destinationId = destinationId
        self.createdAt = createdAt
        self.expiresAt = expiresAt
        self.priority = priority
        self.copyTokens = copyTokens
        self.payload = payload
    }

    public var remainingTTL: TimeInterval {
        return max(0, expiresAt.timeIntervalSinceNow)
    }

    public var isExpired: Bool {
        return Date() >= expiresAt
    }

    private static let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    public func toJson() -> String {
        let createdStr = Self.isoFormatter.string(from: createdAt)
        let expiresStr = Self.isoFormatter.string(from: expiresAt)
        let escapedPayload = payload
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
            .replacingOccurrences(of: "\t", with: "\\t")

        return "{\"version\":\(version),\"messageId\":\"\(messageId)\",\"sourceId\":\"\(sourceId)\",\"destinationId\":\"\(destinationId)\",\"createdAt\":\"\(createdStr)\",\"expiresAt\":\"\(expiresStr)\",\"priority\":\"\(priority.rawValue)\",\"copyTokens\":\(copyTokens),\"payload\":\"\(escapedPayload)\"}"
    }

    public static func fromJson(_ jsonString: String) throws -> Message {
        guard let data = jsonString.data(using: .utf8) else {
            throw NSError(domain: "CampusMesh", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid UTF-8 string"])
        }

        guard let jsonObject = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] else {
            throw NSError(domain: "CampusMesh", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON format"])
        }

        guard let msgId = jsonObject["messageId"] as? String,
              let srcId = jsonObject["sourceId"] as? String,
              let dstId = jsonObject["destinationId"] as? String,
              let pld = jsonObject["payload"] as? String else {
            throw NSError(domain: "CampusMesh", code: 3, userInfo: [NSLocalizedDescriptionKey: "Missing mandatory fields in JSON"])
        }

        let ver = (jsonObject["version"] as? NSNumber)?.intValue ?? 1
        let createdStr = jsonObject["createdAt"] as? String ?? ""
        let expiresStr = jsonObject["expiresAt"] as? String ?? ""
        let prioStr = jsonObject["priority"] as? String ?? "MEDIUM"
        let tokens = (jsonObject["copyTokens"] as? NSNumber)?.intValue ?? 1

        let createdDate = Self.isoFormatter.date(from: createdStr) ?? Date()
        let expiresDate = Self.isoFormatter.date(from: expiresStr) ?? createdDate.addingTimeInterval(3600)
        let prio = Priority.fromString(prioStr)

        return Message(
            version: ver,
            messageId: msgId,
            sourceId: srcId,
            destinationId: dstId,
            createdAt: createdDate,
            expiresAt: expiresDate,
            priority: prio,
            copyTokens: tokens,
            payload: pld
        )
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(messageId)
    }

    public static func == (lhs: Message, rhs: Message) -> Bool {
        return lhs.messageId == rhs.messageId
    }
}
