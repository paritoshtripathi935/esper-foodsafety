package com.esper.foodsafety.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEManager(private val context: Context) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
        val TEMP_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
        val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    private val _connectionStatus = MutableStateFlow("Idle")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _temperature = MutableStateFlow<Float?>(null)
    val temperature: StateFlow<Float?> = _temperature.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<android.bluetooth.BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<android.bluetooth.BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private var isScanning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentGatt: android.bluetooth.BluetoothGatt? = null

    private var reconnectAttempts = 0
    private val reconnectRunnable = Runnable { reconnectAttempts++; startScan() }

    private val gattCallback = GATTCallback(
        onConnectionStateChange = { isConnected ->
            if (isConnected) {
                reconnectAttempts = 0
                _connectionStatus.value = "Connected"
            } else {
                _connectionStatus.value = "Disconnected"
                _temperature.value = null
                if (reconnectAttempts < 5) {
                    mainHandler.postDelayed(reconnectRunnable, 2000L * (reconnectAttempts + 1))
                }
            }
        },
        onTemperatureReceived = { tempF ->
            _temperature.value = tempF
        }
    )

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            Log.d("BLEManager", "Found target device: ${device.address}")
            // Add device to the discovered list if not already present
            val currentList = _discoveredDevices.value.toMutableList()
            if (currentList.none { it.address == device.address }) {
                currentList.add(device)
                _discoveredDevices.value = currentList
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLEManager", "Scan failed: $errorCode")
            _connectionStatus.value = "Scan Failed ($errorCode)"
        }
    }

    fun startScan() {
        if (scanner == null || isScanning) return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        _connectionStatus.value = "Scanning..."
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        scanner?.stopScan(scanCallback)
    }

    /** Connect to a selected device */
    fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        stopScan()
        _connectionStatus.value = "Connecting..."
        currentGatt = device.connectGatt(context, false, gattCallback)
    }

    fun stopAll() {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectAttempts = 0
        stopScan()
        currentGatt?.close()
        currentGatt = null
        _connectionStatus.value = "Idle"
        _temperature.value = null
    }
}
