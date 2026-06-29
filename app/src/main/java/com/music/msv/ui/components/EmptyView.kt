package com.music.msv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.msv.ui.theme.DarkMuted
import com.music.msv.ui.theme.LightMuted

@Composable
fun EmptyView(
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val muted = if (isDark) DarkMuted else LightMuted
    val accent = if (isDark) androidx.compose.ui.graphics.Color(0xFF8CC8FF) else androidx.compose.ui.graphics.Color(0xFF2F6AD9)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            Text(
                text = "点击上方按钮选择文件，或从文件管理器分享到本应用",
                style = MaterialTheme.typography.bodySmall,
                color = accent
            )
        }
    }
}
