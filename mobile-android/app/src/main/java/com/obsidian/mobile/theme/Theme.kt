package com.obsidian.mobile.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.obsidian.mobile.data.DarkMode
import com.obsidian.mobile.data.ThemeChoice

// Material 3 colour schemes. Meaning is carried by roles, not hues, in every palette:
// error = scam / blocked, tertiary = suspicious, surfaceVariant = checking or no threats found
// (never green: "no threats found" is not a promise that something is safe).

/** The few colours a palette defines; the rest of the Material 3 roles are derived. */
private data class Palette(
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color, val tertiaryContainer: Color, val onTertiaryContainer: Color,
    val error: Color, val onError: Color, val errorContainer: Color, val onErrorContainer: Color,
    val background: Color, val onBackground: Color, val surfaceVariant: Color, val onSurfaceVariant: Color,
    val outline: Color,
)

private fun Palette.scheme(dark: Boolean): ColorScheme {
    // Container surfaces step from the background towards the text colour.
    fun step(f: Float) = lerp(background, onBackground, f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        inversePrimary = primaryContainer,
        secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        background = background, onBackground = onBackground,
        surface = background, onSurface = onBackground, surfaceTint = primary,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        inverseSurface = onBackground, inverseOnSurface = background,
        outline = outline, outlineVariant = lerp(surfaceVariant, outline, 0.35f),
        surfaceBright = step(if (dark) 0.10f else 0.0f), surfaceDim = step(if (dark) 0.0f else 0.08f),
        surfaceContainerLowest = step(0.0f), surfaceContainerLow = step(0.03f), surfaceContainer = step(0.05f),
        surfaceContainerHigh = step(0.08f), surfaceContainerHighest = step(0.11f),
        scrim = Color.Black,
    )
}

private val ObsidianLight = Palette(
    Color(0xFF00696D), Color.White, Color(0xFF6FF6FC), Color(0xFF002022),
    Color(0xFF4A6364), Color.White, Color(0xFFCCE8E8), Color(0xFF051F20),
    Color(0xFF7D5700), Color.White, Color(0xFFFFDEA0), Color(0xFF271900),
    Color(0xFFBA1A1A), Color.White, Color(0xFFFFDAD6), Color(0xFF410002),
    Color(0xFFFAFDFD), Color(0xFF191C1D), Color(0xFFDAE4E5), Color(0xFF3F4849), Color(0xFF6F7979),
)
private val ObsidianDark = Palette(
    Color(0xFF4FD8DD), Color(0xFF003739), Color(0xFF004F52), Color(0xFF6FF6FC),
    Color(0xFFB0CCCC), Color(0xFF1B3435), Color(0xFF324B4C), Color(0xFFCCE8E8),
    Color(0xFFF8BD48), Color(0xFF422C00), Color(0xFF5E4200), Color(0xFFFFDEA0),
    Color(0xFFFFB4AB), Color(0xFF690005), Color(0xFF93000A), Color(0xFFFFDAD6),
    Color(0xFF111414), Color(0xFFE0E3E3), Color(0xFF3F4849), Color(0xFFBEC8C9), Color(0xFF889393),
)
private val CatppuccinLatte = Palette(
    Color(0xFF8839EF), Color.White, Color(0xFFE5D4FC), Color(0xFF2E0E5C),
    Color(0xFF1E66F5), Color.White, Color(0xFFD5E1FD), Color(0xFF0A2463),
    Color(0xFF9A5B00), Color.White, Color(0xFFFBE3BE), Color(0xFF3A2400),
    Color(0xFFD20F39), Color.White, Color(0xFFF9D3DB), Color(0xFF4A0414),
    Color(0xFFEFF1F5), Color(0xFF4C4F69), Color(0xFFCCD0DA), Color(0xFF5C5F77), Color(0xFF8C8FA1),
)
private val CatppuccinMocha = Palette(
    Color(0xFFCBA6F7), Color(0xFF2A1748), Color(0xFF4A3470), Color(0xFFEBDDFC),
    Color(0xFF89B4FA), Color(0xFF0F2549), Color(0xFF2B4170), Color(0xFFD8E5FD),
    Color(0xFFF9E2AF), Color(0xFF3B2E0E), Color(0xFF5A4A22), Color(0xFFFCF1D8),
    Color(0xFFF38BA8), Color(0xFF4A0A1E), Color(0xFF7A2440), Color(0xFFFCD9E3),
    Color(0xFF1E1E2E), Color(0xFFCDD6F4), Color(0xFF45475A), Color(0xFFBAC2DE), Color(0xFF7F849C),
)
private val NordLight = Palette(
    Color(0xFF5E81AC), Color.White, Color(0xFFD5E0EE), Color(0xFF1C2B40),
    Color(0xFF4C7A86), Color.White, Color(0xFFD3E4E8), Color(0xFF152A30),
    Color(0xFFA35F35), Color.White, Color(0xFFF3DCCD), Color(0xFF3A1D0B),
    Color(0xFFBF616A), Color.White, Color(0xFFF4DADD), Color(0xFF3E1015),
    Color(0xFFECEFF4), Color(0xFF2E3440), Color(0xFFD8DEE9), Color(0xFF434C5E), Color(0xFF7B88A1),
)
private val NordDark = Palette(
    Color(0xFF88C0D0), Color(0xFF13313A), Color(0xFF3B5A66), Color(0xFFD7ECF2),
    Color(0xFF81A1C1), Color(0xFF16263A), Color(0xFF3A4F68), Color(0xFFD9E4F0),
    Color(0xFFEBCB8B), Color(0xFF3B2C0B), Color(0xFF5E4C23), Color(0xFFF8ECD2),
    Color(0xFFE0939A), Color(0xFF45141A), Color(0xFF7D3A42), Color(0xFFF6D9DC),
    Color(0xFF2E3440), Color(0xFFECEFF4), Color(0xFF434C5E), Color(0xFFD8DEE9), Color(0xFF8790A3),
)
private val DraculaLight = Palette( // "Alucard"
    Color(0xFF644AC9), Color.White, Color(0xFFE1D9FA), Color(0xFF1E0F52),
    Color(0xFFA3144D), Color.White, Color(0xFFF8D2E0), Color(0xFF3F0119),
    Color(0xFF8A4A00), Color.White, Color(0xFFF8DFC6), Color(0xFF331A00),
    Color(0xFFCB3A2A), Color.White, Color(0xFFF8D6D1), Color(0xFF450D06),
    Color(0xFFFFFBEB), Color(0xFF1F1F1F), Color(0xFFE8E4D3), Color(0xFF4A4741), Color(0xFF8A8579),
)
private val DraculaDark = Palette(
    Color(0xFFBD93F9), Color(0xFF2C1458), Color(0xFF4F3A80), Color(0xFFEDE1FD),
    Color(0xFFFF79C6), Color(0xFF4D0B31), Color(0xFF7A2F5A), Color(0xFFFFDDEF),
    Color(0xFFF1FA8C), Color(0xFF353A00), Color(0xFF555C1A), Color(0xFFFAFDD9),
    Color(0xFFFF5555), Color(0xFF4F0000), Color(0xFF8A1F1F), Color(0xFFFFDADA),
    Color(0xFF282A36), Color(0xFFF8F8F2), Color(0xFF44475A), Color(0xFFD6D6E0), Color(0xFF6272A4),
)
private val SolarizedLight = Palette(
    Color(0xFF268BD2), Color.White, Color(0xFFD3E6F5), Color(0xFF07263C),
    Color(0xFF2AA198), Color.White, Color(0xFFD1EDEA), Color(0xFF062B28),
    Color(0xFF8C6A00), Color.White, Color(0xFFF2E3B3), Color(0xFF2E2300),
    Color(0xFFDC322F), Color.White, Color(0xFFF8D4D3), Color(0xFF450706),
    Color(0xFFFDF6E3), Color(0xFF586E75), Color(0xFFEEE8D5), Color(0xFF586E75), Color(0xFF93A1A1),
)
private val SolarizedDark = Palette(
    Color(0xFF6CB2E4), Color(0xFF032338), Color(0xFF1B4A6B), Color(0xFFD3E6F5),
    Color(0xFF5CC2B9), Color(0xFF062B28), Color(0xFF1B514C), Color(0xFFD1EDEA),
    Color(0xFFD9B23A), Color(0xFF2E2300), Color(0xFF5C4A0F), Color(0xFFF2E3B3),
    Color(0xFFF07472), Color(0xFF450706), Color(0xFF7E201E), Color(0xFFF8D4D3),
    Color(0xFF002B36), Color(0xFFC5CDCD), Color(0xFF073642), Color(0xFF93A1A1), Color(0xFF657B83),
)

/** True on Android 12+, where Material You takes its colours from the wallpaper. */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val ObsidianShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ObsidianTheme(choice: ThemeChoice, mode: DarkMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when (choice) {
        ThemeChoice.MATERIAL_YOU -> if (dynamicColorAvailable) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            (if (dark) ObsidianDark else ObsidianLight).scheme(dark)
        }
        ThemeChoice.OBSIDIAN -> (if (dark) ObsidianDark else ObsidianLight).scheme(dark)
        ThemeChoice.CATPPUCCIN -> (if (dark) CatppuccinMocha else CatppuccinLatte).scheme(dark)
        ThemeChoice.NORD -> (if (dark) NordDark else NordLight).scheme(dark)
        ThemeChoice.DRACULA -> (if (dark) DraculaDark else DraculaLight).scheme(dark)
        ThemeChoice.SOLARIZED -> (if (dark) SolarizedDark else SolarizedLight).scheme(dark)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = ObsidianTypography, shapes = ObsidianShapes, content = content)
}

/** Swatches for the theme picker: primary, background, error. */
fun themePreview(choice: ThemeChoice, dark: Boolean): List<Color> {
    val palette = when (choice) {
        ThemeChoice.MATERIAL_YOU, ThemeChoice.OBSIDIAN -> if (dark) ObsidianDark else ObsidianLight
        ThemeChoice.CATPPUCCIN -> if (dark) CatppuccinMocha else CatppuccinLatte
        ThemeChoice.NORD -> if (dark) NordDark else NordLight
        ThemeChoice.DRACULA -> if (dark) DraculaDark else DraculaLight
        ThemeChoice.SOLARIZED -> if (dark) SolarizedDark else SolarizedLight
    }
    return listOf(palette.primary, palette.background, palette.tertiary, palette.error)
}
