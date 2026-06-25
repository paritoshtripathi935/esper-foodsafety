package com.esper.foodsafety

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import com.esper.foodsafety.ui.screens.KioskActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.esper.foodsafety.ai.ModelDownloader
import com.esper.foodsafety.ai.OnDeviceLLM
import com.esper.foodsafety.alerts.AlertEngine
import com.esper.foodsafety.alerts.AlertRule
import com.esper.foodsafety.alerts.AlertSoundPlayer
import com.esper.foodsafety.alerts.DEFAULT_ALERT_RULES
import com.esper.foodsafety.ble.BLEManager
import com.esper.foodsafety.sync.EventPayload
import com.esper.foodsafety.sync.DeviceIdentity
import com.esper.foodsafety.sync.DeviceRegistrar
import com.esper.foodsafety.sync.EventQueue
import com.esper.foodsafety.sync.NetworkProbePoller
import com.esper.foodsafety.sync.RealtimeProbeSubscription
import com.esper.foodsafety.sync.SyncWorker
import com.esper.foodsafety.timers.TimerManager
import com.esper.foodsafety.ui.screens.ActionLoggedScreen
import com.esper.foodsafety.ui.screens.AIProcessingScreen
import com.esper.foodsafety.ui.screens.EventHistoryScreen
import com.esper.foodsafety.ui.screens.HACCPReportsScreen
import com.esper.foodsafety.ui.screens.ProbePairingScreen
import com.esper.foodsafety.ui.screens.SetupScreen
import com.esper.foodsafety.ui.screens.SettingsScreen
import com.esper.foodsafety.ui.screens.StationBoardScreen
import com.esper.foodsafety.ui.screens.TimersScreen
import com.esper.foodsafety.voice.VoiceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

class MainActivity : KioskActivity() {

    private lateinit var bleManager: BLEManager
    private lateinit var realtimeProbe: RealtimeProbeSubscription
    private lateinit var networkPoller: NetworkProbePoller
    private lateinit var alertEngine: AlertEngine
    private lateinit var alertSoundPlayer: AlertSoundPlayer
    private lateinit var timerManager: TimerManager
    private lateinit var eventQueue: EventQueue

    private val activityScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _identity              = MutableStateFlow<DeviceIdentity.Identity?>(null)
    private val _isRegistering         = MutableStateFlow(false)
    private val _registrationError     = MutableStateFlow<String?>(null)
    private val _showVoiceLog          = MutableStateFlow(false)
    private val _isSendingAction       = MutableStateFlow(false)
    // null = ready (model on disk), 0f..1f = downloading
    private val _modelDownloadProgress = MutableStateFlow<Float?>(null)

    // Multi-station temperature map: station -> tempF
    private val _stationTemps = MutableStateFlow<Map<String, Float>>(emptyMap())
    // Single-temp for legacy UI consumers — always the first station value
    private val _singleTemp   = MutableStateFlow<Float?>(null)

    private val lastTempEnqueueMs    = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val tempEnqueueIntervalMs = 60_000L  // one reading per minute per station

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) bleManager.startScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved identity (or stay null → SetupScreen will show).
        _identity.value = DeviceIdentity.load(this)

        // Ensure model is on disk then warm the inference engine — background, non-blocking.
        activityScope.launch {
            runCatching {
                if (!ModelDownloader.isDownloaded(this@MainActivity)) {
                    _modelDownloadProgress.value = 0f
                }
                ModelDownloader.ensureDownloaded(this@MainActivity) { progress ->
                    _modelDownloadProgress.value = progress
                }
                _modelDownloadProgress.value = null  // signals "ready"
                OnDeviceLLM.warmUp(this@MainActivity)
            }.onFailure {
                Log.w("MainActivity", "On-device model init failed", it)
                _modelDownloadProgress.value = null
            }
        }

        bleManager    = BLEManager(this)
        realtimeProbe = RealtimeProbeSubscription(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            anonKey     = BuildConfig.SUPABASE_ANON_KEY,
        )
        networkPoller = NetworkProbePoller(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            anonKey     = BuildConfig.SUPABASE_ANON_KEY,
        )
        alertEngine      = AlertEngine(DEFAULT_ALERT_RULES)
        alertSoundPlayer = AlertSoundPlayer(this)
        eventQueue       = EventQueue(File(filesDir, "events"))

        timerManager = TimerManager { batchLabel, holdMinutes, value ->
            // Use first active station for timer events, fallback to default
            val station = _stationTemps.value.keys.firstOrNull() ?: "walk-in-cooler-1"
            enqueueEvent(
                type    = "timer",
                value   = 0.0,
                station = station,
                payload = mapOf(
                    "batch_label"  to batchLabel,
                    "hold_minutes" to holdMinutes.toString(),
                    "status"       to value,
                ),
            )
        }

        setContent {
            com.esper.foodsafety.ui.theme.SafeTempTheme {
            val identity          by _identity.collectAsState()
            val isRegistering     by _isRegistering.collectAsState()
            val registrationError by _registrationError.collectAsState()

            // First-boot: show setup form until identity is saved
            if (identity == null) {
                SetupScreen(
                    isRegistering = isRegistering,
                    error         = registrationError,
                    onConfirm     = { siteName, deviceName ->
                        activityScope.launch {
                            _isRegistering.value     = true
                            _registrationError.value = null
                            val id = DeviceIdentity.Identity(
                                siteId     = DeviceIdentity.toSlug(siteName,   "site"),
                                siteName   = siteName,
                                deviceId   = DeviceIdentity.toSlug(deviceName, "device"),
                                deviceName = deviceName,
                            )
                            runCatching { DeviceRegistrar.register(id) }
                                .onSuccess {
                                    DeviceIdentity.save(this@MainActivity, id)
                                    _identity.value = id
                                }
                                .onFailure { e ->
                                    _registrationError.value = e.message ?: "Unknown error"
                                }
                            _isRegistering.value = false
                        }
                    },
                )
                return@SafeTempTheme
            }

            var networkMode by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf("Board") }
            val showVoiceLog          by _showVoiceLog.collectAsState()
            val isSendingAction       by _isSendingAction.collectAsState()
            val modelDownloadProgress by _modelDownloadProgress.collectAsState()
            var showActionLogged by remember { mutableStateOf(false) }
            var loggedStation by remember { mutableStateOf("") }

            // Switch between BLE and network sources
            LaunchedEffect(networkMode) {
                if (networkMode) {
                    bleManager.stopAll()
                    // realtimeProbe.start()  // disabled: reconnect spin-loop + invalid Phoenix join, see RealtimeProbeSubscription. Re-enable post-demo.
                    networkPoller.start()
                } else {
                    // realtimeProbe.stop()
                    networkPoller.stop()
                    _stationTemps.value = emptyMap()
                    checkPermissionsAndStart()
                }
            }

            // In BLE mode: one station from the connected device
            LaunchedEffect(networkMode) {
                if (!networkMode) {
                    bleManager.temperature.collect { tempF ->
                        if (tempF == null) {
                            _stationTemps.value = emptyMap()
                            _singleTemp.value   = null
                            return@collect
                        }
                        val station = "walk-in-cooler-1"
                        _stationTemps.value = mapOf(station to tempF)
                        _singleTemp.value   = tempF
                        handleTempUpdate(station, tempF)
                        delay(1_000L)
                    }
                }
            }

            // Network mode: poller is the single source of truth (realtime WS disabled — see start() above).
            LaunchedEffect(networkMode) {
                if (networkMode) {
                    networkPoller.temperatures.collect { map ->
                        if (map.isNotEmpty()) {
                            _stationTemps.value = map
                            _singleTemp.value   = map.values.firstOrNull()
                            map.forEach { (station, tempF) -> handleTempUpdate(station, tempF) }
                        }
                    }
                }
            }

            val status: StateFlow<String> =
                if (networkMode) networkPoller.status else bleManager.connectionStatus

            // AI processing full-screen overlay
            if (isSendingAction) {
                AIProcessingScreen()
                return@SafeTempTheme
            }

            // Success overlay — auto-dismisses after 3s
            if (showActionLogged) {
                LaunchedEffect(Unit) {
                    delay(3_000L)
                    showActionLogged = false
                    currentScreen = "Board"
                }
                ActionLoggedScreen(
                    stationName = loggedStation,
                    onBack = { showActionLogged = false; currentScreen = "Board" },
                )
                return@SafeTempTheme
            }

            when (currentScreen) {
                "Timers" -> TimersScreen(
                    timers         = timerManager.timers,
                    onStartTimer   = { label, mins, station -> timerManager.start(label, mins, station) },
                    onCompleteTimer = { id -> timerManager.complete(id) },
                    activeStations = _stationTemps.value.keys.toList(),
                    onBack         = { currentScreen = "Board" },
                )
                "History" -> EventHistoryScreen(onBack = { currentScreen = "Board" })
                "Reports" -> HACCPReportsScreen(onBack = { currentScreen = "Board" })
                "Settings" -> SettingsScreen(
                    alertRules        = alertEngine.activeAlerts.value.map { it.rule }
                        .ifEmpty { com.esper.foodsafety.alerts.DEFAULT_ALERT_RULES },
                    kioskModeEnabled  = true,
                    supabaseConnected = com.esper.foodsafety.sync.SyncHealth.state.collectAsState().value.lastSuccessMs?.let {
                        System.currentTimeMillis() - it < 120_000L
                    } ?: false,
                    siteName          = identity?.siteName  ?: "",
                    deviceId          = identity?.deviceId  ?: "",
                    onThresholdChange = { ruleId, newLimitF -> alertEngine.updateRule(ruleId, newLimitF) },
                    onKioskModeToggle = { },
                    onBack            = { currentScreen = "Board" },
                )
                "Pairing" -> ProbePairingScreen(
                    discoveredDevices = bleManager.discoveredDevices,
                    onPair            = { device, _, _, _ -> bleManager.connectToDevice(device) },
                    onBack            = { currentScreen = "Board" },
                )
                else -> StationBoardScreen(
                    temperature       = _singleTemp,
                    stationTemps      = _stationTemps,
                    status            = status,
                    activeAlerts      = alertEngine.activeAlerts,
                    timers            = timerManager.timers,
                    discoveredDevices = bleManager.discoveredDevices,
                    isSendingAction   = isSendingAction,
                    onDeviceClick     = { bleManager.connectToDevice(it) },
                    onModeToggle      = { networkMode = !networkMode },
                    isNetworkMode     = networkMode,
                    siteName          = identity?.siteName ?: "",
                    showVoiceLog      = showVoiceLog,
                    onLogAction       = { _showVoiceLog.value = true },
                    onSubmitAction    = { transcript ->
                        loggedStation = _stationTemps.value.keys.firstOrNull()
                            ?.replace('-', ' ')
                            ?.split(' ')
                            ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                            ?: "Walk-in Cooler"
                        submitCorrectiveAction(transcript) { showActionLogged = true }
                    },
                    onCancelAction        = { _showVoiceLog.value = false },
                    modelDownloadProgress = modelDownloadProgress,
                    onStartTimer          = { label, minutes, station ->
                        timerManager.start(label, minutes, station)
                    },
                    onCompleteTimer   = { id -> timerManager.complete(id) },
                    onSilenceAlarm    = { alertSoundPlayer.stopAlarm() },
                    onNavItemClick    = { tab -> currentScreen = tab },
                )
            }
            } // SafeTempTheme
        }
    }

    private fun handleTempUpdate(station: String, tempF: Float) {
        activityScope.launch {
            val now  = System.currentTimeMillis()
            val last = lastTempEnqueueMs[station] ?: 0L
            if (now - last >= tempEnqueueIntervalMs) {
                lastTempEnqueueMs[station] = now
                enqueueEvent(type = "temp", value = tempF.toDouble(), station = station, probeId = station)
                SyncWorker.enqueue(this@MainActivity)
            }

            val newAlert = alertEngine.evaluate(station, tempF)
            if (newAlert != null) {
                alertSoundPlayer.startAlarm()
                enqueueEvent(
                    type    = "alert",
                    value   = tempF.toDouble(),
                    station = station,
                    payload = mapOf(
                        "threshold" to newAlert.rule.maxTempF.toString(),
                        "rule_id"   to newAlert.rule.ruleId,
                    ),
                )
            }
            if (!alertEngine.hasActiveAlert) alertSoundPlayer.stopAlarm()
        }
    }

    private fun submitCorrectiveAction(transcript: String, onComplete: (() -> Unit)? = null) {
        activityScope.launch {
            _isSendingAction.value = true
            val result     = VoiceLogger.structureNote(this@MainActivity, transcript)
            val structured = result.getOrNull()
            val station    = alertEngine.activeAlerts.value.firstOrNull()?.rule?.station
                ?: _stationTemps.value.keys.firstOrNull()
                ?: "walk-in-cooler-1"
            enqueueEvent(
                type    = "corrective_action",
                value   = 0.0,
                station = station,
                payload = buildMap {
                    put("transcript", transcript)
                    if (structured != null) {
                        put("action_taken", structured.actionTaken)
                        put("root_cause",   structured.rootCause)
                        put("disposition",  structured.disposition)
                        put("severity",     structured.severity)
                    }
                    alertEngine.firstActiveRuleId()?.let { put("rule_id", it) }
                },
            )
            SyncWorker.enqueue(this@MainActivity)
            _isSendingAction.value = false
            _showVoiceLog.value    = false
            onComplete?.invoke()
        }
    }

    private fun enqueueEvent(
        type: String,
        value: Double,
        station: String,
        probeId: String? = null,
        payload: Map<String, String> = emptyMap(),
    ) {
        activityScope.launch {
            val id = _identity.value ?: return@launch
            eventQueue.enqueue(
                EventPayload(
                    deviceId = id.deviceId,
                    siteId   = id.siteId,
                    station  = station,
                    probeId  = probeId,
                    type     = type,
                    value    = value,
                    ts       = Instant.now().toString(),
                    payload  = payload,
                )
            )
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan    = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)    == PackageManager.PERMISSION_GRANTED
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
        realtimeProbe.stop()
        networkPoller.stop()
        alertSoundPlayer.stopAlarm()
        timerManager.clear()
        OnDeviceLLM.close()
    }
}
