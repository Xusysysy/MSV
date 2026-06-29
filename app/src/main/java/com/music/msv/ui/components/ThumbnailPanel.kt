package com.music.msv.ui.components

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.msv.ui.theme.ThumbnailItemShape
import com.music.msv.ui.theme.ThumbnailThumbShape

@Composable
fun ThumbnailPanel(
    isDark: Boolean,
    pageCount: Int,
    currentPage: Int,
    getThumbnailUri: (Int) -> Any?,
    onPageSelected: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelBg = if (isDark) Color(0xF00F121C) else Color(0xF2FFFFFF)
    val panelBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val itemBg = if (isDark) Color(0x08FFFFFF) else Color(0x0A1A2230)
    val itemBorder = if (isDark) Color(0x14FFFFFF) else Color(0x1A1A2230)
    val itemActiveBg = if (isDark) Color(0x148CC8FF) else Color(0x1F2F6AD9)
    val itemActiveBorder = if (isDark) Color(0x738CC8FF) else Color(0x662F6AD9)
    val muted = if (isDark) Color(0xB8F5F7FF) else Color(0x9E1B2230)

    Column(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(panelBg, RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
            .border(1.dp, panelBorder, RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
    ) {
        // Close button
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
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = if (isDark) Color.White else Color(0xFF1B2230), fontSize = 14.sp)
            }
        }

        // Thumbnail grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed((0 until pageCount).toList()) { index, _ ->
                val isCurrent = index == currentPage
                Column(
                    modifier = Modifier
                        .clip(ThumbnailItemShape)
                        .background(
                            if (isCurrent) itemActiveBg else itemBg,
                            ThumbnailItemShape
                        )
                        .border(
                            1.dp,
                            if (isCurrent) itemActiveBorder else itemBorder,
                            ThumbnailItemShape
                        )
                        .clickable { onPageSelected(index) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(getThumbnailUri(index))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clip(ThumbnailThumbShape)
                            .background(if (isDark) Color(0x2E000000) else Color(0x141A2230))
                    )
                    Text(
                        text = "${index + 1}",
                        color = muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
