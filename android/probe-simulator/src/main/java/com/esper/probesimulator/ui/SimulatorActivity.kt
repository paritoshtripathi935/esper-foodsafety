package com.esper.probesimulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.esper.probesimulator.scenarios.Scenario
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SimulatorContent(
    temperature: StateFlow<Float>,
    activeScenario: StateFlow<Scenario?>,
    networkMode: Boolean,
    lastStatus: String,
    isSending: Boolean,
    probeIdentity: ProbeIdentity?,
    onModeToggle: (Boolean) -> Unit,
    onStartScenario: (Scenario) -> Unit,
    onStopScenario: () -> Unit,
    onManualTempChange: (Float) -> Unit,
    onChangeIdentity: () -> Unit = {},
    onNetworkSend: () -> Unit = {},
) {
    val temp     by temperature.collectAsState()
    val scenario by activeScenario.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "BLE Probe Simulator",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            // ── Probe identity chip ──────────────────────────────────────
            if (probeIdentity != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF0CA678).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF0CA678).copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🌡", fontSize = 14.sp)
                    Text(
                        probeIdentity.siteId,
                        color = Color(0xFFBCCAC0),
                        fontSize = 13.sp,
                    )
                    Text("·", color = Color(0xFF86948B), fontSize = 13.sp)
                    Text(
                        probeIdentity.stationId,
                        color = Color(0xFF5DDCAA),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("·", color = Color(0xFF86948B), fontSize = 13.sp)
                    Text(
                        probeIdentity.deviceId,
                        color = Color(0xFFBCCAC0),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick = onChangeIdentity,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            "Change",
                            color = Color(0xFF5DDCAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Mode toggle ───────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BLE", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = networkMode, onCheckedChange = onModeToggle)
                Spacer(Modifier.width(8.dp))
                Text("Network (Supabase)", style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                lastStatus,
                style = MaterialTheme.typography.bodySmall,
                color = if (lastStatus.contains("✓") || lastStatus == "BLE advertising")
                    Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "%.1f°F".format(temp),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = if (temp > 41f) Color(0xFFB71C1C) else Color(0xFF0CA678),
            )

            Spacer(Modifier.height(12.dp))

            ScenarioSelector(
                activeScenario = scenario,
                onStart = onStartScenario,
                onStop = onStopScenario,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            if (scenario == null) {
                Text("Manual temperature", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = temp,
                    onValueChange = onManualTempChange,
                    valueRange = 0f..120f,
                )
            }
        }
    }
}
