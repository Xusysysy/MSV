package com.music.msv.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

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

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && forceDark == null -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
