//
//  IOSTCPTransport.swift
//  CampusMesh
//
//  High-performance length-prefixed TCP transport implemented with Apple Network.framework.
//  Wire Protocol: [4-Byte Big-Endian Length Prefix (UInt32)] + [UTF-8 JSON Payload Bytes]
//

import Foundation
import Network

public final class IOSTCPTransport {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "org.campusmesh.transport", qos: .userInitiated)
    private var isRunning = false

    public var onMessageReceived: ((Message, String) -> Void)?
    public private(set) var boundPort: UInt16 = 0

    public init() {}

    // MARK: - Server Listener

    public func startServer(port: UInt16 = 5000, completion: @escaping (Result<UInt16, Error>) -> Void) {
        queue.async {
            do {
                let tcpOptions = NWProtocolTCP.Options()
                tcpOptions.enableKeepalive = true
                tcpOptions.keepaliveIdle = 10

                let params = NWParameters(tls: nil, tcp: tcpOptions)
                params.allowLocalEndpointReuse = true
                params.includePeerToPeer = true

                let nwPort = NWEndpoint.Port(rawValue: port) ?? .any
                let newListener = try NWListener(using: params, on: nwPort)

                newListener.stateUpdateHandler = { [weak self] state in
                    switch state {
                    case .ready:
                        if let actualPort = newListener.port?.rawValue {
                            self?.boundPort = actualPort
                            self?.isRunning = true
                            completion(.success(actualPort))
                        }
                    case .failed(let error):
                        self?.isRunning = false
                        completion(.failure(error))
                    default:
                        break
                    }
                }

                newListener.newConnectionHandler = { [weak self] connection in
                    self?.handleIncomingConnection(connection)
                }

                self.listener = newListener
                newListener.start(queue: self.queue)
            } catch {
                completion(.failure(error))
            }
        }
    }

    private func handleIncomingConnection(_ connection: NWConnection) {
        connection.start(queue: queue)
        readLengthPrefix(from: connection)
    }

    private func readLengthPrefix(from connection: NWConnection) {
        // Read exact 4-byte big-endian length prefix
        connection.receive(minimumIncompleteLength: 4, maximumLength: 4) { [weak self] content, _, isComplete, error in
            guard let self = self else { return }

            if let data = content, data.count == 4 {
                let length = data.withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
                if length > 0 && length <= 10 * 1024 * 1024 { // Cap max single transfer to 10MB
                    self.readMessagePayload(from: connection, expectedLength: Int(length))
                    return
                }
            }

            if isComplete || error != nil {
                connection.cancel()
            }
        }
    }

    private func readMessagePayload(from connection: NWConnection, expectedLength: Int) {
        connection.receive(minimumIncompleteLength: expectedLength, maximumLength: expectedLength) { [weak self] content, _, isComplete, error in
            guard let self = self else { return }

            if let data = content, data.count == expectedLength {
                if let jsonString = String(data: data, encoding: .utf8) {
                    do {
                        let message = try Message.fromJson(jsonString)
                        let remoteHost: String
                        if case let .hostPort(host, _) = connection.endpoint {
                            remoteHost = "\(host)"
                        } else {
                            remoteHost = "unknown"
                        }

                        DispatchQueue.main.async {
                            self.onMessageReceived?(message, remoteHost)
                        }
                    } catch {
                        print("[IOSTCPTransport] Failed to parse message JSON: \(error.localizedDescription)")
                    }
                }
            }

            connection.cancel()
        }
    }

    // MARK: - Outbound Transmission

    public func sendMessage(
        message: Message,
        host: String,
        port: UInt16,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        queue.async {
            let jsonString = message.toJson()
            guard let payloadData = jsonString.data(using: .utf8) else {
                completion(.failure(NSError(domain: "CampusMesh", code: 4, userInfo: [NSLocalizedDescriptionKey: "Encoding error"])))
                return
            }

            var length = UInt32(payloadData.count).bigEndian
            var packetData = Data(bytes: &length, count: MemoryLayout<UInt32>.size)
            packetData.append(payloadData)

            let endpointHost = NWEndpoint.Host(host)
            guard let endpointPort = NWEndpoint.Port(rawValue: port) else {
                completion(.failure(NSError(domain: "CampusMesh", code: 5, userInfo: [NSLocalizedDescriptionKey: "Invalid target port"])))
                return
            }

            let tcpOptions = NWProtocolTCP.Options()
            tcpOptions.connectionTimeout = 5
            let params = NWParameters(tls: nil, tcp: tcpOptions)
            params.includePeerToPeer = true

            let connection = NWConnection(host: endpointHost, port: endpointPort, using: params)

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    connection.send(content: packetData, completion: .contentProcessed { sendError in
                        if let sendError = sendError {
                            connection.cancel()
                            completion(.failure(sendError))
                        } else {
                            connection.cancel()
                            completion(.success(()))
                        }
                    })
                case .failed(let error):
                    connection.cancel()
                    completion(.failure(error))
                default:
                    break
                }
            }

            connection.start(queue: self.queue)
        }
    }

    public func stop() {
        queue.async {
            self.listener?.cancel()
            self.listener = nil
            self.isRunning = false
        }
    }
}
