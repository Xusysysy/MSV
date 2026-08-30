package com.music.msv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.msv.data.model.UpdateInfo
import com.music.msv.data.model.UpdateStatus
import com.music.msv.ui.theme.ButtonShape

@Composable
fun SettingsPanel(
    isDark: Boolean,
    versionName: String,
    versionCode: Int,
    showVersionLog: Boolean,
    versionNotes: String,
    versionNotesLoading: Boolean,
    downloadProgress: Int,
    updateStatus: UpdateStatus,
    updateInfo: UpdateInfo?,
    updateMessage: String,
    onCheckUpdate: () -> Unit,
    onToggleVersionLog: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelBg = if (isDark) Color(0xF00F121C) else Color(0xF2FFFFFF)
    val panelBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)
    val muted = if (isDark) Color(0xB8F5F7FF) else Color(0xD11B2230)
    val divider = if (isDark) Color(0x1FFFFFFF) else Color(0x1F1A2230)
    val ctrlBg = if (isDark) Color(0x0FFFFFFF) else Color(0x0A1A2230)
    val ctrlBorder = if (isDark) Color(0x24FFFFFF) else Color(0x1A1A2230)
    val accent = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)
    val onAccent = if (isDark) Color(0xFF0F1220) else Color.White

    Column(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(panelBg, RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
            .border(1.dp, panelBorder, RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
    ) {
        // Close button (同 ThumbnailPanel 样式)
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("🎼 MSV 乐谱查看器", color = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("版本 $versionName ($versionCode)", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (showVersionLog) "收起本版本更新日志 ▴" else "查看本版本更新日志 ▾",
                color = accent,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onToggleVersionLog() }
            )
            if (showVersionLog) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (versionNotesLoading) {
                        Text("加载中…", color = muted, fontSize = 12.sp, lineHeight = 17.sp)
                    } else {
                        Text(versionNotes, color = muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
            Spacer(Modifier.height(12.dp))

            // 检查更新按钮（检查中/下载更新包时禁用）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ButtonShape)
                    .background(accent)
                    .clickable(
                        enabled = updateStatus != UpdateStatus.Checking &&
                            updateStatus != UpdateStatus.Downloading
                    ) { onCheckUpdate() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (updateStatus) {
                        UpdateStatus.Checking -> "检查中…"
                        UpdateStatus.Downloading -> "下载中…"
                        else -> "检查更新"
                    },
                    color = onAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))

            // 更新状态区
            when (updateStatus) {
                UpdateStatus.Idle, UpdateStatus.Checking -> {}
                UpdateStatus.UpToDate -> StatusText(updateMessage.ifEmpty { "已是最新版本" }, muted)
                UpdateStatus.Error -> StatusText(updateMessage.ifEmpty { "检查失败，请重试" }, muted)
                UpdateStatus.Downloading -> {
                    StatusText("正在下载更新包… $downloadProgress%", muted)
                    Spacer(Modifier.height(8.dp))
                    // 应用内下载进度条（accent 填充）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ctrlBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(downloadProgress.coerceIn(0, 100) / 100f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent)
                        )
                    }
                }
                UpdateStatus.Available -> {
                    val info = updateInfo
                    if (info != null) {
                        Text(
                            "发现新版本 v${info.tag}（来源：${info.source}）",
                            color = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(info.notes, color = muted, fontSize = 12.sp, lineHeight = 17.sp)
                        var showFullLog by remember { mutableStateOf(false) }
                        Text(
                            if (showFullLog) "收起更新日志 ▴" else "查看更新日志 ▾",
                            color = accent,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { showFullLog = !showFullLog }
                        )
                        if (showFullLog) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(info.fullNotes.ifEmpty { "暂无更新日志" }, color = muted, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlineButton("立即更新", accent, ctrlBg, onDownloadUpdate)
                    }
                }
                UpdateStatus.Downloaded -> {
                    StatusText("更新包已下载完成，可安装", muted)
                    Spacer(Modifier.height(10.dp))
                    OutlineButton("立即安装", accent, ctrlBg, onInstallUpdate)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "© 2026 Xusysysy · MSV 乐谱查看器",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Center,
            color = muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StatusText(msg: String, color: Color) {
    Text(msg, color = color, fontSize = 13.sp)
}

@Composable
private fun OutlineButton(label: String, accent: Color, ctrlBg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(ctrlBg)
            .border(1.dp, accent, ButtonShape)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
