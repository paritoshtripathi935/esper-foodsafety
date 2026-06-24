package com.esper.probesimulator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.esper.probesimulator.ble.GATTServer
import com.esper.probesimulator.network.NetworkPoster
import com.esper.probesimulator.scenarios.Scenario
import com.esper.probesimulator.scenarios.ScenarioRunner
import com.esper.probesimulator.ui.ProbeIdentity
import com.esper.probesimulator.ui.ProbeSetupDialog
import com.esper.probesimulator.ui.SimulatorContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

private const val PREFS_NAME  = "probe_identity"
private const val KEY_STATION = "station_id"
private const val KEY_DEVICE  = "device_id"
private const val KEY_SITE    = "site_id"

class MainActivity : ComponentActivity() {

    private lateinit var gattServer: GATTServer
    private lateinit var scenarioRunner: ScenarioRunner
    private val activityScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val manualTemp  = MutableStateFlow(38.0f)
    private val networkMode = MutableStateFlow(false)
    private val lastStatus  = MutableStateFlow("Idle")

    private var networkPollJob: Job? = null

    // Probe identity — loaded from prefs, set via dialog on first run
    private val probeIdentity = MutableStateFlow<ProbeIdentity?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) gattServer.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load persisted identity
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedStation = prefs.getString(KEY_STATION, null)
        val savedDevice  = prefs.getString(KEY_DEVICE,  null)
        val savedSite    = prefs.getString(KEY_SITE,    null)
        if (savedStation != null && savedDevice != null && savedSite != null) {
            probeIdentity.value = ProbeIdentity(savedStation, savedDevice, savedSite)
        }

        gattServer = GATTServer(this)

        scenarioRunner = ScenarioRunner { tempF ->
            manualTemp.value = tempF
            if (networkMode.value) {
                activityScope.launch { postTemp(tempF) }
            } else {
                gattServer.updateTemperature(tempF)
            }
        }

        setContent {
            MaterialTheme {
                val identity      by probeIdentity.collectAsState()
                val isNetworkMode by networkMode.collectAsState()
                val statusText    by lastStatus.collectAsState()

                // Show setup dialog until identity is confirmed
                if (identity == null) {
                    ProbeSetupDialog(initial = null) { confirmed ->
                        saveIdentity(confirmed)
                        probeIdentity.value = confirmed
                        // Register this probe-sim in the devices table
                        activityScope.launch {
                            NetworkPoster.upsertDevice(
                                supabaseUrl = BuildConfig.SUPABASE_URL,
                                anonKey     = BuildConfig.SUPABASE_ANON_KEY,
                                deviceId    = confirmed.deviceId,
                                siteId      = confirmed.siteId,
                                deviceName  = "${confirmed.stationId} simulator",
                            )
                        }
                    }
                    return@MaterialTheme
                }

                LaunchedEffect(isNetworkMode) {
                    if (isNetworkMode) {
                        gattServer.stop()
                        startNetworkPolling()
                    } else {
                        stopNetworkPolling()
                        checkPermissionsAndStart()
                        lastStatus.value = "BLE advertising"
                    }
                }

                LaunchedEffect(manualTemp.collectAsState().value) {
                    if (!isNetworkMode && scenarioRunner.currentScenario.value == null) {
                        gattServer.updateTemperature(manualTemp.value)
                    }
                }

                SimulatorContent(
                    temperature = manualTemp,
                    activeScenario = scenarioRunner.currentScenario,
                    networkMode = isNetworkMode,
                    lastStatus = statusText,
                    isSending = false,
                    probeIdentity = identity,
                    onModeToggle = { on -> networkMode.value = on },
                    onStartScenario = { scenario: Scenario ->
                        stopNetworkPolling()
                        scenarioRunner.start(scenario)
                        lastStatus.value = "Running: ${scenario.name}"
                    },
                    onStopScenario = {
                        scenarioRunner.stop()
                        if (isNetworkMode) startNetworkPolling()
                        lastStatus.value = if (isNetworkMode) "Network — posting" else "BLE advertising"
                    },
                    onManualTempChange = { t ->
                        manualTemp.value = t
                        if (!isNetworkMode) gattServer.updateTemperature(t)
                    },
                    onChangeIdentity = {
                        // Show dialog again to re-configure
                        probeIdentity.value = null
                    },
                )
            }
        }
    }

    private fun saveIdentity(identity: ProbeIdentity) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATION, identity.stationId)
            .putString(KEY_DEVICE,  identity.deviceId)
            .putString(KEY_SITE,    identity.siteId)
            .apply()
    }

    private fun startNetworkPolling() {
        networkPollJob?.cancel()
        networkPollJob = activityScope.launch {
            lastStatus.value = "Network — posting"
            while (isActive) {
                if (scenarioRunner.currentScenario.value == null) {
                    postTemp(manualTemp.value)
                }
                delay(2_000L)
            }
        }
    }

    private fun stopNetworkPolling() {
        networkPollJob?.cancel()
        networkPollJob = null
    }

    private suspend fun postTemp(tempF: Float) {
        val id = probeIdentity.value ?: return
        val ok = NetworkPoster.postTempEvent(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            anonKey     = BuildConfig.SUPABASE_ANON_KEY,
            tempF       = tempF,
            deviceId    = id.deviceId,
            siteId      = id.siteId,
            station     = id.stationId,
            probeId     = id.deviceId,
        )
        lastStatus.value = if (ok)
            "${id.stationId} — sent ${"%.1f".format(tempF)}°F ✓"
        else
            "${id.stationId} — send failed"
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasAdvertise = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            val hasConnect   = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)   == PackageManager.PERMISSION_GRANTED
            if (hasAdvertise && hasConnect) gattServer.start()
            else requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            gattServer.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gattServer.stop()
        scenarioRunner.clear()
        stopNetworkPolling()
        activityScope.cancel()
    }
}
