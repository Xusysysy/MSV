package com.music.msv.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.msv.data.model.ShelfFile
import com.music.msv.ui.theme.ButtonShape

@Composable
fun ShelfPanel(
    isDark: Boolean,
    shelfFiles: List<ShelfFile>,
    onFileSelected: (Uri) -> Unit,
    onImportClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelBg = if (isDark) Color(0xF00F121C) else Color(0xF2FFFFFF)
    val panelBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val itemBg = if (isDark) Color(0x08FFFFFF) else Color(0x0A1A2230)
    val itemBorder = if (isDark) Color(0x14FFFFFF) else Color(0x1A1A2230)
    val muted = if (isDark) Color(0xB8F5F7FF) else Color(0xD11B2230)
    val accent = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)

    Column(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(panelBg, RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
            .border(1.dp, panelBorder, RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF1B2230).copy(alpha = 0.08f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = if (isDark) Color.White else Color(0xFF1B2230), fontSize = 14.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ButtonShape)
                    .background(accent)
                    .border(1.dp, accent, ButtonShape)
                    .clickable { onImportClick() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+ 导入乐谱",
                    color = if (isDark) Color(0xFF0F1220) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (shelfFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无导入的乐谱", color = muted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(shelfFiles) { _, sf ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(itemBg, RoundedCornerShape(12.dp))
                            .border(1.dp, itemBorder, RoundedCornerShape(12.dp))
                            .clickable { onFileSelected(sf.uri) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val context = LocalContext.current
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.2f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0x2E000000) else Color(0x141A2230)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sf.thumbnailUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(sf.thumbnailUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = sf.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    "🎼",
                                    fontSize = 32.sp
                                )
                            }
                        }
                        Text(
                            text = sf.name,
                            color = text,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
