package com.music.msv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
private const val SWIPE_THRESHOLD = 0.3f

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
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = if (isDragging) tween(0) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
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
            .pointerInput(isZoomed, stageSize) {
                if (!isZoomed && stageSize.width > 0) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = stageSize.width * SWIPE_THRESHOLD
                            if (abs(animatedOffset) > threshold) {
                                if (animatedOffset < 0) onSwipeLeft() else onSwipeRight()
                            }
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isDragging) isDragging = true
                            val newOffset = dragOffset + dragAmount
                            val maxDrag = stageSize.width.toFloat()
                            dragOffset = newOffset.coerceIn(-maxDrag, maxDrag)
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
        val peekProgress = (abs(animatedOffset) / stageSize.width.toFloat()).coerceIn(0f, 1f)

        if (!isZoomed && peekProgress > 0.01f && stageSize.width > 0) {
            val peekDir = if (animatedOffset < 0) 1 else -1
            val peekUri = if (animatedOffset < 0) nextUri else prevUri

            if (peekUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(peekUri)
                            .build(),
                        contentDescription = "peek page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            val stackAlpha = (peekProgress * 0.4f).coerceAtMost(0.4f)
            val stacks = minOf(
                if (animatedOffset < 0) pageCount - currentPage - 1 else currentPage,
                5
            ).coerceAtLeast(0)
            if (stacks > 0) {
                Box(
                    modifier = Modifier
                        .align(if (animatedOffset < 0) Alignment.CenterEnd else Alignment.CenterStart)
                        .alpha(stackAlpha)
                ) {
                    for (i in 0 until stacks) {
                        Box(
                            modifier = Modifier
                                .offset(x = (peekDir * i * 2).dp)
                                .width(3.dp)
                                .alpha(0.3f - i * 0.05f)
                                .shadow(1.dp)
                                .background(shadowColor)
                        )
                    }
                }
            }
        }

        val currentOffsetX = animatedOffset.roundToInt()
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
                        .offset { IntOffset(currentOffsetX, 0) }
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

        if (!isZoomed && abs(animatedOffset) < 1f && pageCount > 1) {
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
