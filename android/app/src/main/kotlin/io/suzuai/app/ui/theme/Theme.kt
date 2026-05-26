package io.suzuai.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SuzuColorScheme = darkColorScheme(
    primary = SuzuColors.AccentCyan,
    onPrimary = SuzuColors.Background,
    primaryContainer = SuzuColors.SurfaceVariant,
    onPrimaryContainer = SuzuColors.AccentCyan,
    secondary = SuzuColors.AccentGreen,
    onSecondary = SuzuColors.Background,
    background = SuzuColors.Background,
    onBackground = SuzuColors.OnBackground,
    surface = SuzuColors.Surface,
    onSurface = SuzuColors.OnSurface,
    surfaceVariant = SuzuColors.SurfaceVariant,
    onSurfaceVariant = SuzuColors.Muted,
    error = SuzuColors.AccentRed,
    onError = SuzuColors.Background,
    outline = SuzuColors.Border,
)

@Composable
fun SuzuTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SuzuColors.Background.toArgb()
            window.navigationBarColor = SuzuColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = SuzuColorScheme,
        typography = SuzuTypography,
        content = content,
    )
}
