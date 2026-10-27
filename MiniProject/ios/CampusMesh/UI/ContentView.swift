//
//  ContentView.swift
//  CampusMesh
//
//  SwiftUI Main Dashboard for CampusMesh iOS offline mesh node.
//

import SwiftUI

public struct ContentView: View {
    @StateObject private var discovery: IOSBonjourDiscoveryService
    @StateObject private var buffer = IOSMessageBuffer()

    private let transport = IOSTCPTransport()
    private let localPeerId: String

    @State private var discoveredPeersList: [Peer] = []
    @State private var messagesList: [Message] = []
    @State private var selectedPeer: Peer? = nil
    @State private var showComposeSheet = false

    // Compose state
    @State private var destinationId = ""
    @State private var messagePayload = ""
    @State private var selectedPriority = Priority.MEDIUM
    @State private var ttlHours: Double = 1.0
    @State private var statusMessage = "Online - Discovering Mesh"

    public init() {
        let id = "IPHONE_" + UUID().uuidString.prefix(6).uppercased()
        self.localPeerId = id
        _discovery = StateObject(wrappedValue: IOSBonjourDiscoveryService(peerId: id, displayName: "CampusMesh-iOS", port: 5000))
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Node Status Card
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .foregroundColor(.blue)
                        Text(localPeerId)
                            .font(.headline)
                            .bold()
                        Spacer()
                        Circle()
                            .fill(Color.green)
                            .frame(width: 10, height: 10)
                        Text("Active")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    HStack {
                        Text(statusMessage)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("Buffer: \(buffer.currentSize / 1024) KB / \(buffer.maxCapacityBytes / (1024 * 1024)) MB")
                            .font(.caption2)
                            .foregroundColor(.gray)
                    }
                }
                .padding()
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(12)
                .padding(.horizontal)
                .padding(.top, 8)

                // Discovered Peers Section
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Discovered Peers (\(discoveredPeersList.count))")
                            .font(.headline)
                        Spacer()
                    }
                    .padding(.horizontal)
                    .padding(.top, 12)

                    if discoveredPeersList.isEmpty {
                        HStack {
                            Spacer()
                            VStack(spacing: 6) {
                                ProgressView()
                                Text("Scanning Wi-Fi & Bonjour for nearby nodes...")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            .padding(.vertical, 20)
                            Spacer()
                        }
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(discoveredPeersList) { peer in
                                    PeerCard(peer: peer) {
                                        selectedPeer = peer
                                        destinationId = peer.peerId
                                        showComposeSheet = true
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                        .frame(height: 105)
                    }
                }

                Divider()
                    .padding(.vertical, 8)

                // Messages Log
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Mesh Message Buffer (\(messagesList.count))")
                            .font(.headline)
                        Spacer()
                        Button(action: { showComposeSheet = true }) {
                            Label("Compose", systemImage: "square.and.pencil")
                                .font(.subheadline)
                        }
                    }
                    .padding(.horizontal)

                    if messagesList.isEmpty {
                        Spacer()
                        VStack(spacing: 8) {
                            Image(systemName: "tray")
                                .font(.system(size: 40))
                                .foregroundColor(.secondary)
                            Text("No store-and-forward messages yet.")
                                .font(.callout)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    } else {
                        List(messagesList) { msg in
                            MessageRow(message: msg)
                        }
                        .listStyle(.plain)
                    }
                }
            }
            .navigationTitle("CampusMesh iOS")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: refreshMesh) {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .sheet(isPresented: $showComposeSheet) {
                composeMessageSheet
            }
            .onAppear(perform: startMeshNode)
        }
    }

    // MARK: - Subviews

    private var composeMessageSheet: some View {
        NavigationView {
            Form {
                Section(header: Text("Target Destination")) {
                    TextField("Destination Peer ID (or BROADCAST)", text: $destinationId)
                }

                Section(header: Text("Priority & Lifecycle")) {
                    Picker("Priority", selection: $selectedPriority) {
                        ForEach(Priority.allCases, id: \.self) { prio in
                            Text(prio.rawValue).tag(prio)
                        }
                    }
                    .pickerStyle(.segmented)

                    VStack(alignment: .leading) {
                        Text("TTL: \(Int(ttlHours)) Hour(s)")
                        Slider(value: $ttlHours, in: 0.5...24.0, step: 0.5)
                    }
                }

                Section(header: Text("Payload")) {
                    TextEditor(text: $messagePayload)
                        .frame(height: 100)
                }

                Section {
                    Button(action: sendCurrentMessage) {
                        HStack {
                            Spacer()
                            Text("Dispatch to Mesh Network")
                                .bold()
                            Spacer()
                        }
                    }
                    .disabled(destinationId.isEmpty || messagePayload.isEmpty)
                }
            }
            .navigationTitle("Compose Message")
            .navigationBarItems(leading: Button("Cancel") { showComposeSheet = false })
        }
    }

    // MARK: - Mesh Logic

    private func startMeshNode() {
        transport.onMessageReceived = { message, remoteHost in
            if buffer.addMessage(message) {
                messagesList = buffer.getPendingMessages()
                statusMessage = "Received msg from \(message.sourceId)"
            }
        }

        transport.startServer(port: 5000) { result in
            switch result {
            case .success(let port):
                statusMessage = "Listening on port \(port)"
                discovery.onPeerDiscovered = { peer in
                    if !discoveredPeersList.contains(where: { $0.peerId == peer.peerId }) {
                        discoveredPeersList.append(peer)
                    }
                }
                discovery.startDiscovery()
            case .failure(let err):
                statusMessage = "Transport error: \(err.localizedDescription)"
            }
        }
    }

    private func refreshMesh() {
        buffer.purgeExpired()
        messagesList = buffer.getPendingMessages()
    }

    private func sendCurrentMessage() {
        let msg = Message(
            sourceId: localPeerId,
            destinationId: destinationId.trimmingCharacters(in: .whitespacesAndNewlines),
            ttlSeconds: ttlHours * 3600,
            priority: selectedPriority,
            copyTokens: 4,
            payload: messagePayload
        )

        _ = buffer.addMessage(msg)
        messagesList = buffer.getPendingMessages()
        showComposeSheet = false
        messagePayload = ""

        // If target peer is known and online, push directly
        if let target = discoveredPeersList.first(where: { $0.peerId == msg.destinationId }), target.isReachable {
            transport.sendMessage(message: msg, host: target.ip, port: UInt16(target.port)) { res in
                DispatchQueue.main.async {
                    switch res {
                    case .success:
                        statusMessage = "Message delivered directly to \(target.peerId)"
                    case .failure(let err):
                        statusMessage = "Direct transfer queued for DTN relay: \(err.localizedDescription)"
                    }
                }
            }
        }
    }
}

// MARK: - Helper UI Components

struct PeerCard: View {
    let peer: Peer
    let onTap: () -> Void

    var platformBadgeColor: Color {
        switch peer.platform.uppercased() {
        case "WINDOWS": return .blue
        case "UBUNTU": return .orange
        case "ANDROID": return .green
        case "IOS": return .purple
        default: return .gray
        }
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(peer.platform)
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(platformBadgeColor.opacity(0.2))
                        .foregroundColor(platformBadgeColor)
                        .cornerRadius(4)
                    Spacer()
                    Image(systemName: "wifi")
                        .font(.caption2)
                        .foregroundColor(.blue)
                }

                Text(peer.peerId)
                    .font(.subheadline)
                    .bold()
                    .lineLimit(1)
                    .foregroundColor(.primary)

                Text("\(peer.ip):\(peer.port)")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            .padding(10)
            .frame(width: 140, height: 90)
            .background(Color(UIColor.secondarySystemBackground))
            .cornerRadius(10)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.2), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct MessageRow: View {
    let message: Message

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(message.messageId)
                    .font(.caption)
                    .bold()
                Spacer()
                Text(message.priority.rawValue)
                    .font(.system(size: 10, weight: .bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(message.priority == .HIGH ? Color.red.opacity(0.2) : Color.blue.opacity(0.2))
                    .foregroundColor(message.priority == .HIGH ? .red : .blue)
                    .cornerRadius(4)
            }

            Text("\(message.sourceId) → \(message.destinationId)")
                .font(.subheadline)
                .foregroundColor(.secondary)

            Text(message.payload)
                .font(.body)
                .padding(.top, 2)

            HStack {
                Text("Tokens: \(message.copyTokens)")
                Spacer()
                Text("TTL: \(Int(message.remainingTTL / 60)) min remaining")
            }
            .font(.caption2)
            .foregroundColor(.gray)
            .padding(.top, 2)
        }
        .padding(.vertical, 4)
    }
}
