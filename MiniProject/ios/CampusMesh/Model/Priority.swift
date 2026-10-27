//
//  Priority.swift
//  CampusMesh
//
//  Defines message priority levels for DTN queuing and transmission.
//

import Foundation

public enum Priority: String, Codable, CaseIterable, Comparable {
    case HIGH = "HIGH"
    case MEDIUM = "MEDIUM"
    case LOW = "LOW"

    public var numericWeight: Int {
        switch self {
        case .HIGH: return 3
        case .MEDIUM: return 2
        case .LOW: return 1
        }
    }

    public static func < (lhs: Priority, rhs: Priority) -> Bool {
        return lhs.numericWeight < rhs.numericWeight
    }

    public static func fromString(_ value: String?) -> Priority {
        guard let val = value?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() else {
            return .MEDIUM
        }
        return Priority(rawValue: val) ?? .MEDIUM
    }
}
