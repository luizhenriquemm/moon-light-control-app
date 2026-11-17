package io.github.luizhenriquemm.moonlightcontrol.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colors of the web UI (manifest.webmanifest / favicon.svg in the server repo).
private val Night = Color(0xFF14161C)
private val Moon = Color(0xFFF2C94C)

private val DarkColors = darkColorScheme(
    primary = Moon,
    onPrimary = Night,
    background = Night,
    surface = Night,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5C00),
    onPrimary = Color.White,
)

@Composable
fun MoonLightTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
