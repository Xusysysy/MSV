package com.music.msv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 语义圆角标度：全应用统一使用，取代此前散落的 12 种圆角。 */
val MsvShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // 标签 / 小徽章
    small = RoundedCornerShape(12.dp),       // 卡片 / 列表项
    medium = RoundedCornerShape(16.dp),      // 内嵌控件
    large = RoundedCornerShape(22.dp),       // 弹窗 / 侧栏
    extraLarge = RoundedCornerShape(28.dp),  // 外壳
)

val ShellShape = RoundedCornerShape(28.dp)
val ShellFullscreenShape = RoundedCornerShape(0.dp)
val TopbarShape = RoundedCornerShape(20.dp)
val ButtonShape = RoundedCornerShape(50)
val PageDisplayShape = RoundedCornerShape(50)
val PanelShape = RoundedCornerShape(22.dp, 0.dp, 22.dp, 0.dp)
val ThumbnailItemShape = RoundedCornerShape(12.dp)
val ThumbnailThumbShape = RoundedCornerShape(8.dp)
val FooterShape = RoundedCornerShape(50)
