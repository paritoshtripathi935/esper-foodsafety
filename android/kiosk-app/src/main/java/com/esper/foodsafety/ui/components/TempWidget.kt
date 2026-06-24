package com.esper.foodsafety.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.ui.theme.*

private val JetBrainsMono = FontFamily.Monospace

@Composable
fun TempWidget(
    station: String,
    tempF: Float?,
    isAlert: Boolean,
    isWarning: Boolean = false,
    safeLabel: String = "Safe ≤ 41°F",
    modifier: Modifier = Modifier,
) {
    val leftBarColor = when {
        isAlert   -> ErrorColor
        isWarning -> TertiaryContainer
        else      -> PrimaryContainer
    }
    val glowColor = leftBarColor.copy(alpha = 0.3f)
    val tempColor = when {
        isAlert   -> ErrorColor
        isWarning -> TertiaryContainer
        else      -> OnSurface
    }

    val dotTransition = rememberInfiniteTransition(label = "tw-dot")
    val dotAlpha by dotTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot",
    )

    val animatedGlow = if (isAlert) {
        val glowTransition = rememberInfiniteTransition(label = "tw-pulse")
        val glowAlpha by glowTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glow",
        )
        glowColor.copy(alpha = glowAlpha)
    } else {
        glowColor.copy(alpha = 0.3f)
    }

    val stationName = station.replace('-', ' ').split(' ')
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    Box(
        modifier = modifier
            .heightIn(min = 200.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = animatedGlow,
                spotColor = animatedGlow,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer)
            .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(6.dp)
                .background(leftBarColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: LIVE dot + station name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = dotAlpha))
                )
                Text(
                    "LIVE",
                    color = Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                )
            }
            Text(stationName, color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            // Temperature
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = tempF?.let { "%.0f".format(it) } ?: "--",
                    color = tempColor,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMono,
                    lineHeight = 64.sp,
                    letterSpacing = (-1.2).sp,
                )
                Text(
                    "°F",
                    color = OnSurfaceVariant,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }

            // Safe label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val subColor = if (isAlert || isWarning) leftBarColor else OnSurfaceVariant
                Text("ℹ", color = subColor, fontSize = 12.sp)
                Text(
                    if (isWarning) "$safeLabel · approaching limit" else safeLabel,
                    color = subColor,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
