package com.music.msv.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.Job
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
    var flipJob by remember { mutableStateOf<Job?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var pendingFlipDir by remember { mutableStateOf(0) }

    val isZoomed = zoom > 1.01f || abs(panOffsetX) > 1f || abs(panOffsetY) > 1f
    val pw = if (pageWidth > 0) pageWidth.toFloat() else stageWidth.toFloat()

    val currentIsZoomed by rememberUpdatedState(isZoomed)
    val currentPageIndex by rememberUpdatedState(currentPage)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentPw by rememberUpdatedState(pw)

    fun baseX(pageIndex: Int): Float {
        val cp = currentPage
        return if (pageIndex >= cp) 0f else -(cp - pageIndex).toFloat() * pw
    }

    fun pageX(pageIndex: Int): Float {
        val base = baseX(pageIndex)
        val t = transition.value
        return when {
            t < 0 && pageIndex == currentPage -> base + t
            t > 0 && pageIndex == currentPage - 1 -> base + t
            else -> base
        }
    }

    fun forceCompletePendingFlip() {
        val d = pendingFlipDir
        if (d != 0) {
            pendingFlipDir = 0
            if (d > 0) onNextPage() else onPrevPage()
        }
    }

    fun doClickFlip(dir: Int) {
        if (pw <= 0f) return
        if (currentPageIndex + dir !in 0 until currentPageCount) return
        forceCompletePendingFlip()
        flipJob?.cancel()
        flipJob = scope.launch {
            transition.snapTo(0f)
            transition.animateTo(-dir * pw, tween(200, easing = FastOutSlowInEasing))
            transition.snapTo(0f)
            if (dir > 0) onNextPage() else onPrevPage()
        }
    }

    val pagesToShow = if (pageCount > 0) {
        ((currentPage - 3).coerceAtLeast(0)..(currentPage + 3).coerceAtMost(pageCount - 1))
            .sortedByDescending { it }
    } else emptyList()

    val pageSizeModifier: Modifier = if (pageWidth > 0) {
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
                        var activeDir = 0
                        while (true) {
                            detectHorizontalDragGestures(
                            onDragStart = {
                                forceCompletePendingFlip()
                                activeDir = 0
                                dragOffset = 0f
                                flipJob?.cancel()
                                scope.launch { transition.snapTo(0f) }
                            },
                            onDragEnd = {
                                if (currentIsZoomed || currentPw <= 0f) return@detectHorizontalDragGestures
                                if (activeDir == 0) return@detectHorizontalDragGestures
                                val threshold = currentPw * SWIPE_THRESHOLD
                                val finalV = dragOffset
                                if (abs(finalV) > threshold) {
                                    val dir = activeDir
                                    pendingFlipDir = dir
                                    flipJob = scope.launch {
                                        transition.snapTo(finalV)
                                        transition.animateTo(-dir * currentPw, spring(dampingRatio = 0.85f, stiffness = 350f))
                                        transition.snapTo(0f)
                                        if (dir > 0) onNextPage() else onPrevPage()
                                        pendingFlipDir = 0
                                    }
                                } else {
                                    flipJob = scope.launch {
                                        transition.snapTo(finalV)
                                        transition.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 500f))
                                    }
                                }
                            },
                                onDragCancel = {
                                    flipJob = scope.launch {
                                        transition.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 500f))
                                    }
                                },
                                onHorizontalDrag = { _, amount ->
                                    if (currentIsZoomed) return@detectHorizontalDragGestures
                                    if (activeDir == 0) {
                                        val dir = if (amount < 0) 1 else -1
                                        if (currentPageIndex + dir !in 0 until currentPageCount) return@detectHorizontalDragGestures
                                        flipJob?.cancel()
                                        activeDir = dir
                                        dragOffset = 0f
                                    }
                                    dragOffset = if (activeDir > 0)
                                        (dragOffset + amount).coerceIn(-currentPw, 0f)
                                    else
                                        (dragOffset + amount).coerceIn(0f, currentPw)
                                    scope.launch { transition.snapTo(dragOffset) }
                                }
                            )
                        }
                    }
                    launch {
                        detectTapGestures(
                            onTap = { pos ->
                                if (currentIsZoomed) return@detectTapGestures
                                val sw = stageWidth
                                if (sw <= 0) return@detectTapGestures
                                val third = sw / 3f
                                when {
                                    pos.x < third -> doClickFlip(-1)
                                    pos.x > sw - third -> doClickFlip(1)
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
                    .then(pageSizeModifier)
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
