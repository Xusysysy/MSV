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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onPreloadAround: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFF0F1220) else Color(0xFFDFE6F5)
    var stageWidth by remember { mutableStateOf(0) }
    var currentZoom by remember { mutableFloatStateOf(zoom) }
    val transition = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var flipJob by remember { mutableStateOf<Job?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

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

    fun doFlip(dir: Int, fromOffset: Float, easing: Boolean) {
        if (currentPw <= 0f) return
        if (currentPageIndex + dir !in 0 until currentPageCount) return
        flipJob?.cancel()
        flipJob = scope.launch {
            transition.snapTo(fromOffset)
            try {
                if (easing) {
                    transition.animateTo(-dir * currentPw, tween(200, easing = FastOutSlowInEasing))
                } else {
                    transition.animateTo(-dir * currentPw, spring(dampingRatio = 0.85f, stiffness = 350f))
                }
            } finally {
                withContext(NonCancellable) {
                    transition.snapTo(0f)
                    if (dir > 0) onNextPage() else onPrevPage()
                }
            }
        }
    }

    fun doBounce(dir: Int) {
        if (currentPw <= 0f) return
        flipJob?.cancel()
        flipJob = scope.launch {
            transition.snapTo(0f)
            val overshoot = -dir * currentPw * 0.08f
            transition.animateTo(overshoot, tween(100, easing = FastOutSlowInEasing))
            transition.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
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
            .pointerInput(pageCount) {
                var activeDir = 0
                while (true) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            flipJob?.cancel()
                            activeDir = 0
                            dragOffset = 0f
                            scope.launch { transition.snapTo(0f) }
                        },
                        onDragEnd = {
                            if (currentIsZoomed || currentPw <= 0f) return@detectHorizontalDragGestures
                            if (activeDir == 0) return@detectHorizontalDragGestures
                            val dir = activeDir
                            val atBoundary = currentPageIndex + dir !in 0 until currentPageCount
                            when {
                                atBoundary -> {
                                    flipJob = scope.launch {
                                        transition.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 350f))
                                    }
                                }
                                abs(dragOffset) > currentPw * SWIPE_THRESHOLD -> {
                                    doFlip(dir, dragOffset, easing = false)
                                }
                                else -> {
                                    flipJob = scope.launch {
                                        transition.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 500f))
                                    }
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
                                val inRange = currentPageIndex + dir in 0 until currentPageCount
                                flipJob?.cancel()
                                activeDir = dir
                                dragOffset = 0f
                                if (inRange) onPreloadAround(currentPageIndex + dir)
                            }
                            val inRange = currentPageIndex + activeDir in 0 until currentPageCount
                            val factor = if (inRange) 1f else 0.25f
                            val limit = if (inRange) currentPw else currentPw * 0.25f
                            dragOffset = if (activeDir > 0)
                                (dragOffset + amount * factor).coerceIn(-limit, 0f)
                            else
                                (dragOffset + amount * factor).coerceIn(0f, limit)
                            scope.launch { transition.snapTo(dragOffset) }
                        }
                    )
                }
            }
            .pointerInput(pageCount) {
                detectTapGestures(
                    onTap = { pos ->
                        if (currentIsZoomed) return@detectTapGestures
                        val sw = stageWidth
                        if (sw <= 0) return@detectTapGestures
                        val third = sw / 3f
                        when {
                            pos.x < third -> {
                                if (currentPageIndex > 0) {
                                    onPreloadAround(currentPageIndex - 1)
                                    doFlip(-1, 0f, easing = true)
                                } else {
                                    doBounce(-1)
                                }
                            }
                            pos.x > sw - third -> {
                                if (currentPageIndex < currentPageCount - 1) {
                                    onPreloadAround(currentPageIndex + 1)
                                    doFlip(1, 0f, easing = true)
                                } else {
                                    doBounce(1)
                                }
                            }
                            else -> onCenterTap()
                        }
                    },
                    onDoubleTap = { onDoubleTap() }
                )
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
