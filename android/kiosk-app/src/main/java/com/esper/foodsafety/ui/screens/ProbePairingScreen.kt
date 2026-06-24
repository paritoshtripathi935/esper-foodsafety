package com.esper.foodsafety.ui.screens

import android.bluetooth.BluetoothDevice
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

private val STATION_OPTIONS = listOf(
    "walk-in-cooler-1" to "Walk-in Cooler 1",
    "walk-in-cooler-2" to "Walk-in Cooler 2",
    "prep-table-1"     to "Prep Station 1",
    "prep-table-2"     to "Prep Station 2",
    "hot-hold-1"       to "Hot Hold Cabinet",
    "freezer-1"        to "Reach-in Freezer",
)

@Composable
fun ProbePairingScreen(
    discoveredDevices: StateFlow<List<BluetoothDevice>>,
    onPair: (device: BluetoothDevice, station: String, limitF: Float, mode: String) -> Unit,
    onBack: () -> Unit,
) {
    val devices by discoveredDevices.collectAsState()
    var selected by remember { mutableStateOf<BluetoothDevice?>(null) }
    var networkMode by remember { mutableStateOf(false) }
    var station by remember { mutableStateOf("walk-in-cooler-1") }
    var limitF by remember { mutableFloatStateOf(41f) }
    var readingMode by remember { mutableStateOf("ble") }

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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariant),
                ) { Text("← Board", fontSize = 15.sp) }
                Column {
                    Text("PROBE PAIRING", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
                    Text("Pair a Probe", color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left: Probe discovery list ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(width = 1.dp, color = SurfaceVariant, shape = RoundedCornerShape(0.dp))
                    .padding(24.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    // Animated scanning dot
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Primary))
                    Text("Discovering probes", color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                if (devices.isEmpty()) {
                    Text("Scanning… no probes found yet.", color = OnSurfaceVariant, fontSize = 14.sp)
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices) { device ->
                        @Suppress("MissingPermission")
                        val isSelected = selected?.address == device.address && !networkMode
                        ProbeListItem(
                            icon = "📶",
                            title = device.name ?: "Unnamed Probe",
                            subtitle = "Bluetooth · ${device.address}",
                            isSelected = isSelected,
                            onClick = { selected = device; networkMode = false },
                        )
                    }
                    item {
                        ProbeListItem(
                            icon = "🌐",
                            title = "Esper VM (network probe)",
                            subtitle = "lan · Posts directly to Supabase",
                            isSelected = networkMode,
                            onClick = { selected = null; networkMode = true },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Searching for more devices nearby…", color = OnSurfaceVariant, fontSize = 12.sp)
            }

            // ── Right: Assign panel ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Assign Probe", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                // Station chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel("STATION")
                    Spacer(Modifier.height(4.dp))
                    STATION_OPTIONS.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (id, label) ->
                                val isSelected = station == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) PrimaryContainer.copy(alpha = 0.15f)
                                            else SurfaceContainerHigh
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Primary else SurfaceVariant,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable { station = id }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        label,
                                        color = if (isSelected) Primary else OnSurface,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                // Safe limit stepper
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("SAFE LIMIT")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StepperIconButton("−") { limitF = (limitF - 1f).coerceAtLeast(32f) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerHigh)
                                .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Safe ≤ %.0f°F".format(limitF), color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        StepperIconButton("+") { limitF = (limitF + 1f).coerceAtMost(165f) }
                    }
                }

                // Reading mode toggle
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("READING MODE")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ble" to "BLE GATT", "network" to "Network").forEach { (id, label) ->
                            val isSelected = readingMode == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) PrimaryContainer.copy(alpha = 0.15f)
                                        else SurfaceContainerHigh
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Primary else SurfaceVariant,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { readingMode = id }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) Primary else OnSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Action row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = onBack,
                        colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariant),
                    ) { Text("Cancel", fontSize = 15.sp) }

                    Button(
                        onClick = {
                            if (networkMode) {
                                devices.firstOrNull()?.let { d -> onPair(d, station, limitF, "network") }
                            } else {
                                selected?.let { d -> onPair(d, station, limitF, "ble") }
                            }
                        },
                        enabled = selected != null || networkMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryContainer,
                            contentColor = OnPrimary,
                            disabledContainerColor = SurfaceVariant,
                            disabledContentColor = Outline,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    ) {
                        Text("Pair & Assign", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeListItem(
    icon: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) PrimaryContainer.copy(alpha = 0.12f) else SurfaceContainerHigh)
            .border(
                1.dp,
                if (isSelected) Primary else SurfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, fontSize = 20.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = OnSurfaceVariant, fontSize = 12.sp)
        }
        if (isSelected) {
            Text("✓", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
}

@Composable
private fun StepperIconButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerHigh)
            .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
