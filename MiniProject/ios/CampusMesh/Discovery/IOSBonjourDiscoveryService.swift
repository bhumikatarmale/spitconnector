//
//  IOSBonjourDiscoveryService.swift
//  CampusMesh
//
//  Discovers nearby mesh peers via Apple Bonjour (mDNS) with UDP Multicast Beacon Fallback.
//  Service Type: _campusmesh._tcp.
//  Fallback Multicast Group: 239.255.77.88:5354
//

import Foundation
import Network

public final class IOSBonjourDiscoveryService: ObservableObject {
    public let localPeerId: String
    public let localDisplayName: String
    public let listenPort: UInt16

    private var browser: NWBrowser?
    private var advertiser: NWListener?
    private var udpBeaconTimer: Timer?
    private var udpListener: NWListener?

    private let queue = DispatchQueue(label: "org.campusmesh.discovery", qos: .utility)
    private let lock = NSLock()

    @Published public private(set) var discoveredPeers: [String: Peer] = [:]

    public var onPeerDiscovered: ((Peer) -> Void)?
    public var onPeerLost: ((String) -> Void)?

    public init(peerId: String, displayName: String, port: UInt16) {
        self.localPeerId = peerId
        self.localDisplayName = displayName
        self.listenPort = port
    }

    // MARK: - Lifecycle

    public func startDiscovery() {
        startBonjourAdvertising()
        startBonjourBrowsing()
        startUdpBeaconFallback()
    }

    public func stopDiscovery() {
        stopBonjourAdvertising()
        stopBonjourBrowsing()
        stopUdpBeaconFallback()
    }

    // MARK: - Bonjour (mDNS)

    private func startBonjourAdvertising() {
        queue.async {
            do {
                let params = NWParameters.tcp
                params.includePeerToPeer = true

                let listener = try NWListener(using: params, on: NWEndpoint.Port(rawValue: self.listenPort) ?? .any)
                // Advertise as _campusmesh._tcp
                let txtRecord = [
                    "peerId": self.localPeerId,
                    "platform": "IOS",
                    "port": "\(self.listenPort)"
                ]
                var txtData = Data()
                for (k, v) in txtRecord {
                    let entry = "\(k)=\(v)"
                    txtData.append(UInt8(entry.utf8.count))
                    txtData.append(contentsOf: entry.utf8)
                }

                listener.service = NWListener.Service(
                    name: "CampusMesh-\(self.localPeerId)",
                    type: "_campusmesh._tcp",
                    domain: "local.",
                    txtRecord: txtData
                )

                listener.stateUpdateHandler = { state in
                    if case .ready = state {
                        print("[IOSDiscovery] Bonjour advertiser ready on port \(self.listenPort)")
                    }
                }

                self.advertiser = listener
                listener.start(queue: self.queue)
            } catch {
                print("[IOSDiscovery] Failed to start Bonjour advertiser: \(error.localizedDescription)")
            }
        }
    }

    private func startBonjourBrowsing() {
        queue.async {
            let descriptor = NWBrowser.Descriptor.bonjour(type: "_campusmesh._tcp", domain: "local.")
            let params = NWParameters()
            params.includePeerToPeer = true

            let browser = NWBrowser(for: descriptor, using: params)
            browser.browseResultsChangedHandler = { [weak self] results, changes in
                guard let self = self else { return }
                self.processBrowseResults(results)
            }

            self.browser = browser
            browser.start(queue: self.queue)
            print("[IOSDiscovery] Bonjour browser started for _campusmesh._tcp")
        }
    }

    private func processBrowseResults(_ results: Set<NWBrowser.Result>) {
        for result in results {
            if case let .service(name, _, _, _) = result.endpoint {
                if name.contains(localPeerId) {
                    continue // Skip self
                }

                var discoveredId = name.replacingOccurrences(of: "CampusMesh-", with: "")
                var discoveredPlatform = "UNKNOWN"
                var targetPort = Int(listenPort)

                // Inspect metadata if available
                if case let .bonjour(txt) = result.metadata {
                    if let id = txt["peerId"] { discoveredId = id }
                    if let plat = txt["platform"] { discoveredPlatform = plat }
                    if let p = txt["port"], let portInt = Int(p) { targetPort = portInt }
                }

                // Create or update discovered peer
                let peer = Peer(
                    peerId: discoveredId,
                    displayName: name,
                    platform: discoveredPlatform,
                    ip: "bonjour.local", // Resolved dynamically via NWEndpoint
                    port: targetPort,
                    lastSeen: Date(),
                    transports: ["WIFI"]
                )

                registerDiscoveredPeer(peer)
            }
        }
    }

    private func stopBonjourAdvertising() {
        advertiser?.cancel()
        advertiser = nil
    }

    private func stopBonjourBrowsing() {
        browser?.cancel()
        browser = nil
    }

    // MARK: - UDP Multicast Beacon Fallback (239.255.77.88:5354)

    private func startUdpBeaconFallback() {
        DispatchQueue.main.async {
            self.udpBeaconTimer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { [weak self] _ in
                self?.broadcastBeacon()
            }
        }
        startUdpListener()
    }

    private func stopUdpBeaconFallback() {
        DispatchQueue.main.async {
            self.udpBeaconTimer?.invalidate()
            self.udpBeaconTimer = nil
        }
        udpListener?.cancel()
        udpListener = nil
    }

    private func broadcastBeacon() {
        queue.async {
            let beaconPayload = "CAMPUSMESH_BEACON:v=1;id=\(self.localPeerId);name=\(self.localDisplayName);plat=IOS;port=\(self.listenPort);tx=WIFI"
            guard let data = beaconPayload.data(using: .utf8) else { return }

            guard let multicastHost = try? NWEndpoint.Host("239.255.77.88"),
                  let multicastPort = NWEndpoint.Port(rawValue: 5354) else { return }

            let connection = NWConnection(host: multicastHost, port: multicastPort, using: .udp)
            connection.stateUpdateHandler = { state in
                if case .ready = state {
                    connection.send(content: data, completion: .contentProcessed { _ in
                        connection.cancel()
                    })
                }
            }
            connection.start(queue: self.queue)
        }
    }

    private func startUdpListener() {
        queue.async {
            do {
                guard let multicastPort = NWEndpoint.Port(rawValue: 5354) else { return }
                let params = NWParameters.udp
                params.allowLocalEndpointReuse = true

                let listener = try NWListener(using: params, on: multicastPort)
                listener.newConnectionHandler = { [weak self] connection in
                    connection.start(queue: self?.queue ?? .global())
                    connection.receiveMessage { data, _, _, _ in
                        if let data = data, let text = String(data: data, encoding: .utf8) {
                            self?.handleBeaconPacket(text, from: connection.endpoint)
                        }
                    }
                }
                self.udpListener = listener
                listener.start(queue: self.queue)
            } catch {
                print("[IOSDiscovery] UDP multicast listener warning: \(error.localizedDescription)")
            }
        }
    }

    private func handleBeaconPacket(_ packet: String, from endpoint: NWEndpoint) {
        guard packet.hasPrefix("CAMPUSMESH_BEACON:") else { return }
        let payload = packet.replacingOccurrences(of: "CAMPUSMESH_BEACON:", with: "")
        let pairs = payload.components(separatedBy: ";")
        var map: [String: String] = [:]
        for pair in pairs {
            let kv = pair.components(separatedBy: "=")
            if kv.count == 2 {
                map[kv[0]] = kv[1]
            }
        }

        guard let peerId = map["id"], peerId != localPeerId else { return }
        let name = map["name"] ?? peerId
        let plat = map["plat"] ?? "UNKNOWN"
        let port = Int(map["port"] ?? "5000") ?? 5000

        var remoteIp = "127.0.0.1"
        if case let .hostPort(host, _) = endpoint {
            remoteIp = "\(host)"
        }

        let peer = Peer(
            peerId: peerId,
            displayName: name,
            platform: plat,
            ip: remoteIp,
            port: port,
            lastSeen: Date(),
            transports: ["WIFI"]
        )

        registerDiscoveredPeer(peer)
    }

    private func registerDiscoveredPeer(_ peer: Peer) {
        lock.lock()
        defer { lock.unlock() }

        discoveredPeers[peer.peerId] = peer

        DispatchQueue.main.async {
            self.onPeerDiscovered?(peer)
        }
    }
}
