package com.music.msv.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.msv.data.model.PageBookmark
import com.music.msv.ui.theme.ThumbnailItemShape
import com.music.msv.ui.theme.ThumbnailThumbShape

private val invertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

private val bookmarkPresets = listOf(
    0xFFFF5A5A.toInt(), 0xFFFF9F43.toInt(), 0xFFFFD93D.toInt(), 0xFF6BCB77.toInt(),
    0xFF4D96FF.toInt(), 0xFF9B5DE5.toInt(), 0xFFFF6FB5.toInt(), 0xFF00C2A8.toInt()
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThumbnailPanel(
    isDark: Boolean,
    pageCount: Int,
    currentPage: Int,
    isPdfMode: Boolean,
    bookmarks: List<PageBookmark>,
    getThumbnailUri: (Int) -> Any?,
    onPageSelected: (Int) -> Unit,
    onBookmarkClick: (Int) -> Unit,
    onAddBookmark: (Int, String, Int) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onRenameBookmark: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelBg = if (isDark) Color(0xF00F121C) else Color(0xF2FFFFFF)
    val panelBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val itemBg = if (isDark) Color(0x08FFFFFF) else Color(0x0A1A2230)
    val itemBorder = if (isDark) Color(0x14FFFFFF) else Color(0x1A1A2230)
    val itemActiveBg = if (isDark) Color(0x148CC8FF) else Color(0x1F2F6AD9)
    val itemActiveBorder = if (isDark) Color(0x738CC8FF) else Color(0x662F6AD9)
    val muted = if (isDark) Color(0xB8F5F7FF) else Color(0xD11B2230)
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)
    val danger = if (isDark) Color(0xFFFF9AA8) else Color(0xFFD9455D)
    val menuContainer = if (isDark) Color(0xFF1A1E2E) else Color.White

    var addPage by remember { mutableStateOf<Int?>(null) }
    var addTitle by remember { mutableStateOf("") }
    var addColor by remember { mutableStateOf(bookmarkPresets[4]) }
    var renameBmId by remember { mutableStateOf<String?>(null) }
    var renameBmText by remember { mutableStateOf("") }
    var bmMenuId by remember { mutableStateOf<String?>(null) }

    Row(modifier = modifier) {
        // 书签栏：横向书签条自上而下贴着排列，左侧半圆、宽度随名字长度、半透明、无额外背景、可上下滚动
        if (isPdfMode && bookmarks.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                bookmarks.forEach { bm ->
                    var bmMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 180.dp)
                                .background(
                                    Color(bm.color).copy(alpha = 0.75f),
                                    RoundedCornerShape(topStart = 17.dp, bottomStart = 17.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                                )
                                .combinedClickable(
                                    onClick = { onBookmarkClick(bm.page) },
                                    onLongClick = { bmMenuOpen = !bmMenuOpen }
                                )
                                .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                bm.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 2,
                                modifier = Modifier.widthIn(max = 150.dp)
                            )
                            DropdownMenu(
                                expanded = bmMenuOpen,
                                onDismissRequest = { bmMenuOpen = false },
                                containerColor = menuContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("重命名", color = text) },
                                    onClick = {
                                        bmMenuOpen = false
                                        renameBmId = bm.id
                                        renameBmText = bm.title
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = danger) },
                                    onClick = {
                                        bmMenuOpen = false
                                        onDeleteBookmark(bm.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
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
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF1B2230).copy(alpha = 0.08f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = if (isDark) Color.White else Color(0xFF1B2230), fontSize = 14.sp)
                }
            }

            // Thumbnail grid
            val gridState = rememberLazyGridState()

            LaunchedEffect(currentPage, pageCount) {
                if (pageCount <= 0) return@LaunchedEffect
                val rowIndex = currentPage / 2
                val visibleRows = 5
                val targetRow = (rowIndex - visibleRows / 2).coerceAtLeast(0)
                gridState.scrollToItem(targetRow * 2)
            }

            LazyVerticalGrid(
                state = gridState,
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
                            .combinedClickable(
                                onClick = { onPageSelected(index) },
                                onLongClick = if (isPdfMode) {
                                    {
                                        addPage = index
                                        addTitle = ""
                                        addColor = bookmarkPresets[4]
                                    }
                                } else null,
                                enabled = true
                            )
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
                            colorFilter = if (isDark) ColorFilter.colorMatrix(invertColorMatrix) else null,
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

    // 添加书签弹窗（长按页面缩略图呼出；命名上限 50 字 + RGB 颜色选择）
    if (addPage != null) {
        AlertDialog(
            onDismissRequest = { addPage = null },
            title = { Text("添加书签 · 第${(addPage ?: 0) + 1}页") },
            text = {
                Column {
                    OutlinedTextField(
                        value = addTitle,
                        onValueChange = { addTitle = it.take(50) },
                        singleLine = true,
                        placeholder = { Text("书签名（最多50字）") },
                        supportingText = { Text("${addTitle.length}/50") }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("颜色", color = muted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bookmarkPresets.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        if (addColor == c) 2.dp else 1.dp,
                                        if (addColor == c) text else itemBorder,
                                        CircleShape
                                    )
                                    .clickable { addColor = c }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = addPage
                    if (p != null && addTitle.isNotBlank()) onAddBookmark(p, addTitle.trim(), addColor)
                    addPage = null
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { addPage = null }) { Text("取消") }
            }
        )
    }

    // 书签重命名弹窗
    if (renameBmId != null) {
        AlertDialog(
            onDismissRequest = { renameBmId = null },
            title = { Text("重命名书签") },
            text = {
                OutlinedTextField(
                    value = renameBmText,
                    onValueChange = { renameBmText = it.take(50) },
                    singleLine = true,
                    placeholder = { Text("书签名（最多50字）") },
                    supportingText = { Text("${renameBmText.length}/50") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = renameBmId
                    if (id != null && renameBmText.isNotBlank()) onRenameBookmark(id, renameBmText.trim())
                    renameBmId = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameBmId = null }) { Text("取消") }
            }
        )
    }
}
