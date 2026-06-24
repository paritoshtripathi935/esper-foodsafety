package com.esper.foodsafety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.alerts.AlertEngine
import com.esper.foodsafety.ui.theme.*

@Composable
fun AlertScreen(
    alerts: List<AlertEngine.AlertState>,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "ACTIVE ALERTS",
                    color = ErrorColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                )
                Text(
                    "Temperature Breaches",
                    color = OnSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceVariant,
                    contentColor   = OnSurface,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No active alerts.",
                    color = OnSurfaceVariant,
                    fontSize = 18.sp,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(alerts) { alert ->
                    val stationDisplay = alert.rule.station
                        .replace('-', ' ')
                        .split(' ')
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ErrorContainer)
                            .border(1.dp, ErrorColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        // Left accent bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(5.dp)
                                .background(
                                    ErrorColor,
                                    RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 21.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "TEMPERATURE BREACH",
                                    color = ErrorColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.08.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stationDisplay,
                                    color = OnErrorContainer,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "%.1f°F".format(alert.measuredTemp),
                                    color = ErrorColor,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "limit %.0f°F".format(alert.rule.maxTempF),
                                    color = OnErrorContainer.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
