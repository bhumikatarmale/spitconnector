package org.campusmesh.android.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import org.campusmesh.android.model.Message
import org.campusmesh.android.model.Peer
import java.nio.charset.StandardCharsets

@SuppressLint("MissingPermission")
class AndroidBleManager(
    private val context: Context,
    private val localPeerId: String,
    private val onPeerDiscovered: (Peer) -> Unit,
    private val onMessageReceived: (Message) -> Unit
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    private val assembler = AndroidBleAssembler(
        onMessageComplete = { onMessageReceived(it) },
        onFailed = { txId, err -> println("[AndroidBleManager] Transfer $txId failed: $err") }
    )

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            println("[AndroidBleManager] BLE Advertising active for service ${AndroidBleConstants.SERVICE_UUID}")
        }
        override fun onStartFailure(errorCode: Int) {
            println("[AndroidBleManager] BLE Advertising failed with code $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val deviceName = it.device.name ?: "Nearby BLE Peer"
                val address = it.device.address
                val peer = Peer(
                    peerId = "BLE_${address.replace(":", "").takeLast(6)}",
                    displayName = deviceName,
                    platform = "MOBILE",
                    ip = "ble://$address",
                    port = 0,
                    transports = setOf("BLE")
                )
                onPeerDiscovered(peer)
            }
        }
    }

    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        scanner = bluetoothAdapter.bluetoothLeScanner

        setupGattServer()
        startAdvertising()
        startScanning()
    }

    private fun setupGattServer() {
        gattServer = bluetoothManager?.openGattServer(context, object : BluetoothGattServerCallback() {
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (characteristic?.uuid == AndroidBleConstants.CHAR_MESSAGE_CHUNK && value != null) {
                    val ack = assembler.processBytes(value)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ack?.toBytes())
                    }
                } else if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                if (characteristic?.uuid == AndroidBleConstants.CHAR_PEER_IDENTITY) {
                    val identity = "id=$localPeerId;plat=ANDROID".toByteArray(StandardCharsets.UTF_8)
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, identity)
                } else {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        })

        // Build GATT Service
        val service = BluetoothGattService(
            AndroidBleConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val identChar = BluetoothGattCharacteristic(
            AndroidBleConstants.CHAR_PEER_IDENTITY,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        val chunkChar = BluetoothGattCharacteristic(
            AndroidBleConstants.CHAR_MESSAGE_CHUNK,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val ackChar = BluetoothGattCharacteristic(
            AndroidBleConstants.CHAR_TRANSFER_ACK,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(identChar)
        service.addCharacteristic(chunkChar)
        service.addCharacteristic(ackChar)

        gattServer?.addService(service)
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(AndroidBleConstants.SERVICE_UUID))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun startScanning() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(AndroidBleConstants.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    fun stop() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            scanner?.stopScan(scanCallback)
            gattServer?.close()
        } catch (ignored: Exception) {}
    }
}
