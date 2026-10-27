//
//  IOSBleManager.swift
//  CampusMesh
//
//  CoreBluetooth Central and Peripheral manager for BLE mesh discovery and GATT message transfers.
//

import Foundation
import CoreBluetooth

public final class IOSBleManager: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralManagerDelegate, CBPeripheralDelegate {
    public let localPeerId: String
    private var centralManager: CBCentralManager!
    private var peripheralManager: CBPeripheralManager!

    private var identityChar: CBMutableCharacteristic?
    private var chunkChar: CBMutableCharacteristic?
    private var ackChar: CBMutableCharacteristic?

    private let assembler = IOSBleAssembler()

    public var onPeerDiscovered: ((Peer) -> Void)?
    public var onMessageReceived: ((Message) -> Void)?

    public init(peerId: String) {
        self.localPeerId = peerId
        super.init()
        self.centralManager = CBCentralManager(delegate: self, queue: nil)
        self.peripheralManager = CBPeripheralManager(delegate: self, queue: nil)

        assembler.onMessageComplete = { [weak self] msg in
            self?.onMessageReceived?(msg)
        }
    }

    // MARK: - CBPeripheralManagerDelegate

    public func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        if peripheral.state == .poweredOn {
            setupGattService()
        }
    }

    private func setupGattService() {
        let ident = CBMutableCharacteristic(
            type: IOSBleConstants.charPeerIdentity,
            properties: [.read, .notify],
            value: nil,
            permissions: [.readable]
        )
        let chunk = CBMutableCharacteristic(
            type: IOSBleConstants.charMessageChunk,
            properties: [.write, .writeWithoutResponse],
            value: nil,
            permissions: [.writeable]
        )
        let ack = CBMutableCharacteristic(
            type: IOSBleConstants.charTransferAck,
            properties: [.notify, .indicate],
            value: nil,
            permissions: [.readable]
        )

        let service = CBMutableService(type: IOSBleConstants.serviceUUID, primary: true)
        service.characteristics = [ident, chunk, ack]

        self.identityChar = ident
        self.chunkChar = chunk
        self.ackChar = ack

        peripheralManager.add(service)

        // Advertise CampusMesh Service
        peripheralManager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [IOSBleConstants.serviceUUID],
            CBAdvertisementDataLocalNameKey: "CampusMesh-\(localPeerId)"
        ])
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if request.characteristic.uuid == IOSBleConstants.charMessageChunk, let value = request.value {
                let ackPacket = assembler.processIncomingData(value)
                if let ack = ackPacket, let ackChar = self.ackChar {
                    peripheralManager.updateValue(ack.toData(), for: ackChar, onSubscribedCentrals: nil)
                }
                peripheral.respond(to: request, withResult: .success)
            } else {
                peripheral.respond(to: request, withResult: .requestNotSupported)
            }
        }
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        if request.characteristic.uuid == IOSBleConstants.charPeerIdentity {
            let identityStr = "id=\(localPeerId);plat=IOS"
            request.value = identityStr.data(using: .utf8)
            peripheral.respond(to: request, withResult: .success)
        } else {
            peripheral.respond(to: request, withResult: .requestNotSupported)
        }
    }

    // MARK: - CBCentralManagerDelegate

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            central.scanForPeripherals(withServices: [IOSBleConstants.serviceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? peripheral.name ?? "Nearby BLE Peer"
        let peerId = name.replacingOccurrences(of: "CampusMesh-", with: "")

        let peer = Peer(
            peerId: peerId,
            displayName: name,
            platform: "MOBILE",
            ip: "ble://\(peripheral.identifier.uuidString)",
            port: 0,
            transports: ["BLE"]
        )

        DispatchQueue.main.async {
            self.onPeerDiscovered?(peer)
        }
    }

    public func stop() {
        peripheralManager.stopAdvertising()
        centralManager.stopScan()
    }
}
