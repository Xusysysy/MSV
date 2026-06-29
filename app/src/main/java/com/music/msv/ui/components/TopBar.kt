package com.music.msv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.msv.ui.theme.TopbarShape
import com.music.msv.ui.theme.ButtonShape

@Composable
fun TopBar(
    isDark: Boolean,
    fileName: String,
    currentPage: Int,
    pageCount: Int,
    showPageNav: Boolean,
    onUploadClick: () -> Unit,
    onPageNumberSubmit: (Int) -> Unit,
    onThumbnailsClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0x940A0E16) else Color(0xE0FFFFFF)
    val border = if (isDark) Color(0x1FFFFFFF) else Color(0x141A2230)
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)
    val muted = if (isDark) Color(0xB8F5F7FF) else Color(0x9E1B2230)
    val divider = if (isDark) Color(0x1FFFFFFF) else Color(0x1F1A2230)
    val ctrlBg = if (isDark) Color(0x0FFFFFFF) else Color(0x0F1A2230)
    val ctrlBorder = if (isDark) Color(0x24FFFFFF) else Color(0x2E1A2230)

    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TopbarShape)
            .background(bg, TopbarShape)
            .border(1.dp, border, TopbarShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Upload button
        Text(
            text = "+ 打开文件",
            color = text,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(ButtonShape)
                .background(ctrlBg, ButtonShape)
                .border(1.dp, ctrlBorder, ButtonShape)
                .clickable { onUploadClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )

        if (showPageNav) {
            // Divider
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(divider)
            )

            // Page number display
            Row(
                modifier = Modifier
                    .clip(ButtonShape)
                    .background(ctrlBg, ButtonShape)
                    .border(1.dp, ctrlBorder, ButtonShape)
                    .clickable {
                        editing = true
                        editText = currentPage.toString()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editing) {
                    BasicTextField(
                        value = editText,
                        onValueChange = { newVal ->
                            if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                                editText = newVal
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = text,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.width(40.dp),
                        decorationBox = { innerTextField ->
                            innerTextField()
                        }
                    )
                    IconButton(
                        onClick = {
                            val page = editText.toIntOrNull()
                            if (page != null && page in 1..pageCount) {
                                onPageNumberSubmit(page - 1)
                            }
                            editing = false
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Text("✓", color = text, fontSize = 12.sp)
                    }
                } else {
                    Text(text = currentPage.toString(), color = text, fontSize = 13.sp)
                    Text(text = "/", color = muted, fontSize = 13.sp)
                    Text(text = pageCount.toString(), color = text, fontSize = 13.sp)
                }
            }
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
            Text(
                text = "▦",
                color = text,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(ButtonShape)
                    .background(ctrlBg, ButtonShape)
                    .border(1.dp, ctrlBorder, ButtonShape)
                    .clickable { onThumbnailsClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            // Fullscreen / Theme button
            Text(
                text = "⛶",
                color = text,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(ButtonShape)
                    .background(ctrlBg, ButtonShape)
                    .border(1.dp, ctrlBorder, ButtonShape)
                    .clickable { onFullscreenClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            // Reset button
            Text(
                text = "⟲",
                color = text,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(ButtonShape)
                    .background(ctrlBg, ButtonShape)
                    .border(1.dp, ctrlBorder, ButtonShape)
                    .clickable { onResetClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}
