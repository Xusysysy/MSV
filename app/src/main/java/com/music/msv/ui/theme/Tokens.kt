package com.music.msv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * MSV 语义设计令牌 —— 全应用唯一的颜色事实源。
 *
 * 设计约束（用户要求）：所有弹窗与界面必须共享同一套设计语言。
 * 因此组件一律通过 [Msv.colors] 取色，**不得**再写 `if (isDark) Color(0x…) else Color(0x…)`。
 * 令牌取值来自 ui/theme/Color.kt 的既有色板（择一为权威值），以修复此前的取值漂移：
 * - 浮层/弹窗底色统一为 深 0xF00F121C / 浅 0xF2FFFFFF（原先存在 0xFF、0x99 等变体）
 * - 菜单等抬升面统一为 深 0xFF1B1F2E / 浅 0xFFE8EDF5（原先存在 0xFF1A1E2E 等非令牌值）
 */
@Immutable
data class MsvColors(
    /** 应用底色 */
    val appBg: Color,
    /** 外壳（内容卡片）底色 */
    val shellBg: Color,
    /** 谱面舞台底色 */
    val stageBg: Color,
    /** 顶部/底部悬浮工具条底色（半透明玻璃） */
    val barBg: Color,
    /** 悬浮工具条描边 */
    val barBorder: Color,
    /** 浮层/弹窗/侧栏主底色（玻璃面） */
    val surface: Color,
    /** 玻璃面描边 */
    val surfaceBorder: Color,
    /** 抬升面：下拉菜单、气泡、对话框内嵌卡片 */
    val surfaceElevated: Color,
    /** 控件底色（未选中） */
    val controlBg: Color,
    /** 控件底色（悬停/按下） */
    val controlBgHover: Color,
    /** 控件描边 */
    val controlBorder: Color,
    /** 控件描边（悬停/按下） */
    val controlBorderHover: Color,
    /** 主文本 */
    val text: Color,
    /** 次要文本 */
    val textMuted: Color,
    /** 强调色（品牌色） */
    val accent: Color,
    /** 强调色之上的前景色 */
    val onAccent: Color,
    /** 危险/破坏性操作 */
    val danger: Color,
    /** 成功/在线状态 */
    val success: Color,
    /** 分隔线 */
    val divider: Color,
    /** 遮罩（弹窗背后） */
    val scrim: Color,
    /** 列表项/卡片底色 */
    val itemBg: Color,
    /** 列表项/卡片描边 */
    val itemBorder: Color,
    /** 列表项激活底色 */
    val itemActiveBg: Color,
    /** 列表项激活描边 */
    val itemActiveBorder: Color,
    /** 缩略图占位底色 */
    val thumbBg: Color,
    /** 进度指示轨道 */
    val spinnerTrack: Color,
)

internal val DarkMsvColors = MsvColors(
    appBg = DarkAppBg,
    shellBg = DarkShellBg,
    stageBg = DarkStageBg,
    barBg = DarkTopbarBg,
    barBorder = DarkTopbarBorder,
    surface = DarkPanelBg,
    surfaceBorder = DarkPanelBorder,
    surfaceElevated = DarkSurfaceVariant,
    controlBg = DarkControlBg,
    controlBgHover = DarkControlBgHover,
    controlBorder = DarkControlBorder,
    controlBorderHover = DarkControlBorderHover,
    text = DarkText,
    textMuted = DarkMuted,
    accent = DarkAccent,
    onAccent = DarkAppBg,
    danger = DarkDanger,
    success = Color(0xFF4ADE80),
    divider = DarkDivider,
    scrim = DarkShadeBg,
    itemBg = DarkThumbnailItemBg,
    itemBorder = DarkThumbnailItemBorder,
    itemActiveBg = DarkThumbnailItemActiveBg,
    itemActiveBorder = DarkThumbnailItemActiveBorder,
    thumbBg = DarkThumbnailThumbBg,
    spinnerTrack = DarkSpinnerTrack,
)

internal val LightMsvColors = MsvColors(
    appBg = LightAppBg,
    shellBg = LightShellBg,
    stageBg = LightStageBg,
    barBg = LightTopbarBg,
    barBorder = LightTopbarBorder,
    surface = LightPanelBg,
    surfaceBorder = LightPanelBorder,
    surfaceElevated = LightSurfaceVariant,
    controlBg = LightControlBg,
    controlBgHover = LightControlBgHover,
    controlBorder = LightControlBorder,
    controlBorderHover = LightControlBorderHover,
    text = LightText,
    textMuted = LightMuted,
    accent = LightAccent,
    onAccent = Color(0xFFFFFFFF),
    danger = LightDanger,
    success = Color(0xFF16A34A),
    divider = LightDivider,
    scrim = LightShadeBg,
    itemBg = LightThumbnailItemBg,
    itemBorder = LightThumbnailItemBorder,
    itemActiveBg = LightThumbnailItemActiveBg,
    itemActiveBorder = LightThumbnailItemActiveBorder,
    thumbBg = LightThumbnailThumbBg,
    spinnerTrack = LightSpinnerTrack,
)

val LocalMsvColors = staticCompositionLocalOf { DarkMsvColors }

/** 语义令牌访问器：组件通过 `Msv.colors.xxx` 取色。 */
object Msv {
    val colors: MsvColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMsvColors.current
}

/**
 * 间距标度：全应用只使用这 6 档，取代此前散落的 16 种 padding 取值。
 */
object MsvSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
