package com.music.msv.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private const val SWIPE_THRESHOLD = 0.30f

@Composable
fun Stage(
    isDark: Boolean,
    pageUris: Map<Int, Uri>,
    currentPage: Int,
    pageCount: Int,
    pageWidth: Int,
    pageHeight: Int,
    zoom: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    onCenterTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float, Float) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onViewportSizeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFF0F1220) else Color(0xFFDFE6F5)
    val shadowColor = if (isDark) Color(0x33000000) else Color(0x20000000)
    var stageWidth by remember { mutableStateOf(0) }
    var currentZoom by remember { mutableFloatStateOf(zoom) }
    val transition = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var rawDragOffset by remember { mutableFloatStateOf(0f) }
    var isAnimFlip by remember { mutableStateOf(false) }

    val isZoomed = zoom > 1.01f || abs(panOffsetX) > 1f || abs(panOffsetY) > 1f
    val offset = transition.value
    val pw = if (pageWidth > 0) pageWidth.toFloat() else stageWidth.toFloat()
    val ph = if (pageHeight > 0) pageHeight else stageWidth

    val currentIsZoomed by rememberUpdatedState(isZoomed)
    val currentIsAnimFlip by rememberUpdatedState(isAnimFlip)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentPageIndex by rememberUpdatedState(currentPage)
    val currentPw by rememberUpdatedState(pw)

    fun doFlip(dir: Int) {
        if (isAnimFlip || pw <= 0f) return
        val target = currentPageIndex + dir
        if (target !in 0 until currentPageCount) return
        isAnimFlip = true
        scope.launch {
            transition.animateTo(
                -dir * pw,
                tween(280, easing = FastOutSlowInEasing)
            )
            transition.snapTo(0f)
            isAnimFlip = false
            if (dir > 0) onNextPage() else onPrevPage()
        }
    }

    val pagesToShow = if (pageCount > 0) {
        ((currentPage - 3).coerceAtLeast(0)..(currentPage + 3).coerceAtMost(pageCount - 1))
            .sortedByDescending { it }
    } else emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .onSizeChanged {
                stageWidth = it.width
                if (pageWidth <= 0) onViewportSizeChanged(it.width, it.height)
            }
            .pointerInput(isZoomed) {
                if (isZoomed) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        currentZoom = (currentZoom * zoomChange).coerceIn(0.5f, 8f)
                        onZoomChange(currentZoom)
                        onPanChange(pan.x, pan.y)
                    }
                }
            }
            .pointerInput(Unit) {
                kotlinx.coroutines.coroutineScope {
                    launch {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (currentIsZoomed || currentIsAnimFlip || currentPw <= 0f) {
                                    rawDragOffset = 0f
                                    return@detectHorizontalDragGestures
                                }
                                val threshold = currentPw * SWIPE_THRESHOLD
                                val finalOffset = rawDragOffset
                                val dir = -finalOffset.sign.toInt()
                                if (abs(finalOffset) > threshold && dir != 0) {
                                    doFlip(dir)
                                } else {
                                    scope.launch { transition.animateTo(0f, tween(160, easing = FastOutSlowInEasing)) }
                                }
                                rawDragOffset = 0f
                            },
                            onDragCancel = {
                                rawDragOffset = 0f
                                scope.launch { transition.animateTo(0f, tween(150)) }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (currentIsZoomed || currentIsAnimFlip) return@detectHorizontalDragGestures
                                rawDragOffset =
                                    (rawDragOffset + dragAmount).coerceIn(-currentPw, currentPw)
                                scope.launch { transition.snapTo(rawDragOffset) }
                            }
                        )
                    }
                    launch {
                        detectTapGestures(
                            onTap = { pos ->
                                if (currentIsAnimFlip || currentIsZoomed) return@detectTapGestures
                                val sw = stageWidth
                                if (sw <= 0) return@detectTapGestures
                                val third = sw / 3f
                                when {
                                    pos.x < third -> doFlip(-1)
                                    pos.x > sw - third -> doFlip(1)
                                    else -> onCenterTap()
                                }
                            },
                            onDoubleTap = { onDoubleTap() }
                        )
                    }
                }
            }
    ) {
        val currentOffset = offset.roundToInt()

        for (pageIndex in pagesToShow) {
            val uri = pageUris[pageIndex] ?: continue
            val baseX = (pageIndex - currentPage) * pw.roundToInt()
            val pageModifier = if (pageWidth > 0) {
                Modifier.size(pageWidth.dp, pageHeight.dp)
            } else {
                Modifier.fillMaxSize()
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(currentOffset + baseX, 0) }
                    .then(pageModifier)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(uri).build(),
                    contentDescription = "page $pageIndex",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(6.dp, RectangleShape)
                )
            }
        }

        if (!isZoomed && abs(offset) < 1f && pageCount > 1) {
            val pageStack = minOf(currentPageCount - currentPageIndex - 1, 5).coerceAtLeast(0)
            if (pageStack > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                ) {
                    for (i in 0 until pageStack) {
                        Box(
                            modifier = Modifier
                                .offset(x = (i * 2).dp)
                                .width(3.dp)
                                .alpha(0.18f - i * 0.03f)
                                .shadow(1.dp)
                                .background(shadowColor)
                        )
                    }
                }
            }
            val prevStack = minOf(currentPageIndex, 5).coerceAtLeast(0)
            if (prevStack > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                ) {
                    for (i in 0 until prevStack) {
                        Box(
                            modifier = Modifier
                                .offset(x = (-i * 2).dp)
                                .width(3.dp)
                                .alpha(0.18f - i * 0.03f)
                                .shadow(1.dp)
                                .background(shadowColor)
                        )
                    }
                }
            }
        }
    }
}
