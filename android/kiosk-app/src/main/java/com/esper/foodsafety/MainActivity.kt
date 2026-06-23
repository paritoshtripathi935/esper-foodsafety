package com.esper.foodsafety

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.esper.foodsafety.ble.BLEManager
import com.esper.foodsafety.sync.NetworkProbePoller
import com.esper.foodsafety.ui.screens.StationBoardScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BLEManager
    private lateinit var networkPoller: NetworkProbePoller

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) bleManager.startScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = BLEManager(this)
        networkPoller = NetworkProbePoller(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
        )

        setContent {
            var networkMode by remember { mutableStateOf(false) }

            // Switch data source when mode changes
            LaunchedEffect(networkMode) {
                if (networkMode) {
                    bleManager.stopAll()
                    networkPoller.start()
                } else {
                    networkPoller.stop()
                    checkPermissionsAndStart()
                }
            }

            val temperature = if (networkMode) networkPoller.temperature else bleManager.temperature
            val status = if (networkMode) networkPoller.status else bleManager.connectionStatus

            StationBoardScreen(
                temperature = temperature,
                status = status,
                discoveredDevices = bleManager.discoveredDevices,
                onDeviceClick = { bleManager.connectToDevice(it) },
                onModeToggle = { networkMode = !networkMode },
                isNetworkMode = networkMode,
            )
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (hasScan && hasConnect) bleManager.startScan()
            else requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) bleManager.startScan()
            else requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopAll()
        networkPoller.stop()
    }
}
