package com.music.msv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.msv.ui.theme.FooterShape

@Composable
fun BottomFooter(
    isDark: Boolean,
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0x52000000) else Color(0xB3FFFFFF)
    val border = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)

    Text(
        text = statusMessage.ifEmpty { "\u00A0" },
        color = text,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(FooterShape)
            .background(bg, FooterShape)
            .border(1.dp, border, FooterShape)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}
