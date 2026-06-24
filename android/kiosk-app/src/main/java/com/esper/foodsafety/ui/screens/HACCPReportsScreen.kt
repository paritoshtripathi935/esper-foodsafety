package com.esper.foodsafety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.data.remote.EventsRepository
import com.esper.foodsafety.data.remote.RemoteEvent
import com.esper.foodsafety.ui.theme.*

@Composable
fun HACCPReportsScreen(onBack: () -> Unit) {
    var allEvents by remember { mutableStateOf<List<RemoteEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = EventsRepository.fetchEventsResult(limit = 500)
        result.fold(
            onSuccess = { allEvents = it },
            onFailure = { allEvents = emptyList(); fetchError = it.message ?: "Unknown error" },
        )
        loading = false
    }

    val byDate = allEvents.groupBy { it.ts.take(10) }.entries.sortedByDescending { it.key }

    if (selectedDate != null) {
        val dateEvents = byDate.firstOrNull { it.key == selectedDate }?.value ?: emptyList()
        DayDetailView(
            date = selectedDate!!,
            events = dateEvents,
            onBack = { selectedDate = null },
        )
        return
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
                    Text("HACCP REPORTS", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
                    Text("Daily Compliance", color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("${byDate.size} days", color = OnSurfaceVariant, fontSize = 14.sp)
        }

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
            byDate.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data yet.", color = OnSurfaceVariant, fontSize = 16.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(byDate) { (date, events) ->
                    val breaches = events.count { it.type == "alert" }
                    val actions  = events.count { it.type == "corrective_action" }
                    val isCompliant = breaches == 0

                    val (cardBg, leftBarColor) = if (isCompliant)
                        SurfaceContainer to PrimaryContainer
                    else
                        ErrorContainer.copy(alpha = 0.15f).let { it to ErrorColor }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(
                                1.dp,
                                if (isCompliant) SurfaceVariant else ErrorColor.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedDate = date }
                    ) {
                        // Left accent bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(5.dp)
                                .background(leftBarColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 21.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(date, color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${events.size} events · $breaches breach${if (breaches != 1) "es" else ""} · $actions action${if (actions != 1) "s" else ""}",
                                    color = OnSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                            // Status badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isCompliant) PrimaryContainer.copy(alpha = 0.15f) else ErrorContainer)
                                    .border(
                                        1.dp,
                                        if (isCompliant) Primary.copy(alpha = 0.4f) else ErrorColor.copy(alpha = 0.4f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    if (isCompliant) "✓  Compliant" else "⚠  Needs Review",
                                    color = if (isCompliant) Primary else ErrorColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetailView(date: String, events: List<RemoteEvent>, onBack: () -> Unit) {
    val ctx     = LocalContext.current
    val alerts  = events.filter { it.type == "alert" }
    val actions = events.filter { it.type == "corrective_action" }
    val temps   = events.filter { it.type == "temp" }

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
                ) { Text("← Reports", fontSize = 15.sp) }
                Column {
                    Text("DAILY SUMMARY", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
                    Text(date, color = OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // Export Report — opens PDF endpoint in a Chrome custom tab
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerHigh)
                    .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp))
                    .clickable {
                        val url = "${com.esper.foodsafety.BuildConfig.AI_API_BASE.trimEnd('/')}/reports/$date.pdf"
                        CustomTabsIntent.Builder().build().launchUrl(ctx, Uri.parse(url))
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↓", color = OnSurfaceVariant, fontSize = 14.sp)
                Text("Export Report", color = OnSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Temp summary
            if (temps.isNotEmpty()) {
                item {
                    SectionCard(icon = "🌡", title = "Temperature Log", subtitle = "${temps.size} readings today") {
                        Spacer(Modifier.height(4.dp))
                        Text("${temps.size} readings recorded across all stations.", color = OnSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }

            // Breaches
            if (alerts.isNotEmpty()) {
                item {
                    SectionCard(icon = "⚠", title = "Breaches", subtitle = "${alerts.size} detected", accentColor = ErrorColor) {
                        alerts.forEach { alert ->
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ErrorContainer.copy(alpha = 0.4f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    alert.station.replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                                    color = OnErrorContainer,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "%.1f°F · ${alert.ts.substring(11, 16)}".format(alert.value.toDoubleOrNull() ?: 0.0),
                                    color = ErrorColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Corrective actions
            if (actions.isNotEmpty()) {
                item {
                    SectionCard(icon = "✓", title = "Corrective Actions", subtitle = "${actions.size} logged", accentColor = Primary) {
                        actions.forEach { action ->
                            val actionTaken = action.payload.optString("action_taken", action.value)
                            val severity    = action.payload.optString("severity", "")
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryContainer.copy(alpha = 0.08f))
                                    .border(1.dp, Primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                            ) {
                                Text(action.ts.substring(11, 16), color = OnSurfaceVariant, fontSize = 11.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(actionTaken, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                                if (severity.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Severity: ${severity.uppercase()}", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Sign-off
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainer)
                        .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                ) {
                    Text("SIGN-OFF", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Manager Name", color = OnSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = OnSurfaceVariant.copy(alpha = 0.4f))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Digital Signature", color = OnSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = OnSurfaceVariant.copy(alpha = 0.4f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ℹ", color = OnSurfaceVariant, fontSize = 13.sp)
                        Text("PDF export available on the web dashboard.", color = OnSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    icon: String,
    title: String,
    subtitle: String,
    accentColor: Color = Color(0xFF5DDCAA),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer)
            .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(icon, fontSize = 18.sp)
            Column {
                Text(title, color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = accentColor, fontSize = 12.sp)
            }
        }
        content()
    }
}
