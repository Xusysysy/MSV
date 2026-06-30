package com.music.msv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.music.msv.ui.theme.ShellShape

@Composable
fun GlassShell(
    modifier: Modifier = Modifier,
    shape: Shape = ShellShape,
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val bgColor = if (isDark) Color(0xFF141824) else Color(0xFFE8ECF5)
    val borderColor = if (isDark) Color(0x1AFFFFFF) else Color(0x14FFFFFF)

    Box(
        modifier = modifier
            .shadow(24.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
    ) {
        content()
    }
}
