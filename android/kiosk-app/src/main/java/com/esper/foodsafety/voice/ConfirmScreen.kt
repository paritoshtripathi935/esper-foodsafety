package com.esper.foodsafety.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.ui.theme.*

@Composable
fun ConfirmScreen(
    initialTranscript: String,
    isSending: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var transcript by remember(initialTranscript) { mutableStateOf(initialTranscript) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 32.dp),
        ) {
            // Header
            Text(
                "CONFIRM CORRECTIVE ACTION",
                color = Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Review before submitting",
                color = OnSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(24.dp))

            // AI structuring banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryContainer.copy(alpha = 0.12f))
                    .border(1.dp, Primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("✨", fontSize = 16.sp)
                Text(
                    "Audit narrative (AI) — structured from your transcript",
                    color = Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Transcript field
            FieldLabel("ORIGINAL TRANSCRIPT")
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = transcript,
                onValueChange = { transcript = it },
                enabled = !isSending,
                textStyle = TextStyle(color = OnSurface, fontSize = 16.sp, lineHeight = 24.sp),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainer)
                            .border(1.dp, Outline, RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.TopStart,
                    ) { inner() }
                },
            )

            Spacer(Modifier.height(20.dp))

            // Info note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ℹ", fontSize = 14.sp, color = Primary)
                Text(
                    "Added to today's HACCP record.",
                    color = OnSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(32.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled = !isSending,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceVariant),
                ) {
                    Text("⬅  Back / re-record", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                Button(
                    onClick = { onConfirm(transcript) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled = !isSending && transcript.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryContainer,
                        contentColor = OnPrimary,
                        disabledContainerColor = SurfaceVariant,
                        disabledContentColor = Outline,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = OnPrimary,
                        )
                    } else {
                        Text("✓  Submit to log", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = OnSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.08.sp,
    )
}
