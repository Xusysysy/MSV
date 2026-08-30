package com.music.msv.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** 单页渲染：升级换缓存时旧 URI 垫底显示（Coil 内存缓存命中），新图解码完成后覆盖，零空窗防闪烁 */
@Composable
private fun ScorePageImage(uri: Uri, pageIndex: Int, isDark: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(uri) }
    var previous by remember { mutableStateOf<Uri?>(null) }
    if (uri != current) {
        previous = current
        current = uri
    }
    Box(modifier) {
        previous?.let { prev ->
            AsyncImage(
                model = remember(prev) { ImageRequest.Builder(context).data(prev).build() },
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = if (isDark) ColorFilter.colorMatrix(invertColorMatrix) else null,
                modifier = Modifier.fillMaxSize()
            )
        }
        AsyncImage(
            model = remember(current) { ImageRequest.Builder(context).data(current).build() },
            contentDescription = "page $pageIndex",
            contentScale = ContentScale.FillBounds,
            colorFilter = if (isDark) ColorFilter.colorMatrix(invertColorMatrix) else null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

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
    pendingFlip: Int? = null,
    onCenterTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float, Float) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onFlipDone: () -> Unit = {},
    onViewportSizeChanged: (Int, Int) -> Unit,
    onSpreadModeChanged: (Boolean) -> Unit,
    onPreloadAround: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0xFF0F1220) else Color(0xFFDFE6F5)
    val context = LocalContext.current
    var stageWidth by remember { mutableStateOf(0) }
    var stageHeight by remember { mutableStateOf(0) }
    var currentZoom by remember { mutableFloatStateOf(zoom) }
    val transition = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var flipJob by remember { mutableStateOf<Job?>(null) }
    // 当前翻页动画方向（1=前向 -1=后向 0=无动画/拖拽中），用于区分动画期与拖拽期的页面定位
    var flipDir by remember { mutableStateOf(0) }
    // 触发翻页前的页码：动画期间以它锚定滑出页，避免 currentPage 更新滞后 1-2 帧时定位错乱（前一页闪现）
    var lastPage by remember { mutableStateOf(0) }
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

    val currentIsZoomed by rememberUpdatedState(isZoomed)
    val currentPageIndex by rememberUpdatedState(currentPage)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentPw by rememberUpdatedState(pw)
    val displayW = displaySize?.first ?: pw
    val currentDisplayW by rememberUpdatedState(displayW)
    val currentIsSpread by rememberUpdatedState(isSpreadMode)
    val flipUnit = if (currentIsSpread) displayW * 2f else displayW
    val currentFlipUnit by rememberUpdatedState(flipUnit)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentPendingFlip by rememberUpdatedState(pendingFlip)

    LaunchedEffect(pageWidth, pageHeight) {
        transition.snapTo(0f)
    }

    fun doFlip(dir: Int, fromOffset: Float, easing: Boolean) {
        if (currentFlipUnit <= 0f) return
        val step = if (currentIsSpread) 2 else 1
        if (currentPageIndex + dir * step !in 0 until currentPageCount) return
        flipJob?.cancel()
        flipDir = dir
        lastPage = currentPageIndex
        flipJob = scope.launch {
            val selfJob = coroutineContext[Job]
            transition.snapTo(fromOffset)
            // 触发瞬间即推进页码（页码显示/渲染/预加载全部以目标页为准），动画只负责视觉过渡
            if (dir > 0) onNextPage() else onPrevPage()
            try {
                if (easing) {
                    transition.animateTo(
                        -dir * currentFlipUnit,
                        spring(dampingRatio = 0.75f, stiffness = 280f)
                    )
                } else {
                    transition.animateTo(
                        -dir * currentFlipUnit,
                        spring(dampingRatio = 0.8f, stiffness = 300f)
                    )
                }
            } finally {
                withContext(NonCancellable) {
                    // 仅当本动画仍是当前动画时才复位：被新翻页接替的旧动画不得复位位置/方向，
                    // 否则其 snapTo(0)+flipDir=0 会打断新动画并导致页面闪回旧页
                    if (flipJob === selfJob) {
                        transition.snapTo(0f)
                        flipDir = 0
                    }
                    onFlipDone()
                }
            }
        }
    }

    fun doBounce(dir: Int) {
        if (currentFlipUnit <= 0f) return
        flipJob?.cancel()
        flipJob = scope.launch {
            transition.snapTo(0f)
            val overshoot = -dir * currentFlipUnit * 0.06f
            transition.animateTo(overshoot, tween(80, easing = FastOutSlowInEasing))
            transition.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 450f))
        }
    }

    LaunchedEffect(currentPendingFlip) {
        val dir = currentPendingFlip ?: return@LaunchedEffect
        if (currentFlipUnit <= 0f) return@LaunchedEffect
        val step = if (currentIsSpread) 2 else 1
        if (currentPendingFlip != null) {
            doFlip(dir, 0f, easing = true)
        }
    }

    val pagesToShow = if (pageCount > 0) {
        val spreadPages = if (currentIsSpread) 6 else 5
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
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentIsZoomed || currentPw <= 0f) return@awaitEachGesture
                    val downX = down.position.x
                    var lastX = downX
                    var isDrag = false
                    var activeDir = 0
                    var reversed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (!change.pressed) {
                            if (!isDrag) {
                                val sw = stageWidth
                                if (sw > 0) {
                                    val third = sw / 3f
                                    when {
                                        change.position.x < third -> {
                                            val step = if (currentIsSpread) 2 else 1
                                            if (currentPageIndex > 0) {
                                                onPreloadAround(currentPageIndex - step)
                                                doFlip(-1, 0f, easing = true)
                                            } else doBounce(-1)
                                        }
                                        change.position.x > sw - third -> {
                                            val step = if (currentIsSpread) 2 else 1
                                            if (currentPageIndex < currentPageCount - 1) {
                                                onPreloadAround(currentPageIndex + step)
                                                doFlip(1, 0f, easing = true)
                                            } else doBounce(1)
                                        }
                                        else -> onCenterTap()
                                    }
                                }
                            } else {
                                val dir = activeDir
                                val step = if (currentIsSpread) 2 else 1
                                val atBoundary = currentPageIndex + dir * step !in 0 until currentPageCount
                                if (atBoundary || reversed) {
                                    flipJob = scope.launch {
                                        transition.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 400f))
                                    }
                                } else {
                                    doFlip(dir, dragOffset, easing = false)
                                }
                            }
                            break
                        }
                        if (!isDrag) {
                            val dx = change.position.x - downX
                            if (abs(dx) > touchSlop) {
                                isDrag = true
                                activeDir = if (dx < 0) 1 else -1
                                reversed = false
                                dragOffset = 0f
                                flipJob?.cancel()
                                scope.launch { transition.snapTo(0f) }
                                val step = if (currentIsSpread) 2 else 1
                                val inRange = currentPageIndex + activeDir * step in 0 until currentPageCount
                                if (inRange) onPreloadAround(currentPageIndex + activeDir * step)
                            }
                        }
                        if (isDrag) {
                            val amount = change.position.x - lastX
                            lastX = change.position.x
                            if (amount * activeDir > 0) reversed = true
                            val step = if (currentIsSpread) 2 else 1
                            val inRange = currentPageIndex + activeDir * step in 0 until currentPageCount
                            val factor = if (inRange) 1f else 0.25f
                            val limit = if (inRange) currentFlipUnit else currentFlipUnit * 0.25f
                            dragOffset = if (activeDir > 0)
                                (dragOffset + amount * factor).coerceIn(-limit, 0f)
                            else
                                (dragOffset + amount * factor).coerceIn(0f, limit)
                            scope.launch { transition.snapTo(dragOffset) }
                        }
                    }
                }
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
        val t = transition.value

        if (currentIsSpread) {
            val spreadTotalW = dw * 2f
            val spreadCenterX = if (stageWidth > 0) (stageWidth - spreadTotalW) / 2f else 0f
            val centerY = if (stageHeight > 0) (stageHeight - dh) / 2f else 0f

            if (spreadCenterX > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, 0) }
                        .size(
                            with(LocalDensity.current) { spreadCenterX.toDp() },
                            with(LocalDensity.current) { stageHeight.toFloat().toDp() }
                        )
                        .background(bg)
                )
            }

            for (pageIndex in pagesToShow) {
                val uri = pageUris[pageIndex] ?: continue
                // 页码触发瞬间已推进：动画期间以 lastPage（翻页前页码）为基准计算偏移，保证新旧两对页滑动衔接
                val refPage = if (flipDir != 0) lastPage else currentPage
                val offsetInSpread = (pageIndex - refPage).toFloat() * dw
                val pageOffsetX = spreadCenterX + offsetInSpread + t

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                pageOffsetX.roundToInt(),
                                centerY.roundToInt()
                            )
                        }
                        .size(
                            with(LocalDensity.current) { dw.toDp() },
                            with(LocalDensity.current) { dh.toDp() }
                        )
                ) {
                    ScorePageImage(uri = uri, pageIndex = pageIndex, isDark = isDark, modifier = Modifier
                            .fillMaxSize())
                }
            }

            val maskW = (dw * 2f).coerceAtMost(spreadCenterX.coerceAtLeast(0f))
            if (maskW > 0f) {
                with(LocalDensity.current) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, centerY.roundToInt()) }
                            .size(maskW.toDp(), dh.toDp())
                            .background(
                                Brush.horizontalGradient(
                                    0f to bg,
                                    1f to Color.Transparent
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (stageWidth - maskW).roundToInt(),
                                    centerY.roundToInt()
                                )
                            }
                            .size(maskW.toDp(), dh.toDp())
                            .background(
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    1f to bg
                                )
                            )
                    )
                }
            }
        } else {
            val centerX = if (stageWidth > 0) (stageWidth - dw) / 2f else 0f
            val centerY = if (stageHeight > 0) (stageHeight - dh) / 2f else 0f

            for (pageIndex in pagesToShow) {
                val uri = pageUris[pageIndex] ?: continue
                // 页码在翻页触发瞬间已推进到目标页；动画期以 lastPage（翻页前页码）锚定滑出页，
                // 避免 currentPage 更新滞后 1-2 帧时"前一页"闪现在当前页位置：
                // 前向动画：lastPage（旧当前页）叠在 0 处随 t 滑出，新当前页静止在 0 底下
                // 后向动画：lastPage 静止在 0 处被覆盖，新当前页从 -dw 随 t 滑入
                // 拖拽期（flipDir==0）：当前页/前页跟手；其余后续页停靠屏幕外防透闪
                val base = when {
                    pageIndex == currentPage -> 0f
                    flipDir != 0 && pageIndex == lastPage -> 0f
                    flipDir == 0 && t < 0 && pageIndex == currentPage + 1 -> 0f
                    flipDir == 1 && t < 0 && pageIndex == lastPage + 1 -> 0f
                    pageIndex > currentPage -> stageWidth.toFloat()
                    else -> -(currentPage - pageIndex).toFloat() * dw
                }
                val pageOffsetX = when {
                    flipDir == 1 && t < 0 && pageIndex == lastPage -> t
                    flipDir == -1 && t > 0 && pageIndex == currentPage && pageIndex != lastPage -> -dw + t
                    flipDir == 0 && t < 0 && pageIndex == currentPage -> base + t
                    flipDir == 0 && t > 0 && pageIndex == currentPage - 1 -> base + t
                    else -> base
                }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pageOffsetX + centerX).roundToInt(),
                                centerY.roundToInt()
                            )
                        }
                        .size(
                            with(LocalDensity.current) { dw.toDp() },
                            with(LocalDensity.current) { dh.toDp() }
                        )
                ) {
                    ScorePageImage(uri = uri, pageIndex = pageIndex, isDark = isDark, modifier = Modifier
                            .fillMaxSize())
                }
            }
        }
    }
}
