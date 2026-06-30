package com.music.msv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.abs
import kotlin.math.roundToInt

private val PAPER_SHADOW_ELEVATION = 6.dp
private const val SWIPE_THRESHOLD = 0.25f
private const val SWIPE_VELOCITY_THRESHOLD = 800f

@Composable
fun Stage(
    isDark: Boolean,
    contentUri: Any?,
    prevUri: Any?,
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
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val dragProgress by animateFloatAsState(
        targetValue = dragAccumulator,
        animationSpec = tween(100)
    )

    val isZoomed = zoom > 1.01f || abs(panOffsetX) > 1f || abs(panOffsetY) > 1f

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
            .pointerInput(isZoomed) {
                if (!isZoomed) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = stageSize.width * SWIPE_THRESHOLD
                            if (abs(dragAccumulator) > threshold) {
                                if (dragAccumulator < 0) onSwipeLeft() else onSwipeRight()
                            }
                            dragAccumulator = 0f
                        },
                        onDragCancel = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                        }
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val thirdWidth = stageSize.width / 3f
                        when {
                            offset.x < thirdWidth -> onEdgeLeftTap()
                            offset.x > stageSize.width - thirdWidth -> onEdgeRightTap()
                            else -> onCenterTap()
                        }
                    },
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        AnimatedContent(
            targetState = contentUri,
            transitionSpec = {
                val dir = if (isGoingForward) 1 else -1
                (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { dir * it } +
                        fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -dir * it } +
                                fadeOut(tween(160)))
            }
        ) { uri ->
            if (uri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .build(),
                        contentDescription = "page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(PAPER_SHADOW_ELEVATION, RectangleShape)
                    )
                }
            }
        }

        if (!isZoomed && pageCount > 1 && dragAccumulator != 0f) {
            val fraction = (dragProgress / stageSize.width).coerceIn(-0.5f, 0.5f)
            val peekAlpha = abs(fraction) * 0.4f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset((fraction * stageSize.width * 0.3f).roundToInt(), 0) }
                    .alpha(peekAlpha)
            )
        }

        if (!isZoomed && pageCount > 1) {
            val pageStack = minOf(pageCount - currentPage - 1, 5).coerceAtLeast(0)
            if (pageStack > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 0.dp)
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

            val prevStack = minOf(currentPage, 5).coerceAtLeast(0)
            if (prevStack > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 0.dp)
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
