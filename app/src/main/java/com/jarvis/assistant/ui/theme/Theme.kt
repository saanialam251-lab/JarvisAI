package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF00E5FF)
val Blue = Color(0xFF2979FF)
val Bg = Color(0xFF070B14)
val Surface = Color(0xFF101828)
val Surface2 = Color(0xFF182338)
val TextMain = Color(0xFFE6F3FF)
val TextDim = Color(0xFF8FA8C7)
val ErrorRed = Color(0xFFFF5252)
val OkGreen = Color(0xFF69F0AE)

private val JarvisColors = darkColorScheme(
    primary = Cyan, onPrimary = Color(0xFF002530),
    secondary = Blue, onSecondary = Color.White,
    background = Bg, onBackground = TextMain,
    surface = Surface, onSurface = TextMain,
    surfaceVariant = Surface2, onSurfaceVariant = TextDim,
    error = ErrorRed,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = JarvisColors, content = content)
