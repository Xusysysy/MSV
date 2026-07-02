package com.music.msv.ui.components

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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val PAPER_SHADOW_ELEVATION = 6.dp
private const val SWIPE_THRESHOLD = 0.30f

@Composable
fun Stage(
    isDark: Boolean,
    contentUri: Any?,
    prevUri: Any?,
    nextUri: Any?,
    zoom: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    showUI: Boolean,
    isGoingForward: Boolean,
    pageCount: Int,
    currentPage: Int,
    onEdgeLeftTap: () -> Unit,
    onEdgeRightTap: () -> Unit,
    onCenterTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float, Float) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onViewportSizeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFF0F1220) else Color(0xFFDFE6F5)
    val shadowColor = if (isDark) Color(0x33000000) else Color(0x20000000)
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var currentZoom by remember { mutableFloatStateOf(zoom) }
    val transition = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var rawDragOffset by remember { mutableFloatStateOf(0f) }
    var isAnimFlip by remember { mutableStateOf(false) }

    val isZoomed = zoom > 1.01f || abs(panOffsetX) > 1f || abs(panOffsetY) > 1f
    val offset = transition.value
    val vw = stageSize.width.toFloat()

    val currentIsZoomed by rememberUpdatedState(isZoomed)
    val currentIsAnimFlip by rememberUpdatedState(isAnimFlip)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentPageIndex by rememberUpdatedState(currentPage)

    fun animateFlip(dir: Int) {
        if (isAnimFlip || vw <= 0f) return
        val target = currentPageIndex + dir
        if (target !in 0 until currentPageCount) return
        isAnimFlip = true
        scope.launch {
            transition.animateTo(-dir * vw, tween(280, easing = FastOutSlowInEasing))
            transition.snapTo(0f)
            isAnimFlip = false
            if (dir < 0) onSwipeLeft() else onSwipeRight()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .onSizeChanged {
                stageSize = it
                onViewportSizeChanged(it.width, it.height)
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
                                if (currentIsZoomed || currentIsAnimFlip || vw <= 0f) {
                                    rawDragOffset = 0f
                                    return@detectHorizontalDragGestures
                                }
                                val threshold = vw * SWIPE_THRESHOLD
                                val finalOffset = rawDragOffset
                                val dir = -finalOffset.sign.toInt()
                                if (abs(finalOffset) > threshold && dir != 0) {
                                    val targetPage = currentPageIndex + dir
                                    if (targetPage in 0 until currentPageCount) {
                                        isAnimFlip = true
                                        scope.launch {
                                            transition.animateTo(
                                                -dir * vw,
                                                tween(200, easing = FastOutSlowInEasing)
                                            )
                                            transition.snapTo(0f)
                                            isAnimFlip = false
                                            if (dir < 0) onSwipeLeft() else onSwipeRight()
                                        }
                                    } else {
                                        scope.launch { transition.animateTo(0f, tween(150)) }
                                    }
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
                                rawDragOffset = (rawDragOffset + dragAmount).coerceIn(-vw, vw)
                                scope.launch { transition.snapTo(rawDragOffset) }
                            }
                        )
                    }
                    launch {
                        detectTapGestures(
                            onTap = { pos ->
                                if (currentIsAnimFlip || currentIsZoomed) return@detectTapGestures
                                val sw = stageSize.width
                                if (sw <= 0) return@detectTapGestures
                                val third = sw / 3f
                                when {
                                    pos.x < third -> animateFlip(1)
                                    pos.x > sw - third -> animateFlip(-1)
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

        if (!isZoomed && vw > 0f) {
            if (prevUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .offset { IntOffset(currentOffset - vw.roundToInt(), 0) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(prevUri).build(),
                        contentDescription = "prev",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (nextUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .offset { IntOffset(currentOffset + vw.roundToInt(), 0) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(nextUri).build(),
                        contentDescription = "next",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (contentUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .offset { IntOffset(currentOffset, 0) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(contentUri).build(),
                    contentDescription = "page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(PAPER_SHADOW_ELEVATION, RectangleShape)
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
