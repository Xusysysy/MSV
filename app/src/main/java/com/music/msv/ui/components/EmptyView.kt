package com.music.msv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.msv.ui.theme.DarkAccent
import com.music.msv.ui.theme.DarkMuted
import com.music.msv.ui.theme.LightAccent
import com.music.msv.ui.theme.LightMuted

@Composable
fun EmptyView(
    isDark: Boolean = true,
    onShelfClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val muted = if (isDark) DarkMuted else LightMuted
    val accent = if (isDark) DarkAccent else LightAccent
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)
    val bg = if (isDark) Color(0xFF1B1F2E) else Color(0xFFFFFFFF)
    val border = if (isDark) Color(0x1AFFFFFF) else Color(0x1A1A2230)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎼",
                fontSize = 48.sp
            )
            Text(
                text = "乐谱查看器",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "支持 PDF 与多图片乐谱，打开文件即可查看",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(accent)
                    .border(1.dp, accent, RoundedCornerShape(28.dp))
                    .clickable { onShelfClick() }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📂 谱架",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
