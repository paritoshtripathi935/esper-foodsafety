package com.esper.foodsafety.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.esper.foodsafety.ui.theme.*
import com.esper.foodsafety.voice.ConfirmScreen
import com.esper.foodsafety.voice.STTManager
import com.esper.foodsafety.voice.STTState

@Composable
fun VoiceLogScreen(
    isSending: Boolean,
    onSubmit: (transcript: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val sttManager = remember { STTManager(context) }
    val sttState by sttManager.state.collectAsState()

    var showConfirm by remember { mutableStateOf(false) }
    var confirmedTranscript by remember { mutableStateOf("") }
    val sttAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var typeMode by remember { mutableStateOf(!sttAvailable) }
    var typedText by remember { mutableStateOf("") }
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
        if (granted) sttManager.startListening()
    }

    DisposableEffect(Unit) { onDispose { sttManager.destroy() } }

    LaunchedEffect(sttState) {
        val result = sttState
        if (result is STTState.Result) {
            confirmedTranscript = result.transcript
            showConfirm = true
        }
    }

    if (showConfirm) {
        ConfirmScreen(
            initialTranscript = confirmedTranscript,
            isSending = isSending,
            onConfirm = { text -> onSubmit(text) },
            onCancel = { showConfirm = false; sttManager.reset() },
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "LOG CORRECTIVE ACTION",
                        color = Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.sp,
                    )
                    Text(
                        "Voice Log",
                        color = OnSurface,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariant),
                ) {
                    Text("Cancel", fontSize = 15.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            if (!typeMode) {
                val isListening = sttState is STTState.Listening
                val partialText = (sttState as? STTState.Listening)?.partial ?: ""
                val isError = sttState is STTState.Error

                // Mic FAB with pulse ring
                Box(contentAlignment = Alignment.Center) {
                    if (isListening) {
                        val infiniteTransition = rememberInfiniteTransition(label = "mic-ring")
                        val ringScale by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.25f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ), label = "ring-scale"
                        )
                        Box(
                            modifier = Modifier
                                .size((120 * ringScale).dp)
                                .clip(CircleShape)
                                .background(ErrorColor.copy(alpha = 0.15f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(if (isListening) ErrorContainer else PrimaryContainer)
                            .border(
                                width = 4.dp,
                                color = if (isListening) ErrorColor else Primary,
                                shape = CircleShape,
                            )
                            .clickable {
                                if (!isListening) {
                                    if (micPermissionGranted) sttManager.startListening()
                                    else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    sttManager.stopListening()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isListening) "⏹" else "🎙",
                            fontSize = 44.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    when (sttState) {
                        is STTState.Listening -> "Listening — tap to stop"
                        is STTState.Error -> (sttState as STTState.Error).message
                        else -> "Tap to speak"
                    },
                    color = if (isError) ErrorColor else OnSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(32.dp))

                // Live transcript area
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainer)
                            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                    ) {
                        Column {
                            Text(
                                "LIVE TRANSCRIPT",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.08.sp,
                            )
                            if (partialText.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    partialText,
                                    color = OnSurface,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                )
                            }
                        }
                    }
                }

            } else {
                // Type-instead fallback
                Text(
                    "Type your corrective action",
                    color = OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                BasicTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    enabled = !isSending,
                    textStyle = TextStyle(color = OnSurface, fontSize = 16.sp, lineHeight = 24.sp),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainer)
                                .border(1.dp, Outline, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            if (typedText.isEmpty()) {
                                Text(
                                    "Describe what you did to address the alert…",
                                    color = OnSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 16.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            // Bottom action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Type instead / Use mic toggle
                TextButton(
                    onClick = { typeMode = !typeMode },
                    colors = ButtonDefaults.textButtonColors(contentColor = OnSurfaceVariant),
                ) {
                    Text(
                        if (typeMode) "⬅  Use mic" else "⌨  Type instead",
                        fontSize = 15.sp,
                    )
                }

                // Continue / Review button (only shown in type mode)
                if (typeMode) {
                    Button(
                        onClick = { confirmedTranscript = typedText; showConfirm = true },
                        enabled = typedText.isNotBlank() && !isSending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryContainer,
                            contentColor = OnPrimary,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text("Continue  →", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
