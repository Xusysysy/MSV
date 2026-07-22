package com.music.msv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.music.msv.ui.theme.DarkAccent
import com.music.msv.ui.theme.DarkOverlayBg
import com.music.msv.ui.theme.DarkSpinnerTrack
import com.music.msv.ui.theme.LightAccent
import com.music.msv.ui.theme.LightOverlayBg
import com.music.msv.ui.theme.LightSpinnerTrack

@Composable
fun LoadingOverlay(
    isDark: Boolean = true,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val bg = if (isDark) DarkOverlayBg else LightOverlayBg
    val accent = if (isDark) DarkAccent else LightAccent
    val track = if (isDark) DarkSpinnerTrack else LightSpinnerTrack

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = accent,
            trackColor = track,
            strokeWidth = 3.dp
        )
    }
}
