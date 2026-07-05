package com.music.msv.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var stageWidth by remember { mutableStateOf(0) }
    var currentZoom by remember { mutableFloatStateOf(zoom) }
    val transition = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var rawDragOffset by remember { mutableFloatStateOf(0f) }
    var isAnimFlip by remember { mutableStateOf(false) }
    var flipDir by remember { mutableIntStateOf(0) }

    val isZoomed = zoom > 1.01f || abs(panOffsetX) > 1f || abs(panOffsetY) > 1f
    val pw = if (pageWidth > 0) pageWidth.toFloat() else stageWidth.toFloat()

    val currentIsZoomed by rememberUpdatedState(isZoomed)
    val currentIsAnimFlip by rememberUpdatedState(isAnimFlip)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentPageIndex by rememberUpdatedState(currentPage)
    val currentPw by rememberUpdatedState(pw)

    fun baseX(pageIndex: Int, cp: Int): Float =
        if (pageIndex >= cp) 0f else -(cp - pageIndex).toFloat() * pw

    fun pageX(pageIndex: Int): Float {
        val base = baseX(pageIndex, currentPage)
        if (!isAnimFlip) return base
        return when {
            flipDir > 0 && pageIndex == currentPage -> base + transition.value
            flipDir < 0 && pageIndex == currentPage - 1 -> base + transition.value
            else -> base
        }
    }

    fun animateFlip(dir: Int, from: Float = 0f) {
        if (pw <= 0f) return
        val target = currentPageIndex + dir
        if (target !in 0 until currentPageCount) return
        isAnimFlip = true
        flipDir = dir
        scope.launch {
            transition.snapTo(from)
            transition.animateTo(-dir * pw, spring(dampingRatio = 0.8f, stiffness = 300f))
            transition.snapTo(0f)
            isAnimFlip = false
            flipDir = 0
            if (dir > 0) onNextPage() else onPrevPage()
        }
    }

    val pagesToShow = if (pageCount > 0) {
        ((currentPage - 3).coerceAtLeast(0)..(currentPage + 3).coerceAtMost(pageCount - 1))
            .sortedByDescending { it }
    } else emptyList()

    val pageModifier: Modifier = if (pageWidth > 0) {
        Modifier.size(pageWidth.dp, pageHeight.dp)
    } else {
        Modifier.fillMaxSize()
    }

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
                        var dragDir = 0
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragDir = 0
                                rawDragOffset = 0f
                            },
                            onDragEnd = {
                                if (currentIsZoomed || currentPw <= 0f) {
                                    rawDragOffset = 0f
                                    return@detectHorizontalDragGestures
                                }
                                if (dragDir != 0) {
                                    val threshold = currentPw * SWIPE_THRESHOLD
                                    if (abs(rawDragOffset) > threshold) {
                                        animateFlip(dragDir, rawDragOffset)
                                    } else {
                                        scope.launch {
                                            transition.snapTo(rawDragOffset)
                                            transition.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 500f))
                                            isAnimFlip = false
                                            flipDir = 0
                                        }
                                    }
                                } else {
                                    isAnimFlip = false
                                    flipDir = 0
                                }
                                rawDragOffset = 0f
                            },
                            onDragCancel = {
                                scope.launch {
                                    transition.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 500f))
                                }
                                isAnimFlip = false
                                flipDir = 0
                                rawDragOffset = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (currentIsZoomed || currentIsAnimFlip) return@detectHorizontalDragGestures
                                if (dragDir == 0) {
                                    val testDir = if (dragAmount < 0) 1 else -1
                                    if ((currentPageIndex + testDir) in 0 until currentPageCount) {
                                        dragDir = testDir
                                        flipDir = testDir
                                        isAnimFlip = true
                                    }
                                }
                                if (dragDir != 0) {
                                    rawDragOffset = if (dragDir > 0)
                                        (rawDragOffset + dragAmount).coerceIn(-currentPw, 0f)
                                    else
                                        (rawDragOffset + dragAmount).coerceIn(0f, currentPw)
                                    scope.launch { transition.snapTo(rawDragOffset) }
                                }
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
                                    pos.x < third -> animateFlip(-1)
                                    pos.x > sw - third -> animateFlip(1)
                                    else -> onCenterTap()
                                }
                            },
                            onDoubleTap = { onDoubleTap() }
                        )
                    }
                }
            }
    ) {
        for (pageIndex in pagesToShow) {
            val uri = pageUris[pageIndex] ?: continue
            Box(
                modifier = Modifier
                    .offset { IntOffset(pageX(pageIndex).roundToInt(), 0) }
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
    }
}
