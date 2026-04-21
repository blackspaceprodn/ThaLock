package com.thalock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ThaLockPrimary,
    onPrimary = Color.White,
    primaryContainer = ThaLockPrimaryDeep,
    onPrimaryContainer = Color(0xFFE4DFFF),

    secondary = ThaLockPrimarySoft,
    onSecondary = Color(0xFF1A1A24),
    secondaryContainer = Color(0xFF2B2B38),
    onSecondaryContainer = Color(0xFFD9D4FF),

    tertiary = ThaLockTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3E2B5E),
    onTertiaryContainer = Color(0xFFE6D8FF),

    background = SurfaceDark,
    onBackground = TextPrimaryDark,

    surface = SurfaceCardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,

    outline = Color(0xFF3A3A48),
    outlineVariant = Color(0xFF2A2A36),

    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFF451A22),
    onErrorContainer = Color(0xFFFFD4DA),
)

private val LightColorScheme = lightColorScheme(
    primary = ThaLockPrimaryDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF1E1470),

    secondary = ThaLockSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E7EE),
    onSecondaryContainer = Color(0xFF1E2330),

    tertiary = ThaLockTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2E3FF),
    onTertiaryContainer = Color(0xFF2D0E4A),

    background = SurfaceLight,
    onBackground = TextPrimaryLight,

    surface = SurfaceCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFEDEBF5),
    onSurfaceVariant = TextSecondaryLight,

    outline = Color(0xFFBFBDCC),
    outlineVariant = Color(0xFFE1DFEC),

    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E8),
    onErrorContainer = Color(0xFF3F0A12),
)

private val ThaLockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ThaLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ThaLockTypography,
            shapes = ThaLockShapes,
            content = content
        )
    }
}
