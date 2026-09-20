package com.apps.apkstore.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Common colors
val Primary = Color(0xFF4445E8)
val PrimaryLight = Color(0xFF6B6CF0)
val PrimaryDark = Color(0xFF3232B8)
val Secondary = Color(0xFF00C896)
val Tertiary = Color(0xFFFFAA33)
val Success = Color(0xFF00C896)
val Warning = Color(0xFFFFAA33)
val Error = Color(0xFFFF4466)
val Info = Color(0xFF4488FF)
val RatingStar = Color(0xFFFFCC00)

// Dark theme colors
val DarkBackground = Color(0xFF121218)
val DarkSurface = Color(0xFF1A1A24)
val DarkSurfaceVariant = Color(0xFF22222E)
val DarkCardBackground = Color(0xFF1E1E2A)
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFF9999AA)
val DarkTextTertiary = Color(0xFF666677)
val DarkDivider = Color(0xFF2A2A3A)

// Light theme colors
val LightBackground = Color(0xFFF5F5FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F0F8)
val LightCardBackground = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF1A1A2E)
val LightTextSecondary = Color(0xFF666677)
val LightTextTertiary = Color(0xFF999999)
val LightDivider = Color(0xFFE8E8F0)

// Neon Blue theme colors
val NeonBackground = Color(0xFF0A0E1A)
val NeonSurface = Color(0xFF111828)
val NeonSurfaceVariant = Color(0xFF1A2236)
val NeonCardBackground = Color(0xFF151D2E)
val NeonPrimary = Color(0xFF00B4FF)
val NeonTextPrimary = Color(0xFFE8F0FF)
val NeonTextSecondary = Color(0xFF7099CC)
val NeonTextTertiary = Color(0xFF4A6A99)
val NeonDivider = Color(0xFF1E2D44)

// Dark Theme
val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = Secondary,
    onSecondary = Color.White,
    tertiary = Tertiary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = Error,
    onError = Color.White,
    outline = DarkDivider
)

// Light Theme
val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = PrimaryDark,
    secondary = Secondary,
    onSecondary = Color.White,
    tertiary = Tertiary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    error = Error,
    onError = Color.White,
    outline = LightDivider
)

// Neon Blue Theme
val NeonBlueColorScheme = darkColorScheme(
    primary = NeonPrimary,
    onPrimary = Color.Black,
    primaryContainer = NeonPrimary.copy(alpha = 0.15f),
    onPrimaryContainer = NeonPrimary,
    secondary = Secondary,
    onSecondary = Color.Black,
    tertiary = Tertiary,
    background = NeonBackground,
    onBackground = NeonTextPrimary,
    surface = NeonSurface,
    onSurface = NeonTextPrimary,
    surfaceVariant = NeonSurfaceVariant,
    onSurfaceVariant = NeonTextSecondary,
    error = Error,
    onError = Color.White,
    outline = NeonDivider
)

// Legacy aliases for backward compatibility
val Background = DarkBackground
val Surface = DarkSurface
val SurfaceVariant = DarkSurfaceVariant
val CardBackground = DarkCardBackground
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val TextTertiary = DarkTextTertiary
val Divider = DarkDivider
