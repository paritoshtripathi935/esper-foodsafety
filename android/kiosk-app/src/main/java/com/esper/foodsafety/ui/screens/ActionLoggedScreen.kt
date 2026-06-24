package com.esper.foodsafety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.esper.foodsafety.ui.theme.*

@Composable
fun ActionLoggedScreen(
    stationName: String = "Walk-in Cooler",
    onBack: () -> Unit,
) {
    val timeStr = remember {
        java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer.copy(alpha = 0.12f))
                    .border(3.dp, Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✓",
                    color = Primary,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                "Corrective action logged",
                color = OnSurface,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stationName, color = Primary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Logged at $timeStr", color = OnSurfaceVariant, fontSize = 14.sp)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryContainer,
                    contentColor = OnPrimary,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
            ) {
                Text("Return to Board", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
