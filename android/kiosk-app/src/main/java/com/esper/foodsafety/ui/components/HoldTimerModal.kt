package com.esper.foodsafety.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.esper.foodsafety.ui.theme.*

private val JetBrainsMono = FontFamily.Monospace

private val PRESETS = listOf(30, 60, 120, 240) // minutes

private val STATION_OPTIONS = listOf(
    "walk-in-cooler-1" to "Walk-in Cooler 1",
    "walk-in-cooler-2" to "Walk-in Cooler 2",
    "prep-table-1"     to "Prep Station 1",
    "prep-table-2"     to "Prep Station 2",
    "hot-hold-1"       to "Hot Hold Cabinet",
    "freezer-1"        to "Reach-in Freezer",
)

/**
 * Full-screen overlay modal matching the Stitch "Start Hold Timer Modal" design.
 * Max-width 640dp centered dialog with station picker, batch label field,
 * duration presets, and a custom +/- stepper.
 */
@Composable
fun HoldTimerModal(
    onStart: (batchLabel: String, holdMinutes: Int, station: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var batchLabel by remember { mutableStateOf("") }
    var holdMinutes by remember { mutableIntStateOf(60) }
    var selectedPreset by remember { mutableIntStateOf(60) }
    var station by remember { mutableStateOf(STATION_OPTIONS.first().first) }
    var stationMenuOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background.copy(alpha = 0.85f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            // Modal card — stop click propagation
            Column(
                modifier = Modifier
                    .width(640.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainer)
                    .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
                    .clickable(onClick = {}), // absorb clicks
            ) {
                // ── Header ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighest.copy(alpha = 0.3f))
                        .border(
                            width = 1.dp,
                            color = SurfaceVariant,
                            shape = RoundedCornerShape(0.dp),
                        )
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                ) {
                    Text(
                        "Start Hold Timer",
                        color = OnSurface,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 32.sp,
                    )
                }

                // ── Body ──────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Station selector
                    FieldSection(label = "STATION") {
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainerHigh)
                                    .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { stationMenuOpen = true }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text("🍽", fontSize = 20.sp)
                                    Text(
                                        STATION_OPTIONS.first { it.first == station }.second,
                                        color = OnSurface,
                                        fontSize = 18.sp,
                                    )
                                }
                                Text("▾", color = OnSurfaceVariant, fontSize = 18.sp)
                            }
                            DropdownMenu(
                                expanded = stationMenuOpen,
                                onDismissRequest = { stationMenuOpen = false },
                            ) {
                                STATION_OPTIONS.forEach { (id, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { station = id; stationMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }

                    // Batch label field
                    FieldSection(label = "BATCH LABEL") {
                        BasicTextField(
                            value = batchLabel,
                            onValueChange = { batchLabel = it },
                            textStyle = TextStyle(
                                color = OnSurface,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Default,
                            ),
                            decorationBox = { inner ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceContainerHigh)
                                        .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (batchLabel.isEmpty()) {
                                        Text(
                                            "e.g. Chicken — lunch",
                                            color = OnSurfaceVariant.copy(alpha = 0.5f),
                                            fontSize = 18.sp,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }

                    HorizontalDivider(color = SurfaceVariant)

                    // Duration section
                    FieldSection(label = "DURATION") {
                        // Preset buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PRESETS.forEach { preset ->
                                val isSelected = selectedPreset == preset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) Primary.copy(alpha = 0.1f)
                                            else SurfaceContainer
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 1.dp,
                                            color = if (isSelected) Primary else SurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                        .clickable {
                                            selectedPreset = preset
                                            holdMinutes = preset
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (preset < 60) "${preset}m" else "${preset / 60}h",
                                        color = if (isSelected) Primary else OnSurface,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Custom stepper
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainerHigh)
                                .border(1.dp, SurfaceVariant, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Decrement
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainer)
                                    .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable {
                                        holdMinutes = (holdMinutes - 10).coerceAtLeast(10)
                                        selectedPreset = -1
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("−", color = OnSurface, fontSize = 28.sp, fontWeight = FontWeight.Light)
                            }

                            // Time display
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val h = holdMinutes / 60
                                val m = holdMinutes % 60
                                Text(
                                    "%02d:%02d".format(h, m),
                                    color = OnSurface,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMono,
                                    letterSpacing = (-1).sp,
                                )
                                Text(
                                    "HR : MIN",
                                    color = Primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.1.sp,
                                )
                            }

                            // Increment
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainer)
                                    .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable {
                                        holdMinutes = (holdMinutes + 10).coerceAtMost(480)
                                        selectedPreset = -1
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+", color = OnSurface, fontSize = 28.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                }

                // ── Footer ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighest.copy(alpha = 0.2f))
                        .border(
                            width = 1.dp,
                            color = SurfaceVariant,
                            shape = RoundedCornerShape(0.dp),
                        )
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                ) {
                    // Cancel
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        modifier = Modifier.height(64.dp).widthIn(min = 140.dp),
                    ) {
                        Text("Cancel", fontSize = 18.sp)
                    }

                    // Start Timer
                    Button(
                        onClick = {
                            onStart(
                                batchLabel.ifBlank { "Batch" },
                                holdMinutes,
                                station,
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryContainer,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                        modifier = Modifier.height(64.dp).widthIn(min = 200.dp),
                    ) {
                        Text("▶  Start Timer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            color = OnSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.sp,
        )
        content()
    }
}
