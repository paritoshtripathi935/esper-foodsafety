package com.esper.foodsafety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.alerts.AlertRule
import com.esper.foodsafety.ui.theme.*

@Composable
fun SettingsScreen(
    alertRules: List<AlertRule>,
    kioskModeEnabled: Boolean,
    supabaseConnected: Boolean,
    siteName: String,
    deviceId: String,
    onThresholdChange: (ruleId: String, newLimitF: Float) -> Unit,
    onKioskModeToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Background)
                .border(1.dp, SurfaceVariant, RoundedCornerShape(0.dp))
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariant),
            ) { Text("← Board", fontSize = 15.sp) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("SYSTEM SETTINGS", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
                Text("Configuration", color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Section 1: Stations & Thresholds ──────────────────────────────
            item {
                SectionHeader(icon = "🌡", title = "Stations & Thresholds")
            }
            items(alertRules.size) { i ->
                val rule = alertRules[i]
                var sliderVal by remember(rule.ruleId) { mutableFloatStateOf(rule.maxTempF) }

                val badgeLabel = when {
                    rule.station.contains("cooler", ignoreCase = true) -> "COLD STORAGE"
                    rule.station.contains("prep",   ignoreCase = true) -> "PREP AREA"
                    rule.station.contains("freeze", ignoreCase = true) -> "FROZEN"
                    rule.station.contains("hot",    ignoreCase = true) -> "WARMING"
                    else -> "STATION"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainer)
                        .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            rule.station.replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                            color = OnSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerHigh)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(badgeLabel, color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("MAX", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("%.0f°F".format(sliderVal), color = Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    // Stepper row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StepperButton("−") {
                            sliderVal = (sliderVal - 1f).coerceAtLeast(32f)
                            onThresholdChange(rule.ruleId, sliderVal)
                        }
                        Slider(
                            value = sliderVal,
                            onValueChange = { sliderVal = it },
                            onValueChangeFinished = { onThresholdChange(rule.ruleId, sliderVal) },
                            valueRange = 32f..165f,
                            colors = SliderDefaults.colors(
                                thumbColor = Primary,
                                activeTrackColor = PrimaryContainer,
                                inactiveTrackColor = SurfaceContainerHigh,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        StepperButton("+") {
                            sliderVal = (sliderVal + 1f).coerceAtMost(165f)
                            onThresholdChange(rule.ruleId, sliderVal)
                        }
                    }
                }
            }

            // ── Section 2: Kiosk Mode ──────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)); SectionHeader(icon = "🔒", title = "Kiosk Mode") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainer)
                        .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lock Task Mode", color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Prevent exit via Home/Recents. Disable: adb shell am task lock stop",
                                color = OnSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = kioskModeEnabled,
                            onCheckedChange = onKioskModeToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimary,
                                checkedTrackColor = PrimaryContainer,
                                uncheckedThumbColor = OnSurface,
                                uncheckedTrackColor = SurfaceContainerHigh,
                            ),
                        )
                    }
                }
            }

            // ── Section 3: Sync Status ─────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)); SectionHeader(icon = "☁", title = "Sync Status") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainer)
                        .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val syncHealth by com.esper.foodsafety.sync.SyncHealth.state.collectAsState()
                    val syncHealthy = syncHealth.lastSuccessMs?.let {
                        System.currentTimeMillis() - it < 120_000L
                    } ?: false
                    val syncDetail = if (syncHealthy) {
                        val ageS = ((System.currentTimeMillis() - (syncHealth.lastSuccessMs ?: 0L)) / 1000L)
                        "Synced ${ageS}s ago"
                    } else {
                        "Disconnected — ${syncHealth.pendingCount} pending"
                    }
                    StatusRow(
                        label = "SUPABASE SYNC",
                        healthy = syncHealthy,
                        detail = syncDetail,
                    )
                    HorizontalDivider(color = OutlineVariant)
                    StatusRow(label = "PROBE SYNC", healthy = true, detail = "Healthy")
                    HorizontalDivider(color = OutlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            MetaRow("SITE NAME", siteName)
                            MetaRow("DEVICE ID", deviceId)
                            MetaRow("APP VERSION", "V2.4.0")
                        }
                        Button(
                            onClick = { /* force sync */ },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh, contentColor = Primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("↺  Force Sync", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(icon: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Text(icon, fontSize = 16.sp)
        Text(title, color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceContainerHigh)
            .border(1.dp, SurfaceVariant, RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusRow(label: String, healthy: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (healthy) Color(0xFF5DDCAA) else ErrorColor)
            )
            Text(detail, color = if (healthy) Primary else ErrorColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = OnSurface, fontSize = 11.sp)
    }
}
