package com.apps.apkstore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*

enum class AppTheme(val value: Int) {
    DARK(0),
    LIGHT(1),
    NEON_BLUE(2);

    companion object {
        fun fromValue(value: Int): AppTheme = entries.find { it.value == value } ?: DARK
    }
}

val LocalAppTheme = staticCompositionLocalOf { mutableIntStateOf(0) }

@Composable
fun ProvideAppTheme(
    themeMode: Int,
    onThemeChanged: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    val themeState = remember { mutableIntStateOf(themeMode) }

    LaunchedEffect(themeMode) {
        themeState.intValue = themeMode
    }

    CompositionLocalProvider(LocalAppTheme provides themeState) {
        val scheme = when (AppTheme.fromValue(themeState.intValue)) {
            AppTheme.DARK -> DarkColors
            AppTheme.LIGHT -> LightColorScheme
            AppTheme.NEON_BLUE -> NeonBlueColorScheme
        }
        MaterialTheme(colorScheme = scheme) {
            content()
        }
    }
}

fun getAppThemeColorScheme(themeValue: Int) = when (AppTheme.fromValue(themeValue)) {
    AppTheme.DARK -> DarkColors
    AppTheme.LIGHT -> LightColorScheme
    AppTheme.NEON_BLUE -> NeonBlueColorScheme
}
