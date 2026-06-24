package com.esper.foodsafety.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.ai.ModelDownloader
import com.esper.foodsafety.alerts.AlertEngine
import com.esper.foodsafety.timers.TimerState
import com.esper.foodsafety.ui.components.AlertBanner
import com.esper.foodsafety.ui.components.HoldTimerModal
import com.esper.foodsafety.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val JetBrainsMono = FontFamily.Monospace

@Composable
fun StationBoardScreen(
    temperature: StateFlow<Float?>,
    stationTemps: StateFlow<Map<String, Float>> = kotlinx.coroutines.flow.MutableStateFlow(emptyMap()),
    status: StateFlow<String>,
    activeAlerts: StateFlow<List<AlertEngine.AlertState>>,
    timers: StateFlow<List<TimerState>>,
    discoveredDevices: StateFlow<List<BluetoothDevice>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
    isSendingAction: Boolean = false,
    onDeviceClick: (BluetoothDevice) -> Unit = {},
    onModeToggle: (() -> Unit)? = null,
    isNetworkMode: Boolean = false,
    onStartTimer: (String, Int, String) -> Unit = { _, _, _ -> },
    onCompleteTimer: (String) -> Unit = {},
    onLogAction: () -> Unit = {},
    onSilenceAlarm: () -> Unit = {},
    onSubmitAction: (String) -> Unit = {},
    onCancelAction: () -> Unit = {},
    showVoiceLog: Boolean = false,
    onNavItemClick: ((String) -> Unit)? = null,
    modelDownloadProgress: Float? = null,
    siteName: String = "",
) {
    val currentStatus by status.collectAsState()
    val currentTemp   by temperature.collectAsState()
    val stationMap    by stationTemps.collectAsState()
    val devices       by discoveredDevices.collectAsState()
    val alerts        by activeAlerts.collectAsState()
    val timerList     by timers.collectAsState()

    var showTimerModal by remember { mutableStateOf(false) }

    if (showTimerModal) {
        HoldTimerModal(
            onStart = { label, mins, sta ->
                onStartTimer(label, mins, sta)
                showTimerModal = false
            },
            onDismiss = { showTimerModal = false },
        )
    }

    if (showVoiceLog) {
        VoiceLogScreen(
            isSending = isSendingAction,
            onSubmit = onSubmitAction,
            onCancel = onCancelAction,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(SurfaceContainer, Background),
                    radius = 1800f,
                )
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Side Nav ──────────────────────────────────────────────────
            SideNav(
                currentTab = "Board",
                onNavItemClick = onNavItemClick ?: {},
                modifier = Modifier.zIndex(5f),
            )

            // ── Main Area ─────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top App Bar ───────────────────────────────────────────
                TopBar(
                    currentStatus = currentStatus,
                    isNetworkMode = isNetworkMode,
                    onModeToggle = onModeToggle,
                    modelDownloadProgress = modelDownloadProgress,
                    siteName = siteName,
                )

                // Alert banner sits below the top bar, pushes content down
                if (alerts.isNotEmpty()) {
                    AlertBanner(
                        alerts = alerts,
                        onLogAction = onLogAction,
                        onSilenceAlarm = onSilenceAlarm,
                    )
                }

                // ── Canvas ────────────────────────────────────────────────
                val isScanning = !isNetworkMode && currentStatus.contains("Scanning") && devices.isNotEmpty()

                if (isScanning) {
                    DevicePicker(devices = devices, onDeviceClick = onDeviceClick)
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                                .padding(bottom = 140.dp),
                        ) {
                            // Section header row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    "Active Monitoring",
                                    color = OnSurface,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 36.sp,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    LegendDot(color = PrimaryContainer, label = "Nominal")
                                    LegendDot(color = TertiaryContainer, label = "Warning")
                                    LegendDot(color = ErrorColor, label = "Critical")
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            // Dynamic bento grid — one card per active probe
                            val stationCards = buildDynamicStationCards(stationMap, currentTemp, alerts)
                            if (stationCards.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "No probes sending data.\nSwitch a simulator to Network mode.",
                                        color = OnSurfaceVariant,
                                        fontSize = 18.sp,
                                        lineHeight = 28.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                BentoGrid(
                                    cards = stationCards,
                                    timers = timerList,
                                    onCompleteTimer = onCompleteTimer,
                                )
                            }
                        }

                        // Floating bottom bar
                        BottomFloatingBar(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onStartTimer = { showTimerModal = true },
                            onMicPress = onLogAction,
                        )
                    }
                }
            }
        }
    }
}

// ── Side Nav ──────────────────────────────────────────────────────────────────

@Composable
private fun SideNav(
    currentTab: String,
    onNavItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(SurfaceContainer)
            .border(width = 1.dp, color = SurfaceVariant, shape = RoundedCornerShape(0.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Brand avatar
        Box(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 32.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHigh)
                .border(1.dp, SurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("ST", color = Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        NavItem(icon = "⬛", label = "Board",    active = currentTab == "Board",    onClick = { onNavItemClick("Board") })
        NavItem(icon = "⏱",  label = "Timers",   active = currentTab == "Timers",   onClick = { onNavItemClick("Timers") })
        NavItem(icon = "📋", label = "History",  active = currentTab == "History",  onClick = { onNavItemClick("History") })
        NavItem(icon = "📊", label = "Reports",  active = currentTab == "Reports",  onClick = { onNavItemClick("Reports") })

        Spacer(Modifier.weight(1f))

        NavItem(icon = "🔗", label = "Pairing",  active = currentTab == "Pairing",  onClick = { onNavItemClick("Pairing") })
        NavItem(icon = "⚙",  label = "Settings", active = currentTab == "Settings", onClick = { onNavItemClick("Settings") })
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NavItem(icon: String, label: String, active: Boolean, onClick: () -> Unit = {}) {
    val bg = if (active) SurfaceContainerHighest else Color.Transparent
    val fg = if (active) Primary else OnSurfaceVariant.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(bg)
            .drawBehind {
                if (active) drawLine(
                    color = Primary,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 4.dp.toPx(),
                )
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 22.sp)
            Text(
                label.uppercase(),
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
            )
        }
    }
}

// ── Top App Bar ───────────────────────────────────────────────────────────────

private val ClockFormatter = DateTimeFormatter.ofPattern("HH:mm | MMM dd")

@Composable
private fun TopBar(
    currentStatus: String,
    isNetworkMode: Boolean = false,
    onModeToggle: (() -> Unit)? = null,
    modelDownloadProgress: Float? = null,  // null = ready, 0..1 = downloading
    siteName: String = "",
) {
    val context = LocalContext.current
    var clockText by remember { mutableStateOf(LocalDateTime.now().format(ClockFormatter)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            clockText = LocalDateTime.now().format(ClockFormatter)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Background)
            .border(width = 1.dp, color = SurfaceVariant, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "SafeTemp",
                color = Primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMono,
            )
            if (siteName.isNotBlank()) Text(siteName, color = OnSurfaceVariant, fontSize = 18.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // BLE / Network mode toggle
            if (onModeToggle != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "BLE",
                        color = if (!isNetworkMode) Primary else OnSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(
                        checked = isNetworkMode,
                        onCheckedChange = { onModeToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnPrimary,
                            checkedTrackColor = PrimaryContainer,
                            uncheckedThumbColor = OnSurface,
                            uncheckedTrackColor = SurfaceContainerHigh,
                        ),
                    )
                    Text(
                        "Network",
                        color = if (isNetworkMode) Primary else OnSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // On-device model status chip
            val modelReady = ModelDownloader.isDownloaded(context)
            val chipColor  = if (modelReady) PrimaryContainer.copy(alpha = 0.12f)
                             else Color(0xFF2A2010)
            val chipBorder = if (modelReady) Primary.copy(alpha = 0.2f)
                             else Color(0xFFE6A817).copy(alpha = 0.4f)
            val chipText   = when {
                modelReady              -> "On-device AI ✓"
                modelDownloadProgress != null -> "Downloading AI ${(modelDownloadProgress * 100).toInt()}%"
                else                    -> "On-device AI: not downloaded"
            }
            val chipTextColor = if (modelReady) Primary else Color(0xFFE6A817)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(chipColor)
                    .border(1.dp, chipBorder, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("🤖", fontSize = 14.sp)
                Column {
                    Text(chipText, color = chipTextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (modelDownloadProgress != null && !modelReady) {
                        LinearProgressIndicator(
                            progress = { modelDownloadProgress },
                            modifier = Modifier.width(100.dp).height(3.dp),
                            color = Color(0xFFE6A817),
                            trackColor = Color(0xFF2A2010),
                        )
                    }
                }
            }

            // Probe status pill
            val isConnected = currentStatus.contains("advertising", ignoreCase = true)
                    || currentStatus.contains("posting", ignoreCase = true)
                    || currentStatus.contains("✓", ignoreCase = true)
                    || currentStatus.contains("connected", ignoreCase = true)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PrimaryContainer.copy(alpha = 0.12f))
                    .border(1.dp, Primary.copy(alpha = 0.2f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("🌡", fontSize = 16.sp)
                Text(
                    if (isConnected) "Probe connected" else currentStatus,
                    color = Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Clock-style display
            Text(
                clockText,
                color = OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMono,
            )
        }
    }
}

// ── Station Cards ─────────────────────────────────────────────────────────────

data class StationCardData(
    val name: String,
    val zone: String,
    val tempF: Float?,
    val safeLabel: String,
    val isAlert: Boolean,
    val isWarning: Boolean,
    val timerLabel: String? = null,
    val timerDisplay: String? = null,
)

/** Builds one card per station currently in [stationMap], falling back to [legacyTemp] for BLE mode. */
private fun buildDynamicStationCards(
    stationMap: Map<String, Float>,
    legacyTemp: Float?,
    alerts: List<AlertEngine.AlertState>,
): List<StationCardData> {
    // Network mode: use the live map
    if (stationMap.isNotEmpty()) {
        return stationMap.entries.map { (stationId, tempF) ->
            val isAlert   = alerts.any { it.rule.station == stationId }
            val isWarning = !isAlert && tempF > 40f
            StationCardData(
                name      = stationId.replace('-', ' ').split(' ')
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                zone      = stationId,
                tempF     = tempF,
                safeLabel = "Safe ≤ 41°F",
                isAlert   = isAlert,
                isWarning = isWarning,
            )
        }
    }
    // BLE mode: single device
    if (legacyTemp != null) {
        val stationId = "walk-in-cooler-1"
        val isAlert   = alerts.any { it.rule.station == stationId }
        val isWarning = !isAlert && legacyTemp > 40f
        return listOf(
            StationCardData(
                name      = "Walk-in Cooler 1",
                zone      = stationId,
                tempF     = legacyTemp,
                safeLabel = "Safe ≤ 41°F",
                isAlert   = isAlert,
                isWarning = isWarning,
            )
        )
    }
    return emptyList()
}

@Composable
private fun BentoGrid(
    cards: List<StationCardData>,
    timers: List<TimerState>,
    onCompleteTimer: (String) -> Unit = {},
) {
    val columns = when {
        cards.size <= 2 -> cards.size
        else            -> 3
    }
    // Station cards grid
    cards.chunked(columns).forEach { rowCards ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        ) {
            rowCards.forEach { card ->
                StationCard(card = card, modifier = Modifier.weight(1f))
            }
            repeat(columns - rowCards.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }

    // Timer cards below station grid
    if (timers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Hold Timers",
            color = OnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        timers.forEach { timer ->
            com.esper.foodsafety.ui.components.TimerWidget(
                timer = timer,
                onComplete = onCompleteTimer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun StationCard(card: StationCardData, modifier: Modifier = Modifier) {
    val leftBarColor = when {
        card.isAlert   -> ErrorColor
        card.isWarning -> TertiaryContainer
        else           -> PrimaryContainer
    }
    val glowColor = when {
        card.isAlert   -> ErrorColor.copy(alpha = 0.3f)
        card.isWarning -> TertiaryContainer.copy(alpha = 0.3f)
        else           -> PrimaryContainer.copy(alpha = 0.3f)
    }
    val tempColor = when {
        card.isAlert   -> ErrorColor
        card.isWarning -> TertiaryContainer
        else           -> OnSurface
    }
    val statusIcon = when {
        card.isAlert   -> "⚠"
        card.isWarning -> "⚠"
        else           -> "✓"
    }

    val animatedGlow = if (card.isAlert) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ), label = "glow"
        )
        glowColor.copy(alpha = glowAlpha)
    } else {
        glowColor.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .heightIn(min = 240.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = animatedGlow,
                spotColor = animatedGlow,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer)
            .border(
                width = 1.dp,
                color = SurfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(6.dp)
                .background(leftBarColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    // Live indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        PulseDot(color = Primary)
                        Text(
                            "LIVE",
                            color = Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.08.sp,
                        )
                    }
                    Text(card.name, color = OnSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(card.zone, color = OnSurfaceVariant, fontSize = 15.sp)
                }
                Text(
                    statusIcon,
                    color = leftBarColor,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Temperature display
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = card.tempF?.let { "%.0f".format(it) } ?: "--",
                        color = tempColor,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMono,
                        lineHeight = 80.sp,
                        letterSpacing = (-1.6).sp,
                    )
                    Text(
                        "°F",
                        color = OnSurfaceVariant,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val subColor = when {
                        card.isAlert || card.isWarning -> leftBarColor
                        else -> OnSurfaceVariant
                    }
                    Text("ℹ", color = subColor, fontSize = 14.sp)
                    Text(
                        if (card.isWarning) "${card.safeLabel} · approaching limit" else card.safeLabel,
                        color = subColor,
                        fontSize = 14.sp,
                    )
                }
            }

            // Timer pill (if present)
            if (card.timerLabel != null && card.timerDisplay != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryContainer.copy(alpha = 0.1f))
                        .border(1.dp, Primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(card.timerLabel, color = Primary, fontSize = 15.sp)
                    Text(card.timerDisplay, color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Floating Bottom Bar ───────────────────────────────────────────────────────

@Composable
private fun BottomFloatingBar(
    modifier: Modifier = Modifier,
    onStartTimer: () -> Unit,
    onMicPress: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        // Start Hold Timer button — bottom left
        Button(
            onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onStartTimer()
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("⏱", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text("Start Hold Timer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // Mic FAB — bottom center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Outer pulse ring
                val infiniteTransition = rememberInfiniteTransition(label = "mic-pulse")
                val ringAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ), label = "ring"
                )
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .border(4.dp, TertiaryContainer.copy(alpha = ringAlpha), CircleShape)
                )
                // Mic button
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer)
                        .border(4.dp, Background, CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onMicPress()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🎙", fontSize = 48.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceContainer.copy(alpha = 0.9f))
                    .border(1.dp, SurfaceVariant, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    "HOLD TO SPEAK",
                    color = OnSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.12.sp,
                )
            }
        }
    }
}

// ── Device Picker ─────────────────────────────────────────────────────────────

@Composable
private fun DevicePicker(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(
            "Select a Probe",
            color = OnSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { device ->
                @Suppress("MissingPermission")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainer)
                        .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
                        .clickable { onDeviceClick(device) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("🌡", fontSize = 24.sp)
                    Column {
                        Text(device.name ?: "Unnamed Device", color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(device.address, color = OnSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun PulseDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "dot-alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, color = OnSurfaceVariant, fontSize = 14.sp)
    }
}

