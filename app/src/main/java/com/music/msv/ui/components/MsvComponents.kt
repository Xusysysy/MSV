package com.music.msv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.msv.ui.theme.ButtonShape
import com.music.msv.ui.theme.Msv
import com.music.msv.ui.theme.MsvSpacing

/**
 * MSV 统一组件库 —— 全应用弹窗与控件的唯一实现。
 *
 * 设计契约（用户要求"所有弹窗和界面的设计语言都要统一"）：
 * - 圆角：一律取自 [MaterialTheme.shapes]（MaterialTheme.shapes 由 MSVTheme 注入 MsvShapes）
 * - 颜色：一律取自 [Msv.colors] 语义令牌，禁止 `if (isDark) Color(0x…)` 与硬编码 hex
 * - 间距：一律取自 [MsvSpacing]
 * - 排版：一律取自 [MaterialTheme.typography]
 */

// ══════════════════════════ 弹窗 ══════════════════════════

/**
 * 统一确认式弹窗。取代散落的 11 处原生 `AlertDialog` 调用，
 * 保证圆角/底色/描边/标题/正文/按钮排版完全一致。
 */
@Composable
fun MsvAlertDialog(
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    text: String? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val c = Msv.colors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.textMuted,
        iconContentColor = c.accent,
        title = title?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = when {
            content != null -> ({ content() })
            text != null -> ({
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                )
            })
            else -> null
        },
        confirmButton = {
            MsvTextButton(
                text = confirmText,
                onClick = onConfirm,
                enabled = confirmEnabled,
                color = if (danger) c.danger else c.accent,
                emphasized = true,
            )
        },
        dismissButton = dismissText?.let {
            {
                MsvTextButton(
                    text = it,
                    onClick = onDismiss ?: onDismissRequest,
                    color = c.textMuted,
                )
            }
        },
    )
}

/**
 * 统一自定义弹窗容器（带自定义尺寸/内容的弹窗，如 IMSLP、人脸面板）。
 * 与 [MsvAlertDialog] 共享同一圆角、底色与描边，不再出现第二套浮层视觉。
 */
@Composable
fun MsvDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Msv.colors
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            modifier = modifier
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.surfaceBorder, shape),
            content = content,
        )
    }
}

/**
 * 统一浮层表面修饰符：圆角（[MaterialTheme.shapes]）+ 令牌底色 + 令牌描边。
 * 用于不便改用 [MsvDialogSurface] 的既有结构（如 IMSLP 整屏 Dialog），
 * 保证浮层视觉与统一弹窗一致，且无需重排既有布局。
 */
@Composable
fun Modifier.msvSurface(shape: Shape = MaterialTheme.shapes.large): Modifier =
    this
        .clip(shape)
        .background(Msv.colors.surface)
        .border(1.dp, Msv.colors.surfaceBorder, shape)

/** 弹窗内的统一文本按钮。 */
@Composable
fun MsvTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Msv.colors.accent,
    emphasized: Boolean = false,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            text = text,
            color = if (enabled) color else color.copy(alpha = 0.4f),
            fontSize = 14.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ══════════════════════════ 按钮 ══════════════════════════

enum class MsvButtonVariant { Primary, Ghost, Outline, Danger }

/**
 * 统一按钮：取代此前 5 种互不相同的按钮处理（accent 实心胶囊 / ctrlBg 幽灵 / 方角图标 / 圆形 / 芯片）。
 * 高度与内边距固定，圆角取 [ButtonShape]（全胶囊）。
 */
@Composable
fun MsvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MsvButtonVariant = MsvButtonVariant.Primary,
    enabled: Boolean = true,
    leading: String? = null,
) {
    val c = Msv.colors
    val bg = when (variant) {
        MsvButtonVariant.Primary -> c.accent
        MsvButtonVariant.Ghost -> c.controlBg
        MsvButtonVariant.Outline -> Color.Transparent
        MsvButtonVariant.Danger -> c.danger
    }
    val fg = when (variant) {
        MsvButtonVariant.Primary, MsvButtonVariant.Danger -> c.onAccent
        MsvButtonVariant.Ghost -> c.text
        MsvButtonVariant.Outline -> c.accent
    }
    val border = when (variant) {
        MsvButtonVariant.Outline -> c.accent.copy(alpha = 0.55f)
        MsvButtonVariant.Ghost -> c.controlBorder
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(ButtonShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .border(1.dp, border, ButtonShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = MsvSpacing.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(leading, fontSize = 14.sp, color = if (enabled) fg else fg.copy(alpha = 0.5f))
            Spacer(Modifier.width(MsvSpacing.sm))
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) fg else fg.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 统一图标按钮：40dp 方角（[MaterialTheme.shapes.medium]）为默认，可切圆形（侧栏关闭）。
 */
@Composable
fun MsvIconButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    active: Boolean = false,
    circular: Boolean = false,
    contentDescription: String? = null,
) {
    val c = Msv.colors
    val shape = if (circular) ButtonShape else MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (active) c.accent.copy(alpha = 0.14f) else c.controlBg)
            .border(1.dp, if (active) c.accent.copy(alpha = 0.66f) else c.controlBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontSize = if (size <= 34.dp) 15.sp else 17.sp,
            color = if (active) c.accent else c.text,
        )
    }
}

// ══════════════════════════ 面板 / 容器 ══════════════════════════

/**
 * 统一侧栏头部：标题 + 圆形关闭按钮。三个侧栏（谱架/缩略图/设置）此前各自实现一套。
 */
@Composable
fun MsvPanelHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Msv.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MsvSpacing.md, vertical = MsvSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actions != null) {
            actions()
            Spacer(Modifier.width(MsvSpacing.sm))
        }
        MsvIconButton(glyph = "✕", onClick = onClose, size = 34.dp, circular = true, contentDescription = "关闭")
    }
}

/** 统一卡片/列表项容器（选中态用同一套 active 令牌）。 */
@Composable
fun MsvCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = MaterialTheme.shapes.small,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Msv.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) c.itemActiveBg else c.itemBg)
            .border(1.dp, if (selected) c.itemActiveBorder else c.itemBorder, shape),
        content = content,
    )
}

/** 统一分隔线。 */
@Composable
fun MsvDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = Msv.colors.divider, thickness = 1.dp)
}

/** 统一可选标签（芯片）。 */
@Composable
fun MsvChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val c = Msv.colors
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (selected) c.itemActiveBg else c.controlBg)
            .border(1.dp, if (selected) c.itemActiveBorder else c.controlBorder, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = MsvSpacing.sm, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) c.accent else c.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
