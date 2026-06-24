package com.esper.foodsafety.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import com.esper.foodsafety.alerts.AlertEngine
import com.esper.foodsafety.ui.theme.*

@Composable
fun AlertBanner(
    alerts: List<AlertEngine.AlertState>,
    onLogAction: () -> Unit,
    onSilenceAlarm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val first = alerts.first()
    val stationDisplay = first.rule.station
        .replace('-', ' ')
        .split(' ')
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    // Pulse the error-color accent border for attention
    val infiniteTransition = rememberInfiniteTransition(label = "banner-pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "border-alpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ErrorContainer)
            .border(width = 2.dp, color = ErrorColor.copy(alpha = borderAlpha), shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: icon + message
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text("⚠", fontSize = 28.sp, color = ErrorColor)
            Column {
                Text(
                    text = "TEMPERATURE BREACH",
                    color = OnErrorContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                )
                Text(
                    text = "$stationDisplay · %.1f°F (limit %.0f°F)".format(
                        first.measuredTemp, first.rule.maxTempF
                    ),
                    color = OnErrorContainer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 26.sp,
                )
                if (alerts.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(OnErrorContainer.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "+${alerts.size - 1} more station${if (alerts.size > 2) "s" else ""}",
                            color = OnErrorContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Right: two buttons — outlined style on error-container background
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Log Corrective Action — filled tonal (inverted)
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLogAction()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnErrorContainer,
                    contentColor   = ErrorContainer,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text("Log Corrective Action", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Silence Alarm — outlined
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onSilenceAlarm()
                },
                border = BorderStroke(2.dp, OnErrorContainer),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnErrorContainer),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text("Silence Alarm", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
