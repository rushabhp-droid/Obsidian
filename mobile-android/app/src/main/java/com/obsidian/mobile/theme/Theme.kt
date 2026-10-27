package com.obsidian.mobile.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = ScamError,
    onError = OnPrimary,
    errorContainer = ScamErrorContainer,
    onErrorContainer = OnScamErrorContainer,
    tertiary = SuspiciousTertiary,
    onTertiary = OnPrimary,
    tertiaryContainer = SuspiciousTertiaryContainer,
    onTertiaryContainer = OnSuspiciousTertiaryContainer,
    outline = Outline,
    outlineVariant = OutlineVariant
)

@Composable
fun ObsidianMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Obsidian Stitch theme strictly uses the Light scheme (#00696D seed) with high contrast options
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Surface.toArgb()
            window.navigationBarColor = NavBarBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ObsidianTypography,
        shapes = ObsidianShapes,
        content = content
    )
}
