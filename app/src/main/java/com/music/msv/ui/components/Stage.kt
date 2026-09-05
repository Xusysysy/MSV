package com.music.msv.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.music.msv.R
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单页渲染：升级换缓存时旧 URI 垫底显示（Coil 内存缓存命中），新图解码完成后覆盖，零空窗防闪烁。
 *  状态以 pageIndex 键控：窗口滑动导致槽位换页时状态重置，杜绝"前一页"垫底串位闪现；同页升级（URI 变化）仍保留垫底防闪烁 */
@Composable
private fun ScorePageImage(uri: Uri, pageIndex: Int, isDark: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var current by remember(pageIndex) { mutableStateOf(uri) }
    var previous by remember(pageIndex) { mutableStateOf<Uri?>(null) }
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

/** 未渲染页占位图缓存：首次使用解码一次，全局单实例复用（防多页缓存爆炸）；VM init 预热 */
object PagePlaceholderCache {
    @Volatile private var cached: ImageBitmap? = null
    fun get(context: Context): ImageBitmap = cached ?: synchronized(this) {
        cached ?: BitmapFactory.decodeResource(context.resources, R.drawable.page_placeholder)
            .asImageBitmap().also { cached = it }
    }
}

/** 未渲染页占位：真实页面渲染完成后 180ms 淡入覆盖（占位图→乐谱短暂渐变过渡，杜绝背景露出） */
@Composable
private fun PageWithPlaceholder(uri: Uri?, pageIndex: Int, isDark: Boolean, modifier: Modifier) {
    Box(modifier) {
        val context = LocalContext.current
        val placeholder = remember { PagePlaceholderCache.get(context) }
        Image(
            bitmap = placeholder,
            contentDescription = "页面加载中",
            contentScale = ContentScale.FillBounds,
            colorFilter = if (isDark) ColorFilter.colorMatrix(invertColorMatrix) else null,
            modifier = Modifier.fillMaxSize()
        )
        // 初值取 uri 存在性：窗口滑动槽位复用（remember(pageIndex) 重置）时已渲染页直接可见，杜绝"真实页被占位图覆盖"；
        // 槽内存活期间 null→uri 的首次渲染走 180ms 淡入（占位图→乐谱渐变过渡）
        var shown by remember(pageIndex) { mutableStateOf(uri != null) }
        LaunchedEffect(uri) { if (uri != null) shown = true }
        if (uri != null) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(180)),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                ScorePageImage(uri = uri, pageIndex = pageIndex, isDark = isDark, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

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
    zoomMode: Boolean,
    isSpreadMode: Boolean,
    pendingFlip: Int? = null,
    onCenterTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float, Float) -> Unit,
    onZoomModeEnter: () -> Unit = {},
    onZoomModeExit: () -> Unit = {},
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
    val transition = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var flipJob by remember { mutableStateOf<Job?>(null) }
    // 当前翻页动画方向（1=前向 -1=后向 0=无动画/拖拽中），用于区分动画期与拖拽期的页面定位
    var flipDir by remember { mutableStateOf(0) }
    // 触发翻页前的页码：动画期间以它锚定滑出页，避免 currentPage 更新滞后 1-2 帧时定位错乱（前一页闪现）
    var lastPage by remember { mutableStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // 缩放模式双击检测：上次点按时间/位置
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapX by remember { mutableStateOf(0f) }
    var lastTapY by remember { mutableStateOf(0f) }

    // 缩放模式渲染源：graphicsLayer 缩放/平移（送检 VM 状态镜像）
    val renderZoom = remember { Animatable(1f) }
    val renderPanX = remember { Animatable(0f) }
    val renderPanY = remember { Animatable(0f) }
    var pinching by remember { mutableStateOf(false) }

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
    val currentZoomMode by rememberUpdatedState(zoomMode)
    val currentOnZoomModeEnter by rememberUpdatedState(onZoomModeEnter)
    val currentOnZoomModeExit by rememberUpdatedState(onZoomModeExit)

    // VM 缩放/平移状态 → 渲染层同步（非捏合期间；退出缩放的动画进行中不覆盖，保证动画过渡；reset/切文档时归位）
    LaunchedEffect(zoom) {
        if (!pinching && !renderZoom.isRunning) renderZoom.snapTo(zoom)
    }
    LaunchedEffect(panOffsetX, panOffsetY) {
        if (!pinching && !renderPanX.isRunning && !renderPanY.isRunning) {
            renderPanX.snapTo(panOffsetX)
            renderPanY.snapTo(panOffsetY)
        }
    }

    LaunchedEffect(pageWidth, pageHeight) {
        transition.snapTo(0f)
    }

    fun doFlip(dir: Int, fromOffset: Float, easing: Boolean) {
        if (currentZoomMode) return // 缩放模式禁用任何翻页
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
        if (currentZoomMode) return@LaunchedEffect // 缩放模式禁用翻页
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
                    var pinchStartSpan = 1f
                    var pinchStartZoom = 1f
                    var pinchStartCx = 0f
                    var pinchStartCy = 0f
                    var pinchLastCx = 0f
                    var pinchLastCy = 0f
                    var moved = false
                    var wasPinching = false
                    var isDrag = false
                    var activeDir = 0
                    var reversed = false
                    var pinchExited = false
                    var maxNetDisp = 0f
                    // 捏合期局部追踪（同步更新，避免 renderPan 异步 snapTo 滞后造成焦点补偿累积误差）
                    var curZoom = 1f
                    var curPanX = 0f
                    var curPanY = 0f
                    val downX = down.position.x
                    val downY = down.position.y
                    var lastX = downX
                    var lastY = downY

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedList = event.changes.filter { it.pressed }
                        if (pressedList.isEmpty()) {
                            // 全部抬起
                            if (pinching) {
                                pinching = false
                                if (renderZoom.value > 1.01f) {
                                    // 松手保留放大：确保缩放模式已进入
                                    if (!currentZoomMode) onZoomModeEnter()
                                } else {
                                    // 捏回原比例：动画恢复并退出缩放模式（zoom/pan 并行，一步到位）
                                    scope.launch {
                                        launch { renderZoom.animateTo(1f, tween(220)) }
                                        launch { renderPanX.animateTo(0f, tween(220)) }
                                        launch { renderPanY.animateTo(0f, tween(220)) }
                                        onZoomModeExit()
                                    }
                                }
                            } else if ((!isDrag && !moved && !pinchExited) || (isDrag && maxNetDisp < 24.dp.toPx() && !reversed)) {
                                if (isDrag) {
                                    // 双击/点按的手指微动超过 touchSlop 被误判为拖拽（路径未超 24dp）：归位并按单击处理
                                    isDrag = false
                                    scope.launch { transition.snapTo(0f) }
                                }
                                if (!currentZoomMode) {
                                    // 原点按三区逻辑
                                    val sw = stageWidth
                                    if (sw > 0) {
                                        val third = sw / 3f
                                        when {
                                            downX < third -> {
                                                val step = if (currentIsSpread) 2 else 1
                                                if (currentPageIndex > 0) {
                                                    onPreloadAround(currentPageIndex - step)
                                                    doFlip(-1, 0f, easing = true)
                                                } else doBounce(-1)
                                            }
                                            downX > sw - third -> {
                                                val step = if (currentIsSpread) 2 else 1
                                                if (currentPageIndex < currentPageCount - 1) {
                                                    onPreloadAround(currentPageIndex + step)
                                                    doFlip(1, 0f, easing = true)
                                                } else doBounce(1)
                                            }
                                            else -> {
                                                // 中央双击：进入缩放模式并步进放大到 2x（先撤销首次点按的 UI 切换）
                                                val now = System.currentTimeMillis()
                                                if (now - lastTapTime < 400 && abs(downX - lastTapX) < 48.dp.toPx() && abs(downY - lastTapY) < 48.dp.toPx()) {
                                                    lastTapTime = 0L
                                                    onCenterTap()
                                                    onZoomModeEnter()
                                                    scope.launch {
                                                        renderZoom.animateTo(2f, tween(220))
                                                        val maxX = (renderZoom.value - 1f) * stageWidth / 2f
                                                        val maxY = (renderZoom.value - 1f) * stageHeight / 2f
                                                        renderPanX.snapTo(renderPanX.value.coerceIn(-maxX, maxX))
                                                        renderPanY.snapTo(renderPanY.value.coerceIn(-maxY, maxY))
                                                    }
                                                } else {
                                                    onCenterTap()
                                                    lastTapTime = now
                                                    lastTapX = downX
                                                    lastTapY = downY
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // 缩放模式单击：双击检测（任意放大状态恢复；仅 1x 时步进放大到 2x）
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < 400 && abs(downX - lastTapX) < 48.dp.toPx() && abs(downY - lastTapY) < 48.dp.toPx()) {
                                        lastTapTime = 0L
                                        if (renderZoom.value > 1.01f) {
                                            // 任意放大状态双击：恢复并退出缩放模式（zoom/pan 并行，一步到位）
                                            scope.launch {
                                                launch { renderZoom.animateTo(1f, tween(220)) }
                                                launch { renderPanX.animateTo(0f, tween(220)) }
                                                launch { renderPanY.animateTo(0f, tween(220)) }
                                                onZoomModeExit()
                                            }
                                        } else {
                                            scope.launch {
                                                renderZoom.animateTo(2f, tween(220))
                                                val maxX = (renderZoom.value - 1f) * stageWidth / 2f
                                                val maxY = (renderZoom.value - 1f) * stageHeight / 2f
                                                renderPanX.snapTo(renderPanX.value.coerceIn(-maxX, maxX))
                                                renderPanY.snapTo(renderPanY.value.coerceIn(-maxY, maxY))
                                            }
                                        }
                                    } else {
                                        // 记录首击：缺此记录时缩放模式双击的第二击永远判定不到（双击失效根因）
                                        lastTapTime = now
                                        lastTapX = downX
                                        lastTapY = downY
                                    }
                                }
                            } else if (isDrag && !currentZoomMode) {
                                // 拖拽翻页收尾（原逻辑）
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
                        // 双指捏回原比例已自动退出缩放模式：本次手势内不再触发缩放，等待全部抬起
                        if (pinchExited) {
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        // 双指 → 捏合缩放（优先于一切单指逻辑）；检测到双指一瞬间进入缩放模式并隐藏悬浮组件
                        if (pressedList.size >= 2) {
                            val a = pressedList[0].position
                            val b = pressedList[1].position
                            val span = hypot(a.x - b.x, a.y - b.y)
                            val cx = (a.x + b.x) / 2f
                            val cy = (a.y + b.y) / 2f
                            if (!pinching) {
                                pinching = true
                                wasPinching = true
                                // 检测到双指：一瞬间进入缩放模式（隐藏悬浮组件/禁用翻页）
                                if (!currentZoomMode) onZoomModeEnter()
                                flipJob?.cancel()
                                flipDir = 0
                                scope.launch { transition.snapTo(0f) }
                                pinchStartSpan = if (span > 0f) span else 1f
                                pinchStartZoom = renderZoom.value
                                pinchStartCx = cx; pinchStartCy = cy
                                pinchLastCx = cx; pinchLastCy = cy
                                curZoom = renderZoom.value
                                curPanX = renderPanX.value
                                curPanY = renderPanY.value
                            } else {
                                wasPinching = true
                                val newZoom = (pinchStartZoom * span / pinchStartSpan).coerceIn(1f, 8f)
                                val dx = cx - pinchLastCx
                                val dy = cy - pinchLastCy
                                pinchLastCx = cx; pinchLastCy = cy
                                if (currentZoomMode && curZoom > 1.05f && newZoom <= 1.02f) {
                                    // 双指捏回接近原比例：动画归位并自动退出缩放模式（无需松手）
                                    pinching = false
                                    pinchExited = true
                                    curZoom = 1f; curPanX = 0f; curPanY = 0f
                                    scope.launch {
                                        launch { renderZoom.animateTo(1f, tween(220)) }
                                        launch { renderPanX.animateTo(0f, tween(220)) }
                                        launch { renderPanY.animateTo(0f, tween(220)) }
                                        onZoomModeExit()
                                    }
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                // 焦点补偿：以双指起始中点为缩放中心（内容在起始中点处保持不动），叠加质心平移
                                val k = if (curZoom > 0f) 1f - newZoom / curZoom else 0f
                                curPanX += (pinchStartCx - curPanX - stageWidth / 2f) * k + dx
                                curPanY += (pinchStartCy - curPanY - stageHeight / 2f) * k + dy
                                curZoom = newZoom
                                val maxX = (newZoom - 1f) * stageWidth / 2f
                                val maxY = (newZoom - 1f) * stageHeight / 2f
                                curPanX = curPanX.coerceIn(-maxX, maxX)
                                curPanY = curPanY.coerceIn(-maxY, maxY)
                                scope.launch {
                                    renderZoom.snapTo(curZoom)
                                    renderPanX.snapTo(curPanX)
                                    renderPanY.snapTo(curPanY)
                                }
                                onZoomChange(newZoom)
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val change = pressedList[0]
                        // 缩放模式：单指拖拽 = 平移；单击 = 双击检测（任意放大状态恢复）
                        if (currentZoomMode || pinching) {
                            if (!change.pressed) {
                                if (!moved) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < 300 && abs(change.position.x - lastTapX) < 48.dp.toPx() && abs(change.position.y - lastTapY) < 48.dp.toPx()) {
                                        lastTapTime = 0L
                                        if (renderZoom.value > 1.01f) {
                                            // 任意放大状态双击：恢复并退出缩放模式
                                            scope.launch {
                                                renderZoom.animateTo(1f, tween(220))
                                                renderPanX.animateTo(0f, tween(220))
                                                renderPanY.animateTo(0f, tween(220))
                                                onZoomModeExit()
                                            }
                                        } else {
                                            // 1x 双击：步进放大到 2x（固定比例）
                                            scope.launch {
                                                renderZoom.animateTo(2f, tween(220))
                                                val maxX = (renderZoom.value - 1f) * stageWidth / 2f
                                                val maxY = (renderZoom.value - 1f) * stageHeight / 2f
                                                renderPanX.snapTo(renderPanX.value.coerceIn(-maxX, maxX))
                                                renderPanY.snapTo(renderPanY.value.coerceIn(-maxY, maxY))
                                            }
                                        }
                                    } else {
                                        lastTapTime = now
                                        lastTapX = change.position.x
                                        lastTapY = change.position.y
                                    }
                                }
                            } else {
                                if (wasPinching) {
                                    // 捏合结束转单指：重锚定平移基准，消除跳变（保持与松手前对齐）
                                    wasPinching = false
                                    lastX = change.position.x
                                    lastY = change.position.y
                                } else {
                                    val dx = change.position.x - lastX
                                    val dy = change.position.y - lastY
                                    lastX = change.position.x; lastY = change.position.y
                                    // 相对按下点的位移判定 moved：0.5px 级触摸抖动不误判，保证缩放模式双击可触发
                                    moved = abs(change.position.x - downX) > 24.dp.toPx() ||
                                        abs(change.position.y - downY) > 24.dp.toPx()
                                    scope.launch {
                                        val maxX = (renderZoom.value - 1f) * stageWidth / 2f
                                        val maxY = (renderZoom.value - 1f) * stageHeight / 2f
                                        renderPanX.snapTo((renderPanX.value + dx).coerceIn(-maxX, maxX))
                                        renderPanY.snapTo((renderPanY.value + dy).coerceIn(-maxY, maxY))
                                    }
                                }
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        // 正常模式单指：原点按三区/拖拽翻页逻辑
                        // 路径最大净位移：双击手指微动兜底判定（超 touchSlop 但未真正拖动时仍按单击）
                        val netD = hypot(change.position.x - downX, change.position.y - downY)
                        if (netD > maxNetDisp) maxNetDisp = netD
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
                                        else -> {
                                            // 中央双击：进入缩放模式并步进放大到 2x（先撤销首次点按的 UI 切换）
                                            val now = System.currentTimeMillis()
                                            if (now - lastTapTime < 300 && abs(change.position.x - lastTapX) < 48.dp.toPx() && abs(change.position.y - lastTapY) < 48.dp.toPx()) {
                                                lastTapTime = 0L
                                                onCenterTap()
                                                onZoomModeEnter()
                                                scope.launch {
                                                    renderZoom.animateTo(2f, tween(220))
                                                    val maxX = (renderZoom.value - 1f) * stageWidth / 2f
                                                    val maxY = (renderZoom.value - 1f) * stageHeight / 2f
                                                    renderPanX.snapTo(renderPanX.value.coerceIn(-maxX, maxX))
                                                    renderPanY.snapTo(renderPanY.value.coerceIn(-maxY, maxY))
                                                }
                                            } else {
                                                onCenterTap()
                                                lastTapTime = now
                                                lastTapX = change.position.x
                                                lastTapY = change.position.y
                                            }
                                        }
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
    ) {
        val (dw, dh) = displaySize ?: Pair(stageWidth.toFloat(), stageHeight.toFloat())
        val t = transition.value

        // 页面内容容器：缩放模式经 graphicsLayer 整体缩放/平移（横屏双页为一个整体；
        // 其余页仍在组合内但被放大移出屏幕外——不清缓存，只是不显示在屏幕上）
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = renderZoom.value
                    scaleY = renderZoom.value
                    translationX = renderPanX.value
                    translationY = renderPanY.value
                }
        ) {
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
                    val uri = pageUris[pageIndex]
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
                        PageWithPlaceholder(uri = uri, pageIndex = pageIndex, isDark = isDark, modifier = Modifier
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
                    val uri = pageUris[pageIndex]
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
                        PageWithPlaceholder(uri = uri, pageIndex = pageIndex, isDark = isDark, modifier = Modifier
                                .fillMaxSize())
                    }
                }
            }
        }
    }
}
