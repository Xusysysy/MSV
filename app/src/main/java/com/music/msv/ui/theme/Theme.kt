package com.music.msv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkText,
    primaryContainer = DarkControlBg,
    secondary = DarkMuted,
    tertiary = DarkDanger,
    background = DarkAppBg,
    surface = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkLine,
    outlineVariant = DarkControlBorder,
    scrim = DarkShadeBg,
    surfaceVariant = DarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightText,
    primaryContainer = LightControlBg,
    secondary = LightMuted,
    tertiary = LightDanger,
    background = LightAppBg,
    surface = LightSurfaceVariant,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightLine,
    outlineVariant = LightControlBorder,
    scrim = LightShadeBg,
    surfaceVariant = LightSurfaceVariant
)

@Composable
fun MSVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDark: Boolean? = null,
    content: @Composable () -> Unit
) {
    val isDark = forceDark ?: darkTheme

    // 设计语言单一事实源：本应用使用自定义玻璃色板，不采用动态取色（否则色板会被壁纸色静默覆盖）
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val msvColors = if (isDark) DarkMsvColors else LightMsvColors

    CompositionLocalProvider(LocalMsvColors provides msvColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = MsvShapes,
            typography = AppTypography,
            content = content
        )
    }
}
