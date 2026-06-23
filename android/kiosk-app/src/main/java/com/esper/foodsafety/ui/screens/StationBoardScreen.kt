package com.esper.foodsafety.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

@Composable
fun StationBoardScreen(
    temperature: StateFlow<Float?>,
    status: StateFlow<String>,
    discoveredDevices: StateFlow<List<BluetoothDevice>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
    onDeviceClick: (BluetoothDevice) -> Unit = {},
    onModeToggle: (() -> Unit)? = null,
    isNetworkMode: Boolean = false,
) {
    val currentStatus by status.collectAsState()
    val currentTemp by temperature.collectAsState()
    val devices by discoveredDevices.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Food Safety Monitor",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Status: $currentStatus",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Mode toggle
            if (onModeToggle != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BLE", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = isNetworkMode, onCheckedChange = { onModeToggle() })
                    Spacer(Modifier.width(6.dp))
                    Text("Network", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Device list — only in BLE scan mode
            if (!isNetworkMode && currentStatus.contains("Scanning") && devices.isNotEmpty()) {
                Text(
                    text = "Select a device to connect",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(devices) { device ->
                        @Suppress("MissingPermission")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onDeviceClick(device) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = device.name ?: "Unnamed Device", style = MaterialTheme.typography.bodyLarge)
                                Text(text = device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                // Temperature display
                Spacer(Modifier.height(48.dp))
                Text(
                    text = if (currentTemp != null) "%.1f °F".format(currentTemp) else "-- °F",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = if (currentTemp != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (currentTemp != null && currentTemp!! > 41f) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "⚠ TEMP EXCEEDED 41°F",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}
