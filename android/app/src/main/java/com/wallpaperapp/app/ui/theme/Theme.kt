package com.wallpaperapp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.wallpaperapp.app.R

/*
 * Zeul theme (Dev A, app-ui). Brand look instead of Material You wallpaper colours:
 *  - Light: soft neumorphic grey. Surfaces share the background colour and are lifted or pressed
 *    in with paired light/dark shadows (see Soft.kt). Monochrome, with the Zeul orange as a small accent.
 *  - Dark: deep black with charcoal cards and bold Zeul orange for primary actions and the active tab.
 */

/**
 * Display face (wordmark, screen titles, headings, card titles): Montserrat, SIL OFL (brand/fonts).
 * Every weight used is a real file, so Compose never fakes bold.
 */
val ZeulDisplay = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Light),
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

/** Text face (body, labels, buttons, chips): Manrope, SIL OFL (brand/fonts). Calm and legible at small sizes. */
val ZeulText = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun display(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontFamily = ZeulDisplay, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

private fun text(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontFamily = ZeulText, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

/**
 * Zeul type scale (B16). Display/headline/large titles: Montserrat; everything people read or tap:
 * Manrope. Use these roles instead of ad-hoc fontSize/fontFamily.
 */
val ZeulTypography = Typography(
    displayLarge = display(52, 58, FontWeight.Light, 1.0),
    displayMedium = display(42, 48, FontWeight.Light, 1.0),
    displaySmall = display(34, 40, FontWeight.Light, 0.5),   // screen titles
    headlineLarge = display(30, 36, FontWeight.Normal),
    headlineMedium = display(26, 32, FontWeight.Medium),
    headlineSmall = display(22, 28, FontWeight.SemiBold),  // card / sheet titles
    titleLarge = display(20, 26, FontWeight.SemiBold),
    titleMedium = text(16, 22, FontWeight.Bold),
    titleSmall = text(14, 20, FontWeight.Bold),
    bodyLarge = text(16, 24, FontWeight.Normal),
    bodyMedium = text(14, 21, FontWeight.Normal),
    bodySmall = text(12, 17, FontWeight.Medium),
    labelLarge = text(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = text(12, 16, FontWeight.SemiBold, 0.2),
    labelSmall = text(11, 14, FontWeight.Bold, 0.6),       // overlines (uppercase, tracked)
)

val ZeulOrange = Color(0xFFFF5A1F)        // UI tone of the icon's #FF4A01 (crisper for text/icons on it)
val ZeulOrangeDark = Color(0xFFFF6A1A)    // a touch brighter on black

private val LightColors = lightColorScheme(
    primary = ZeulOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D6),
    onPrimaryContainer = Color(0xFF5A1A00),
    secondary = Color(0xFF6B6F76),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE0E5),
    onSecondaryContainer = Color(0xFF2E3136),
    tertiary = ZeulOrange,
    background = SoftLight.base,
    onBackground = SoftLight.text,
    surface = SoftLight.base,
    onSurface = SoftLight.text,
    surfaceVariant = Color(0xFFE2E4E8),
    onSurfaceVariant = SoftLight.textMuted,
    surfaceContainerLowest = Color(0xFFF4F5F7),
    surfaceContainerLow = Color(0xFFEFF0F3),
    surfaceContainer = Color(0xFFE6E8EC),
    surfaceContainerHigh = Color(0xFFE1E3E7),
    surfaceContainerHighest = Color(0xFFDBDDE2),
    outline = Color(0xFFC3C7CE),
    outlineVariant = Color(0xFFD6D9DE),
)

private val DarkColors = darkColorScheme(
    primary = ZeulOrangeDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1A0A),
    onPrimaryContainer = Color(0xFFFFB896),
    secondary = Color(0xFF9A9AA0),
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF2A2A2D),
    onSecondaryContainer = Color(0xFFF2F2F2),
    tertiary = ZeulOrangeDark,
    background = SoftDark.base,
    onBackground = SoftDark.text,
    surface = SoftDark.base,
    onSurface = SoftDark.text,
    surfaceVariant = SoftDark.raised,
    onSurfaceVariant = SoftDark.textMuted,
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF17171A),
    surfaceContainer = SoftDark.raised,
    surfaceContainerHigh = Color(0xFF242426),
    surfaceContainerHighest = Color(0xFF2C2C2F),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2A2A2C),
)

/** Light (soft grey) or dark (black) follows the system setting; Zeul orange is the accent in both. */
@Composable
fun WallpaperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSoft provides if (darkTheme) SoftDark else SoftLight) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = ZeulTypography, shapes = ZeulShapes, content = content)
    }
}
