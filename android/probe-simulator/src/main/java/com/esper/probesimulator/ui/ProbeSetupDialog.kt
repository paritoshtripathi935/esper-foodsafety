package com.esper.probesimulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.esper.probesimulator.network.NetworkPoster
import com.esper.probesimulator.BuildConfig

data class ProbeIdentity(
    val stationId: String,   // e.g. "walk-in-cooler-1"
    val deviceId: String,    // e.g. "probe-sim-01"
    val siteId: String,      // e.g. "site-eastgate"
)

private val STATION_PRESETS = listOf(
    "walk-in-cooler-1" to "Walk-in Cooler 1",
    "walk-in-cooler-2" to "Walk-in Cooler 2",
    "prep-table-1"     to "Prep Table 1",
    "prep-table-2"     to "Prep Table 2",
    "hot-hold-1"       to "Hot Hold Cabinet",
    "custom"           to "Custom…",
)

@Composable
fun ProbeSetupDialog(
    initial: ProbeIdentity?,
    onConfirm: (ProbeIdentity) -> Unit,
) {
    var selectedStation by remember {
        mutableStateOf(
            if (initial != null && STATION_PRESETS.any { it.first == initial.stationId })
                initial.stationId
            else if (initial != null) "custom"
            else "walk-in-cooler-1"
        )
    }
    var customStation by remember {
        mutableStateOf(
            if (initial != null && !STATION_PRESETS.dropLast(1).any { it.first == initial.stationId })
                initial.stationId
            else ""
        )
    }
    var deviceId by remember { mutableStateOf(initial?.deviceId ?: "probe-sim-01") }

    // Site picker state
    var sites by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var sitesLoading by remember { mutableStateOf(true) }
    var selectedSiteId by remember { mutableStateOf(initial?.siteId ?: "") }

    LaunchedEffect(Unit) {
        val fetched = NetworkPoster.fetchSites(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            anonKey     = BuildConfig.SUPABASE_ANON_KEY,
        )
        sites = fetched
        sitesLoading = false
        // Auto-select first site if nothing was pre-set
        if (selectedSiteId.isBlank() && fetched.isNotEmpty()) {
            selectedSiteId = fetched.first().first
        }
    }

    Dialog(
        onDismissRequest = { /* not dismissable without confirming */ },
        properties = DialogProperties(
            dismissOnBackPress    = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.95f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A2027))
                .border(1.dp, Color(0xFF2F353D), RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Title
            Text(
                "Probe Identity",
                color = Color(0xFFDDE3ED),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Choose which site and station this simulator reports to.",
                color = Color(0xFFBCCAC0),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            // ── Site picker ──────────────────────────────────────────────
            Label("SITE")
            when {
                sitesLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF5DDCAA),
                        )
                        Text("Loading sites…", color = Color(0xFFBCCAC0), fontSize = 14.sp)
                    }
                }
                sites.isEmpty() -> {
                    Text(
                        "No sites found — register a kiosk first.",
                        color = Color(0xFFFFB4AB),
                        fontSize = 14.sp,
                    )
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sites.forEach { (id, name) ->
                            val isSelected = selectedSiteId == id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFF0CA678).copy(alpha = 0.15f)
                                        else Color(0xFF242A32)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF0CA678) else Color(0xFF2F353D),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable { selectedSiteId = id }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(name, color = Color(0xFFDDE3ED), fontSize = 16.sp)
                                    Text(
                                        id,
                                        color = Color(0xFF86948B),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    )
                                }
                                if (isSelected) Text("✓", color = Color(0xFF5DDCAA), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Station picker ───────────────────────────────────────────
            Label("STATION")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                STATION_PRESETS.forEach { (id, label) ->
                    val isSelected = selectedStation == id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color(0xFF0CA678).copy(alpha = 0.15f)
                                else Color(0xFF242A32)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF0CA678) else Color(0xFF2F353D),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { selectedStation = id }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, color = Color(0xFFDDE3ED), fontSize = 16.sp)
                        if (isSelected) Text("✓", color = Color(0xFF5DDCAA), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (selectedStation == "custom") {
                    FieldInput(
                        value = customStation,
                        onValueChange = { customStation = it },
                        placeholder = "e.g. reach-in-freezer-1",
                    )
                }
            }

            // ── Device ID field ──────────────────────────────────────────
            Label("DEVICE ID")
            FieldInput(
                value = deviceId,
                onValueChange = { deviceId = it },
                placeholder = "e.g. probe-sim-02",
            )

            // Confirm button
            val effectiveStation = if (selectedStation == "custom") customStation.trim() else selectedStation
            val canConfirm = effectiveStation.isNotBlank()
                && deviceId.trim().isNotBlank()
                && selectedSiteId.isNotBlank()

            Button(
                onClick = {
                    if (canConfirm) onConfirm(ProbeIdentity(
                        stationId = effectiveStation,
                        deviceId  = deviceId.trim(),
                        siteId    = selectedSiteId,
                    ))
                },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor          = Color(0xFF0CA678),
                    contentColor            = Color.White,
                    disabledContainerColor  = Color(0xFF2F353D),
                    disabledContentColor    = Color(0xFF86948B),
                ),
            ) {
                Text("Start Simulator", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = Color(0xFFBCCAC0),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp,
    )
}

@Composable
private fun FieldInput(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = Color(0xFFDDE3ED), fontSize = 16.sp),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF242A32))
                    .border(1.dp, Color(0xFF2F353D), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, color = Color(0xFF86948B), fontSize = 16.sp)
                inner()
            }
        },
    )
}
