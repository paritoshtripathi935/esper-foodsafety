package com.esper.foodsafety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.foodsafety.sync.DeviceIdentity
import com.esper.foodsafety.ui.theme.*

@Composable
fun SetupScreen(
    isRegistering: Boolean,
    error: String?,
    onConfirm: (siteName: String, deviceName: String) -> Unit,
) {
    var siteName   by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    val deviceFocus = remember { FocusRequester() }

    val siteSlug   = if (siteName.isNotBlank())
        DeviceIdentity.toSlug(siteName, "site") else "site-…"
    val deviceSlug = if (deviceName.isNotBlank())
        DeviceIdentity.toSlug(deviceName, "device") else "device-…"

    val canSubmit = siteName.isNotBlank() && deviceName.isNotBlank() && !isRegistering

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceContainer)
                .border(1.dp, SurfaceVariant, RoundedCornerShape(16.dp))
                .padding(horizontal = 36.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "FIRST-TIME SETUP",
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                )
                Text(
                    "Register this kiosk",
                    color = OnSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "This tablet will appear in the SafeTemp dashboard under the site you enter here.",
                    color = OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }

            // Site name field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SITE NAME", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
                OutlinedTextField(
                    value = siteName,
                    onValueChange = { siteName = it },
                    placeholder = { Text("e.g. Westwood Bistro", color = OnSurfaceVariant) },
                    singleLine = true,
                    enabled = !isRegistering,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { deviceFocus.requestFocus() }),
                    colors = outlinedFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "ID: $siteSlug",
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }

            // Device name field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DEVICE NAME", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp)
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    placeholder = { Text("e.g. Line manager tablet", color = OnSurfaceVariant) },
                    singleLine = true,
                    enabled = !isRegistering,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) onConfirm(siteName.trim(), deviceName.trim()) }),
                    colors = outlinedFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(deviceFocus),
                )
                Text(
                    "ID: $deviceSlug",
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }

            // Error
            if (error != null) {
                Text(
                    "Registration failed: $error",
                    color = ErrorColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            // Confirm button
            Button(
                onClick = { onConfirm(siteName.trim(), deviceName.trim()) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryContainer,
                    contentColor   = OnPrimary,
                    disabledContainerColor = SurfaceContainerHigh,
                    disabledContentColor   = OnSurfaceVariant,
                ),
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Registering…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Register & Continue", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary,
    unfocusedBorderColor = SurfaceVariant,
    focusedTextColor     = OnSurface,
    unfocusedTextColor   = OnSurface,
    cursorColor          = Primary,
    focusedContainerColor   = SurfaceContainerHigh,
    unfocusedContainerColor = SurfaceContainerHigh,
)
