package com.music.msv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.msv.ui.theme.TopbarShape
import com.music.msv.ui.theme.ButtonShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopBar(
    isDark: Boolean,
    fileName: String,
    currentPage: Int,
    pageCount: Int,
    showPageNav: Boolean,
    onShelfClick: () -> Unit,
    onPageJumpClick: () -> Unit,
    onThumbnailsClick: () -> Unit,
    onResetClick: () -> Unit,
    onThemeLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0x940A0E16) else Color(0xE0FFFFFF)
    val border = if (isDark) Color(0x1FFFFFFF) else Color(0x141A2230)
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)
    val muted = if (isDark) Color(0xB8F5F7FF) else Color(0xD11B2230)
    val divider = if (isDark) Color(0x1FFFFFFF) else Color(0x1F1A2230)
    val ctrlBg = if (isDark) Color(0x0FFFFFFF) else Color(0x0A1A2230)
    val ctrlBorder = if (isDark) Color(0x24FFFFFF) else Color(0x1A1A2230)
    val accent = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TopbarShape)
            .background(bg, TopbarShape)
            .border(1.dp, border, TopbarShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Shelf button
        Row(
            modifier = Modifier
                .clip(ButtonShape)
                .background(accent)
                .border(1.dp, accent, ButtonShape)
                .clickable { onShelfClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("📂", color = if (isDark) Color(0xFF0F1220) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("谱架", color = if (isDark) Color(0xFF0F1220) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        if (showPageNav) {
            // Page number display (click to jump)
            Row(
                modifier = Modifier
                    .clip(ButtonShape)
                    .background(ctrlBg, ButtonShape)
                    .border(1.dp, ctrlBorder, ButtonShape)
                    .clickable { onPageJumpClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = currentPage.toString(), color = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "/", color = muted, fontSize = 13.sp)
                Text(text = pageCount.toString(), color = muted, fontSize = 13.sp)
            }

            // Divider
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(divider)
            )
        }

        // Spacer
        Spacer(modifier = Modifier.weight(1f))

        // File name
        if (fileName.isNotEmpty()) {
            Text(
                text = fileName,
                color = muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        if (showPageNav) {
            // Thumbnail button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ButtonShape)
                    .background(ctrlBg, ButtonShape)
                    .border(1.dp, ctrlBorder, ButtonShape)
                    .clickable { onThumbnailsClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("▦", color = text, fontSize = 16.sp, textAlign = TextAlign.Center)
            }

            // Reset button (long-press toggles theme)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ButtonShape)
                    .background(ctrlBg, ButtonShape)
                    .border(1.dp, ctrlBorder, ButtonShape)
                    .combinedClickable(
                        onClick = { onResetClick() },
                        onLongClick = { onThemeLongClick() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("↺", color = text, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
