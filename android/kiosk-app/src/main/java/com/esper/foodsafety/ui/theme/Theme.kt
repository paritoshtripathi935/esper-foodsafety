package com.esper.foodsafety.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background              = Color(0xFF0E141B)
val SurfaceContainer        = Color(0xFF1A2027)
val SurfaceContainerHigh    = Color(0xFF242A32)
val SurfaceContainerHighest = Color(0xFF2F353D)
val SurfaceVariant          = Color(0xFF2F353D)
val Primary                 = Color(0xFF5DDCAA)
val PrimaryContainer        = Color(0xFF0CA678)
val OnPrimary               = Color(0xFF003826)
val OnSurface               = Color(0xFFDDE3ED)
val OnSurfaceVariant        = Color(0xFFBCCAC0)
val TertiaryContainer       = Color(0xFFE27069)
val ErrorColor              = Color(0xFFFFB4AB)
val ErrorContainer          = Color(0xFF93000A)
val OnErrorContainer        = Color(0xFFFFDAD6)
val Outline                 = Color(0xFF86948B)
val OutlineVariant          = Color(0xFF3D4A43)

private val SafeTempDarkColors = darkColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    primaryContainer = PrimaryContainer,
    background       = Background,
    surface          = SurfaceContainer,
    surfaceVariant   = SurfaceVariant,
    onSurface        = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error            = ErrorColor,
    errorContainer   = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline          = Outline,
    outlineVariant   = OutlineVariant,
)

@Composable
fun SafeTempTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SafeTempDarkColors, content = content)
}
