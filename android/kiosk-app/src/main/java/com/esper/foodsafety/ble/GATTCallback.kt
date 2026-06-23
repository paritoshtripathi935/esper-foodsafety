package com.esper.foodsafety.ble

import android.bluetooth.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GATTCallback(
    private val onConnectionStateChange: (Boolean) -> Unit,
    private val onTemperatureReceived: (Float) -> Unit
) : BluetoothGattCallback() {

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("GATTCallback", "Connected to GATT server.")
                onConnectionStateChange(true)
                // Discover services after connection
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.e("GATTCallback", "Permission missing for discoverServices", e)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("GATTCallback", "Disconnected from GATT server.")
                onConnectionStateChange(false)
                try {
                    gatt.close()
                } catch (e: SecurityException) {
                    Log.e("GATTCallback", "Permission missing for close", e)
                }
            }
        } else {
            Log.e("GATTCallback", "Error $status encountered, disconnecting.")
            onConnectionStateChange(false)
            try {
                gatt.close()
            } catch (e: SecurityException) {
                Log.e("GATTCallback", "Permission missing for close", e)
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val service = gatt.getService(BLEManager.SERVICE_UUID)
            if (service != null) {
                val characteristic = service.getCharacteristic(BLEManager.TEMP_CHAR_UUID)
                if (characteristic != null) {
                    try {
                        // Enable local notifications
                        gatt.setCharacteristicNotification(characteristic, true)

                        // Enable remote notifications
                        val descriptor = characteristic.getDescriptor(BLEManager.CLIENT_CONFIG_DESCRIPTOR_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        } else {
                            Log.e("GATTCallback", "CCCD descriptor not found.")
                        }
                        
                        // Read initial value
                        gatt.readCharacteristic(characteristic)
                    } catch (e: SecurityException) {
                        Log.e("GATTCallback", "Permission missing for operations", e)
                    }
                } else {
                    Log.e("GATTCallback", "Temperature characteristic not found.")
                }
            } else {
                Log.e("GATTCallback", "Target service not found.")
            }
        } else {
            Log.w("GATTCallback", "onServicesDiscovered received: $status")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (characteristic.uuid == BLEManager.TEMP_CHAR_UUID) {
            decodeTemperature(characteristic.value)
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (characteristic.uuid == BLEManager.TEMP_CHAR_UUID) {
            decodeTemperature(value)
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BLEManager.TEMP_CHAR_UUID) {
            decodeTemperature(characteristic.value)
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BLEManager.TEMP_CHAR_UUID) {
            decodeTemperature(value)
        }
    }

    private fun decodeTemperature(data: ByteArray?) {
        if (data != null && data.size == 4) {
            val buffer = ByteBuffer.wrap(data)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val tempF = buffer.float
            onTemperatureReceived(tempF)
        }
    }
}
