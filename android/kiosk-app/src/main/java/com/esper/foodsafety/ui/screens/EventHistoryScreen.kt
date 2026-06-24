package com.esper.foodsafety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.data.remote.EventsRepository
import com.esper.foodsafety.data.remote.RemoteEvent
import com.esper.foodsafety.ui.theme.*

private val JetBrainsMono = FontFamily.Monospace

@Composable
fun EventHistoryScreen(onBack: () -> Unit) {
    var events by remember { mutableStateOf<List<RemoteEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var activeFilter by remember { mutableStateOf("All") }

    val filters = listOf("All", "alert", "corrective_action", "timer", "temp")

    LaunchedEffect(activeFilter) {
        loading = true
        fetchError = null
        val result = EventsRepository.fetchEventsResult(
            type = if (activeFilter == "All") null else activeFilter,
            limit = 200,
        )
        result.fold(
            onSuccess = { events = it },
            onFailure = { events = emptyList(); fetchError = it.message ?: "Unknown error" },
        )
        loading = false
    }

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
                .border(width = 1.dp, color = SurfaceVariant, shape = RoundedCornerShape(0.dp))
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
                    Text("EVENT HISTORY", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
                    Text("Audit Log", color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("${events.size} events", color = OnSurfaceVariant, fontSize = 14.sp)
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainer)
                .border(width = 1.dp, color = SurfaceVariant, shape = RoundedCornerShape(0.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            filters.forEach { f ->
                val isSelected = activeFilter == f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) PrimaryContainer.copy(alpha = 0.2f)
                            else SurfaceContainerHigh
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Primary else SurfaceVariant,
                            shape = RoundedCornerShape(50),
                        )
                        .clickable { activeFilter = f }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        filterLabel(f),
                        color = if (isSelected) Primary else OnSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        // Content
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
            }
            fetchError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", fontSize = 36.sp, color = ErrorColor)
                    Spacer(Modifier.height(8.dp))
                    Text("Could not reach Supabase", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(fetchError!!, color = OnSurfaceVariant, fontSize = 13.sp)
                }
            }
            events.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No events found.", color = OnSurfaceVariant, fontSize = 16.sp)
            }
            else -> {
                // Column headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainer)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableHeaderCell("TIME",    Modifier.weight(1.8f))
                    TableHeaderCell("STATION", Modifier.weight(2f))
                    TableHeaderCell("TYPE",    Modifier.weight(1.8f))
                    TableHeaderCell("DETAIL",  Modifier.weight(3f))
                    TableHeaderCell("SEVERITY", Modifier.weight(1.5f))
                }
                HorizontalDivider(color = OutlineVariant, thickness = 1.dp)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(events) { event ->
                        EventRow(event)
                        HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: RemoteEvent) {
    val (badgeBg, badgeText) = when (event.type) {
        "alert"             -> ErrorContainer         to ErrorColor
        "corrective_action" -> Color(0xFF1A3A6B)      to Color(0xFF93C5FD)
        "timer"             -> Color(0xFF3A2B00)       to TertiaryContainer
        else                -> SurfaceContainerHigh   to OnSurfaceVariant
    }

    val severity = event.payload.optString("severity", "")
    val (severityColor, severityIcon) = when (severity.lowercase()) {
        "critical" -> ErrorColor to "🔴"
        "high"     -> TertiaryContainer to "🟠"
        "medium"   -> Color(0xFFFFD580) to "🟡"
        else       -> OnSurfaceVariant to "—"
    }

    val ts = event.ts.take(19).replace('T', ' ')
    val detail = when (event.type) {
        "temp"              -> "%.1f°F".format(event.value.toDoubleOrNull() ?: 0.0)
        "corrective_action" -> event.payload.optString("action_taken", event.value).take(60)
        "alert"             -> "Breach %.1f°F".format(event.value.toDoubleOrNull() ?: 0.0)
        "timer"             -> event.payload.optString("batch_label", event.value)
        else                -> event.value.take(40)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (event.type == "alert") ErrorContainer.copy(alpha = 0.08f) else Color.Transparent
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ts, color = OnSurfaceVariant, fontSize = 12.sp, fontFamily = JetBrainsMono, modifier = Modifier.weight(1.8f))

        Text(
            event.station.replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
            color = OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(2f),
        )

        // Type badge
        Box(
            modifier = Modifier
                .weight(1.8f)
                .clip(RoundedCornerShape(4.dp))
                .background(badgeBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                filterLabel(event.type),
                color = badgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04.sp,
            )
        }

        Text(detail, color = OnSurface, fontSize = 13.sp, modifier = Modifier.weight(3f), maxLines = 2)

        Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(severityIcon, fontSize = 12.sp)
            if (severity.isNotEmpty()) {
                Text(severity.uppercase(), color = severityColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(text, color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp, modifier = modifier)
}

private fun filterLabel(type: String) = when (type) {
    "corrective_action" -> "ACTION"
    "alert"             -> "ALERT"
    "timer"             -> "TIMER"
    "temp"              -> "TEMP"
    else                -> type.uppercase()
}
