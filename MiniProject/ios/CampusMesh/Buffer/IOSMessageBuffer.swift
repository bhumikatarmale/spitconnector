//
//  IOSMessageBuffer.swift
//  CampusMesh
//
//  Thread-safe bounded store-and-forward message buffer with priority and TTL eviction.
//

import Foundation

public final class IOSMessageBuffer: ObservableObject {
    public let maxCapacityBytes: Int
    private var messages: [Message] = []
    private let lock = NSLock()

    @Published public private(set) var currentSize: Int = 0

    public init(maxCapacityBytes: Int = 10 * 1024 * 1024) { // 10MB default
        self.maxCapacityBytes = maxCapacityBytes
    }

    public func addMessage(_ message: Message) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        // Reject expired messages immediately
        if message.isExpired {
            return false
        }

        // Deduplication
        if messages.contains(where: { $0.messageId == message.messageId }) {
            return false
        }

        // Purge expired messages before checking capacity
        internalPurgeExpired()

        let estimatedSize = message.toJson().utf8.count
        if estimatedSize > maxCapacityBytes {
            return false
        }

        // Evict if over capacity (evict lowest priority, then oldest)
        while (currentSize + estimatedSize) > maxCapacityBytes && !messages.isEmpty {
            // Find victim: lowest priority, oldest createdAt
            if let victimIndex = messages.indices.min(by: { a, b in
                if messages[a].priority == messages[b].priority {
                    return messages[a].createdAt < messages[b].createdAt
                }
                return messages[a].priority < messages[b].priority
            }) {
                let victim = messages.remove(at: victimIndex)
                currentSize -= victim.toJson().utf8.count
            }
        }

        messages.append(message)
        currentSize += estimatedSize
        return true
    }

    public func removeMessage(id: String) -> Message? {
        lock.lock()
        defer { lock.unlock() }

        if let index = messages.firstIndex(where: { $0.messageId == id }) {
            let msg = messages.remove(at: index)
            currentSize -= msg.toJson().utf8.count
            return msg
        }
        return nil
    }

    public func purgeExpired() {
        lock.lock()
        defer { lock.unlock() }
        internalPurgeExpired()
    }

    private func internalPurgeExpired() {
        let beforeCount = messages.count
        messages.removeAll(where: { $0.isExpired })
        if messages.count != beforeCount {
            currentSize = messages.reduce(0) { $0 + $1.toJson().utf8.count }
        }
    }

    public func getPendingMessages() -> [Message] {
        lock.lock()
        defer { lock.unlock() }
        internalPurgeExpired()
        return messages
    }

    public func clear() {
        lock.lock()
        defer { lock.unlock() }
        messages.removeAll()
        currentSize = 0
    }
}
