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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

private val invertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

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
    isSpreadMode: Boolean,
    onCenterTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float, Float) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onViewportSizeChanged: (Int, Int) -> Unit,
    onSpreadModeChanged: (Boolean) -> Unit,
    onPreloadAround: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFF0F1220) else Color(0xFFDFE6F5)
    var stageWidth by remember { mutableStateOf(0) }
    var stageHeight by remember { mutableStateOf(0) }
    var currentZoom by remember { mutableFloatStateOf(zoom) }
    val transition = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var flipJob by remember { mutableStateOf<Job?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val isZoomed = zoom > 1.01f || abs(panOffsetX) > 1f || abs(panOffsetY) > 1f
    val pw = if (pageWidth > 0) pageWidth.toFloat() else stageWidth.toFloat()

    val displaySize = if (pageWidth > 0 && pageHeight > 0 && stageWidth > 0 && stageHeight > 0) {
        val pageAspect = pageWidth.toFloat() / pageHeight.toFloat()
        val stageAspect = stageWidth.toFloat() / stageHeight.toFloat()
        if (pageAspect > stageAspect) {
            val dw = stageWidth.toFloat()
            val dh = dw / pageAspect
            Pair(dw, dh)
        } else {
            val dh = stageHeight.toFloat()
            val dw = dh * pageAspect
            Pair(dw, dh)
        }
    } else {
        null
    }

    val autoSpreadMode = if (displaySize != null && stageWidth > stageHeight) {
        displaySize.first < stageWidth.toFloat() / 2f
    } else false

    LaunchedEffect(autoSpreadMode) {
        onSpreadModeChanged(autoSpreadMode)
    }

    val spreadW = if (isSpreadMode && displaySize != null) displaySize.first * 2f else 0f
    val spreadOffsetX = if (isSpreadMode && stageWidth > 0) (stageWidth - spreadW) / 2f else 0f

    val currentIsZoomed by rememberUpdatedState(isZoomed)
    val currentPageIndex by rememberUpdatedState(currentPage)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentPw by rememberUpdatedState(pw)
    val displayW = displaySize?.first ?: pw
    val currentIsSpread by rememberUpdatedState(isSpreadMode)
    val flipUnit = if (currentIsSpread) displayW * 2f else displayW

    fun baseX(pageIndex: Int): Float {
        val cp = currentPage
        return if (pageIndex >= cp) 0f else -(cp - pageIndex).toFloat() * displayW
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
        if (flipUnit <= 0f) return
        val step = if (currentIsSpread) 2 else 1
        if (currentPageIndex + dir * step !in 0 until currentPageCount) return
        flipJob?.cancel()
        flipJob = scope.launch {
            transition.snapTo(fromOffset)
            try {
                if (easing) {
                    transition.animateTo(
                        -dir * flipUnit,
                        spring(dampingRatio = 0.75f, stiffness = 280f)
                    )
                } else {
                    transition.animateTo(
                        -dir * flipUnit,
                        spring(dampingRatio = 0.8f, stiffness = 300f)
                    )
                }
            } finally {
                withContext(NonCancellable) {
                    if (dir > 0) onNextPage() else onPrevPage()
                    transition.snapTo(0f)
                }
            }
        }
    }

    fun doBounce(dir: Int) {
        if (flipUnit <= 0f) return
        flipJob?.cancel()
        flipJob = scope.launch {
            transition.snapTo(0f)
            val overshoot = -dir * flipUnit * 0.06f
            transition.animateTo(overshoot, tween(80, easing = FastOutSlowInEasing))
            transition.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 450f))
        }
    }

    val pagesToShow = if (pageCount > 0) {
        val spreadPages = if (currentIsSpread) 6 else 3
        ((currentPage - spreadPages).coerceAtLeast(0)..(currentPage + spreadPages).coerceAtMost(pageCount - 1))
            .sortedByDescending { it }
    } else emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .onSizeChanged {
                stageWidth = it.width
                stageHeight = it.height
                if (pageWidth <= 0) onViewportSizeChanged(it.width, it.height)
            }
            .pointerInput(pageCount) {
                var activeDir = 0
                var reversed = false
                while (true) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            flipJob?.cancel()
                            activeDir = 0
                            reversed = false
                            dragOffset = 0f
                            scope.launch { transition.snapTo(0f) }
                        },
                        onDragEnd = {
                            if (currentIsZoomed || currentPw <= 0f) return@detectHorizontalDragGestures
                            if (activeDir == 0) return@detectHorizontalDragGestures
                            val dir = activeDir
                            val atBoundary = currentPageIndex + dir !in 0 until currentPageCount
                            if (atBoundary || reversed) {
                                flipJob = scope.launch {
                                    transition.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 400f))
                                }
                            } else {
                                doFlip(dir, dragOffset, easing = false)
                            }
                        },
                        onDragCancel = {
                            flipJob = scope.launch {
                                transition.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 450f))
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
                                reversed = false
                                if (inRange) onPreloadAround(currentPageIndex + dir)
                            }
                            if (amount * activeDir > 0) reversed = true
                            val inRange = currentPageIndex + activeDir in 0 until currentPageCount
                            val factor = if (inRange) 1f else 0.25f
                            val limit = if (inRange) flipUnit else flipUnit * 0.25f
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
        val (dw, dh) = displaySize ?: Pair(stageWidth.toFloat(), stageHeight.toFloat())
        val centerX = if (stageWidth > 0) (stageWidth - dw) / 2f else 0f
        val centerY = if (stageHeight > 0) (stageHeight - dh) / 2f else 0f

        if (isSpreadMode && spreadOffsetX > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, 0) }
                    .size(
                        with(LocalDensity.current) { spreadOffsetX.toDp() },
                        with(LocalDensity.current) { stageHeight.toFloat().toDp() }
                    )
                    .background(bg)
            )
        }

        for (pageIndex in pagesToShow) {
            val uri = pageUris[pageIndex] ?: continue
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (pageX(pageIndex) + centerX).roundToInt(),
                            centerY.roundToInt()
                        )
                    }
                    .size(
                        with(LocalDensity.current) { dw.toDp() },
                        with(LocalDensity.current) { dh.toDp() }
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(uri).build(),
                    contentDescription = "page $pageIndex",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = if (isDark) ColorFilter.colorMatrix(invertColorMatrix) else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(6.dp, RectangleShape)
                )
            }
        }
    }
}
