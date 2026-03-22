package tech.ignacio.glosso.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GlossoPrimary,
    secondary = GlossoSecondary,
    tertiary = GlossoTertiary,
    background = GlossoBackground,
    surface = GlossoSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = GlossoOnSurface,
    onSurface = GlossoOnSurface,
    surfaceVariant = Color(0xFFF1F5F9),
    outline = GlossoOutline
)

@Composable
fun GlossoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
