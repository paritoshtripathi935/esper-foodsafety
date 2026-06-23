package com.esper.probesimulator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.esper.probesimulator.BuildConfig
import com.esper.probesimulator.ble.GATTServer
import com.esper.probesimulator.network.NetworkPoster
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var gattServer: GATTServer

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) gattServer.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gattServer = GATTServer(this)

        setContent {
            var temperature by remember { mutableFloatStateOf(38.0f) }
            var networkMode by remember { mutableStateOf(false) }
            var lastStatus by remember { mutableStateOf("Idle") }
            val scope = rememberCoroutineScope()

            LaunchedEffect(temperature, networkMode) {
                if (!networkMode) {
                    gattServer.updateTemperature(temperature)
                }
            }

            LaunchedEffect(networkMode) {
                if (networkMode) {
                    gattServer.stop()
                    lastStatus = "Network mode — tap Send"
                } else {
                    checkPermissionsAndStart()
                    lastStatus = "BLE advertising"
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "BLE Probe Simulator",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(12.dp))

                        // Mode toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("BLE", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = networkMode, onCheckedChange = { networkMode = it })
                            Spacer(Modifier.width(8.dp))
                            Text("Network (Supabase)", style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = lastStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lastStatus.startsWith("Sent") || lastStatus == "BLE advertising")
                                Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(32.dp))

                        Text(
                            text = "%.1f °F".format(temperature),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black
                        )

                        Spacer(Modifier.height(24.dp))

                        Slider(
                            value = temperature,
                            onValueChange = { temperature = (it * 10f).roundToInt() / 10f },
                            valueRange = 0f..120f
                        )

                        if (networkMode) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        lastStatus = "Sending..."
                                        val ok = NetworkPoster.postTempEvent(
                                            supabaseUrl = BuildConfig.SUPABASE_URL,
                                            anonKey = BuildConfig.SUPABASE_ANON_KEY,
                                            tempF = temperature
                                        )
                                        lastStatus = if (ok) "Sent ✓ ${temperature}°F" else "Send failed — check Supabase config"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Send to Supabase")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasAdvertise = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (hasAdvertise && hasConnect) gattServer.start()
            else requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            gattServer.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gattServer.stop()
    }
}
