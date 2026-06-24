package com.esper.foodsafety.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.esper.foodsafety.timers.TimerState
import com.esper.foodsafety.timers.TimerStatus
import com.esper.foodsafety.ui.theme.*

private val JetBrainsMono = FontFamily.Monospace

/**
 * Bento-style timer card matching the Stitch "Hold Timers" design.
 * Shows a circular progress ring with remaining time, batch label, and station chip.
 */
@Composable
fun TimerWidget(
    timer: TimerState,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = timer.holdMinutes * 60
    val progress = if (totalSeconds > 0)
        timer.remainingSeconds.toFloat() / totalSeconds.toFloat()
    else 0f

    val accentColor = when (timer.status) {
        TimerStatus.BREACHED -> ErrorColor
        TimerStatus.COMPLETE -> Primary
        TimerStatus.RUNNING  -> if (progress < 0.15f) TertiaryContainer else PrimaryContainer
    }

    val statusLabel = when (timer.status) {
        TimerStatus.RUNNING  -> if (progress < 0.15f) "NEARING LIMIT" else "SAFE HOLD"
        TimerStatus.BREACHED -> "HOLD BREACHED"
        TimerStatus.COMPLETE -> "COMPLETED"
    }

    val mins = timer.remainingSeconds / 60
    val secs = timer.remainingSeconds % 60
    val timeDisplay = "%02d:%02d".format(mins, secs)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer)
            .border(width = 1.dp, color = SurfaceVariant, shape = RoundedCornerShape(12.dp)),
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(5.dp)
                .background(accentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 21.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
        // Left: labels
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                statusLabel,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.sp,
            )
            Text(
                timer.batchLabel,
                color = OnSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            // Station chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("❄", fontSize = 12.sp)
                Text(
                    timer.station.replace('-', ' ').split(' ')
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.06.sp,
                )
            }

            if (timer.status != TimerStatus.RUNNING) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { onComplete(timer.timerId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (timer.status == TimerStatus.BREACHED) ErrorContainer else SurfaceContainerHigh,
                        contentColor = if (timer.status == TimerStatus.BREACHED) ErrorColor else Primary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (timer.status == TimerStatus.BREACHED) "Dismiss" else "Done",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        Spacer(Modifier.width(24.dp))

        // Right: circular countdown
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressRing(
                progress = progress,
                trackColor = SurfaceVariant,
                progressColor = accentColor,
                strokeWidth = 12.dp,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    timeDisplay,
                    color = if (timer.status == TimerStatus.BREACHED) ErrorColor else OnSurface,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMono,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    "REMAINING",
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                )
            }
        }
    }
    } // end outer Box
}

@Composable
private fun CircularProgressRing(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val startAngle = -90f
        val sweep = 360f * progress.coerceIn(0f, 1f)

        // Track
        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
        )
        // Progress
        if (sweep > 0f) {
            drawArc(
                color = progressColor,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = stroke,
            )
        }
    }
}
