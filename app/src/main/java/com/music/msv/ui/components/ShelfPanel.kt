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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.msv.data.model.ShelfFile
import com.music.msv.data.model.ShelfSort
import com.music.msv.ui.theme.ButtonShape

private val invertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfPanel(
    isDark: Boolean,
    shelfFiles: List<ShelfFile>,
    shelfSortBy: ShelfSort,
    onFileSelected: (Uri) -> Unit,
    onImportClick: () -> Unit,
    onImslpClick: () -> Unit,
    onClose: () -> Unit,
    onRename: (Uri, String) -> Unit,
    onSortSelected: (ShelfSort) -> Unit,
    onDelete: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val panelBg = if (isDark) Color(0xF00F121C) else Color(0xF2FFFFFF)
    val panelBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val itemBg = if (isDark) Color(0x08FFFFFF) else Color(0x0A1A2230)
    val itemBorder = if (isDark) Color(0x14FFFFFF) else Color(0x1A1A2230)
    val muted = if (isDark) Color(0xB8F5F7FF) else Color(0xD11B2230)
    val accent = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)
    val danger = if (isDark) Color(0xFFFF9AA8) else Color(0xFFD9455D)
    var renameTarget by remember { mutableStateOf<Uri?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuTarget by remember { mutableStateOf<ShelfFile?>(null) }
    var deleteTarget by remember { mutableStateOf<ShelfFile?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    // 排序在渲染层兜底执行：无论加载链路状态如何，选择器切换必然改变可见顺序
    val sortedFiles = remember(shelfFiles, shelfSortBy) {
        when (shelfSortBy) {
            ShelfSort.NAME -> shelfFiles.sortedBy { it.name.lowercase() }
            ShelfSort.DATE -> shelfFiles.sortedByDescending { it.lastModified }
        }
    }
    val menuContainer = if (isDark) Color(0xFF1A1E2E) else Color.White

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ButtonShape)
                    .background(accent)
                    .border(1.dp, accent, ButtonShape)
                    .clickable { onImportClick() }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "导入乐谱",
                    color = if (isDark) Color(0xFF0F1220) else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // IMSLP 在线导入入口（次级样式：itemBg 底 + accent 边框）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ButtonShape)
                    .background(if (isDark) Color(0x0FFFFFFF) else Color(0x0A1A2230))
                    .border(1.dp, accent, ButtonShape)
                    .clickable { onImslpClick() }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "IMSLP 导入",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ButtonShape)
                    .background(if (isDark) Color(0x0FFFFFFF) else Color(0x0A1A2230))
                    .border(1.dp, if (isDark) Color(0x24FFFFFF) else Color(0x1A1A2230), ButtonShape)
                    .clickable { sortMenuOpen = !sortMenuOpen },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (shelfSortBy == ShelfSort.DATE) "↓" else "A",
                    color = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                    containerColor = menuContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "按日期排序",
                                color = if (shelfSortBy == ShelfSort.DATE) accent else text,
                                fontWeight = if (shelfSortBy == ShelfSort.DATE) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingIcon = { if (shelfSortBy == ShelfSort.DATE) Text("✓", color = accent) },
                        onClick = { sortMenuOpen = false; onSortSelected(ShelfSort.DATE) }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "按名称排序",
                                color = if (shelfSortBy == ShelfSort.NAME) accent else text,
                                fontWeight = if (shelfSortBy == ShelfSort.NAME) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingIcon = { if (shelfSortBy == ShelfSort.NAME) Text("✓", color = accent) },
                        onClick = { sortMenuOpen = false; onSortSelected(ShelfSort.NAME) }
                    )
                }
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
            // 瀑布流：卡片高度按缩略图真实比例 + 文件名行数自适应，自然错开无留空
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedFiles, key = { it.uri.toString() }) { sf ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(itemBg, RoundedCornerShape(12.dp))
                            .border(1.dp, itemBorder, RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onFileSelected(sf.uri) },
                                onLongClick = { menuTarget = sf }
                            )
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val context = LocalContext.current
                        // 图片区透明背景：缩略图按真实比例铺满（白底页面无灰边）；无缩略图时保留占位底色
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(sf.thumbAspect ?: 0.75f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sf.thumbnailUri != null) Color.Transparent else if (isDark) Color(0x2E000000) else Color(0x141A2230)),
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
                                    colorFilter = if (isDark) ColorFilter.colorMatrix(invertColorMatrix) else null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    "🎼",
                                    fontSize = 24.sp
                                )
                            }
                        }
                        Text(
                            text = sf.name,
                            color = text,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        // 长按弹出操作菜单（锚定在该乐谱条目位置）
                        DropdownMenu(
                            expanded = menuTarget == sf,
                            onDismissRequest = { menuTarget = null },
                            containerColor = menuContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("重命名", color = text) },
                                onClick = {
                                    menuTarget = null
                                    renameTarget = sf.uri
                                    renameText = sf.name.substringBeforeLast(".")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除", color = danger) },
                                onClick = {
                                    menuTarget = null
                                    deleteTarget = sf
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除乐谱") },
            text = { Text("确定删除「${deleteTarget?.name}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.let { onDelete(it.uri) }
                    deleteTarget = null
                }) { Text("删除", color = danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    placeholder = { Text("输入新文件名") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = renameTarget
                    if (uri != null && renameText.isNotBlank()) {
                        onRename(uri, renameText)
                    }
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
}
