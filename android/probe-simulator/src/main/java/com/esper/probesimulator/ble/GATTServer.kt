package com.esper.probesimulator.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log

@SuppressLint("MissingPermission")
class GATTServer(private val context: Context) {
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private var currentTemperature: Float = 38.0f
    private var tempCharacteristic: BluetoothGattCharacteristic? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("GATTServer", "Advertising started successfully")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("GATTServer", "Advertising failed with error: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("GATTServer", "Device connected: ${device.address}")
                connectedDevices.add(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("GATTServer", "Device disconnected: ${device.address}")
                connectedDevices.remove(device)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == TempService.TEMP_CHAR_UUID) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    TempService.encodeTemperature(currentTemperature)
                )
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == TempService.CLIENT_CONFIG_DESCRIPTOR_UUID) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
    }

    fun start() {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e("GATTServer", "Bluetooth is not enabled or supported")
            return
        }

        startServer()
        startAdvertising()
    }

    fun stop() {
        gattServer?.close()
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
    }

    fun updateTemperature(tempF: Float) {
        currentTemperature = tempF
        val encodedTemp = TempService.encodeTemperature(tempF)
        tempCharacteristic?.value = encodedTemp
        for (device in connectedDevices) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer?.notifyCharacteristicChanged(device, tempCharacteristic!!, false, encodedTemp)
            } else {
                @Suppress("DEPRECATION")
                gattServer?.notifyCharacteristicChanged(device, tempCharacteristic, false)
            }
        }
    }

    private fun startServer() {
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        
        val service = BluetoothGattService(TempService.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        tempCharacteristic = BluetoothGattCharacteristic(
            TempService.TEMP_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        val cccd = BluetoothGattDescriptor(
            TempService.CLIENT_CONFIG_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        tempCharacteristic?.addDescriptor(cccd)

        service.addCharacteristic(tempCharacteristic)
        gattServer?.addService(service)
    }

    private fun startAdvertising() {
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e("GATTServer", "Failed to create advertiser")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(TempService.SERVICE_UUID))
            .build()

        bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }
}
