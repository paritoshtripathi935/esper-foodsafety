package com.esper.foodsafety.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.ai.ModelDownloader
import com.esper.foodsafety.ui.theme.*

@Composable
fun AIProcessingScreen() {
    val context  = LocalContext.current
    val onDevice = ModelDownloader.isDownloaded(context)
    val infiniteTransition = rememberInfiniteTransition(label = "ai-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "✨",
                fontSize = 72.sp,
                modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha),
            )
            Text(
                "Structuring your note…",
                color = Primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (onDevice) "On-device model — works offline" else "Cloud AI — analyzing your transcript",
                color = OnSurfaceVariant,
                fontSize = 16.sp,
            )
        }
    }
}
