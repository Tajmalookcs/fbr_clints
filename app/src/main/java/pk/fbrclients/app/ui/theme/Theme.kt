package pk.fbrclients.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF0B6E4F)
private val GreenLight = Color(0xFF6FD3A8)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE0),
    onPrimaryContainer = Color(0xFF002116),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFCDEFE0),
)

@Composable
fun FbrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
