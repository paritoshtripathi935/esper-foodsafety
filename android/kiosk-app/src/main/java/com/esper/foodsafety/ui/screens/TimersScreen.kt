package com.esper.foodsafety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.esper.foodsafety.timers.TimerState
import com.esper.foodsafety.timers.TimerStatus
import com.esper.foodsafety.ui.components.HoldTimerModal
import com.esper.foodsafety.ui.components.TimerWidget
import com.esper.foodsafety.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TimersScreen(
    timers: StateFlow<List<TimerState>>,
    onStartTimer: (label: String, minutes: Int, station: String) -> Unit,
    onCompleteTimer: (String) -> Unit,
    activeStations: List<String>,
    onBack: () -> Unit,
) {
    val timerList by timers.collectAsState()
    var showTimerModal by remember { mutableStateOf(false) }

    if (showTimerModal) {
        HoldTimerModal(
            stations  = activeStations,
            onStart   = { label, mins, station ->
                onStartTimer(label, mins, station)
                showTimerModal = false
            },
            onDismiss = { showTimerModal = false },
        )
    }

    Row(modifier = Modifier.fillMaxSize().background(Background)) {
        TimersSideNav(onBack = onBack, modifier = Modifier.zIndex(5f))

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
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
                Text(
                    "Hold Timers",
                    color = OnSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = { showTimerModal = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor   = OnPrimary,
                    ),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text("⏱", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Timer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            if (timerList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("⏱", fontSize = 48.sp)
                        Text(
                            "No active hold timers.",
                            color = OnSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Start one to track a hot-hold or cooling window.",
                            color = OnSurfaceVariant,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showTimerModal = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryContainer,
                                contentColor   = OnPrimary,
                            ),
                            modifier = Modifier.height(52.dp),
                        ) {
                            Text("Start Hold Timer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                val running   = timerList.filter { it.status == TimerStatus.RUNNING }
                val breached  = timerList.filter { it.status == TimerStatus.BREACHED }
                val completed = timerList.filter { it.status == TimerStatus.COMPLETE }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (running.isNotEmpty()) {
                        item { SectionHeader("Running", running.size) }
                        items(running, key = { it.timerId }) { timer ->
                            TimerWidget(timer = timer, onComplete = onCompleteTimer, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (breached.isNotEmpty()) {
                        item { Spacer(Modifier.height(4.dp)); SectionHeader("Needs Attention", breached.size, isError = true) }
                        items(breached, key = { it.timerId }) { timer ->
                            TimerWidget(timer = timer, onComplete = onCompleteTimer, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (completed.isNotEmpty()) {
                        item { Spacer(Modifier.height(4.dp)); SectionHeader("Completed", completed.size) }
                        items(completed, key = { it.timerId }) { timer ->
                            TimerWidget(timer = timer, onComplete = onCompleteTimer, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int, isError: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Text(
            label,
            color = if (isError) ErrorColor else OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isError) ErrorContainer else SurfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                count.toString(),
                color = if (isError) ErrorColor else OnSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TimersSideNav(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(SurfaceContainer)
            .border(width = 1.dp, color = SurfaceVariant, shape = RoundedCornerShape(0.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

        TimersNavItem(icon = "⬛", label = "Board",    active = false, onClick = onBack)
        TimersNavItem(icon = "⏱",  label = "Timers",   active = true,  onClick = {})
        TimersNavItem(icon = "📋", label = "History",  active = false, onClick = onBack)
        TimersNavItem(icon = "📊", label = "Reports",  active = false, onClick = onBack)

        Spacer(Modifier.weight(1f))

        TimersNavItem(icon = "🔗", label = "Pairing",  active = false, onClick = onBack)
        TimersNavItem(icon = "⚙",  label = "Settings", active = false, onClick = onBack)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimersNavItem(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) SurfaceContainerHighest else Color.Transparent
    val fg = if (active) Primary else OnSurfaceVariant.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(bg)
            .drawBehind {
                if (active) drawLine(
                    color       = Primary,
                    start       = Offset(0f, 0f),
                    end         = Offset(0f, size.height),
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
