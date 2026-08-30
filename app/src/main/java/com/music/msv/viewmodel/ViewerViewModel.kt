package com.music.msv.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.music.msv.facer.FaceLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.msv.data.model.Mode
import com.music.msv.data.model.ShelfFile
import com.music.msv.data.model.ShelfSort
import com.music.msv.data.model.UpdateStatus
import com.music.msv.data.model.ViewerEvent
import com.music.msv.data.model.ViewerState
import com.music.msv.data.pdf.PdfPageRenderer
import com.music.msv.data.repository.FileRepository
import com.music.msv.data.repository.SessionRepository
import com.music.msv.data.repository.UpdateRepository
import com.music.msv.data.repository.compareVersions
import com.music.msv.data.model.PageBookmark
import com.music.msv.facer.FaceRecognitionManager
import com.music.msv.facer.FaceRecognitionRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val FLIP_WINDOW_MS = 2000L        // 快速翻动判定窗口
        const val FAST_FLIP_COUNT = 4           // 窗口内翻页次数阈值（≥4 页判定快速连续翻动）
        const val FLIP_SETTLE_DELAY_MS = 1000L  // 停止翻动后恢复正常分辨率加载的延迟
    }

    private val fileRepo = FileRepository(application)
    private val sessionRepo = SessionRepository(application)
    private val pdfRenderer = PdfPageRenderer(application)
    val faceManager = FaceRecognitionManager(application)
    private val faceRepo = FaceRecognitionRepository(application)
    private val updateRepo = UpdateRepository(application)

    private val _uiState = MutableStateFlow(ViewerState())
    val uiState: StateFlow<ViewerState> = _uiState.asStateFlow()

    private var imageUris: List<Uri> = emptyList()
    private var pdfUri: Uri? = null
    private var loadJob: Job? = null
    private var preloadJob: Job? = null
    private val thumbnailCache = java.util.concurrent.ConcurrentHashMap<Int, Uri>()
    private var pageMap = mapOf<String, Int>()

    // 每页已渲染位图相对全尺寸(pageW×pageH)的比例：1f=全分辨率，0.6f/0.4f=预加载压缩层
    private val pageScales = java.util.concurrent.ConcurrentHashMap<Int, Float>()

    // 快速连续翻动检测：窗口(2s)内翻页≥4次进入快速模式（全部压缩渲染），停止翻动 1s 后恢复正常分辨率
    private val flipTimestamps = ArrayDeque<Long>()
    private var settleJob: Job? = null

    private var singleRenderJob: Job? = null
    private var downloadJob: Job? = null

    init {
        FaceLog.d("MSV_VM", "ViewerViewModel init")
        PDFBoxResourceLoader.init(getApplication())
        _uiState.update {
            it.copy(appVersionName = updateRepo.installedVersionName, appVersionCode = updateRepo.installedVersionCode)
        }
        viewModelScope.launch {
            sessionRepo.getPageMap().collect { pageMap = it }
        }
        viewModelScope.launch {
            FaceLog.d("MSV_VM", "开始加载面部偏好+监听stateFlow")
            faceRepo.load(faceManager)
            faceManager.stateFlow.collect { faceState ->
                _uiState.update { it.copy(faceActive = faceState.actionActive, faceEnabled = faceState.running) }
            }
        }
        faceManager.onGesture = { gesture ->
            FaceLog.i("MSV_VM", "收到手势回调: $gesture")
            when (gesture) {
                FaceRecognitionManager.Gesture.RIGHT_WINK,
                FaceRecognitionManager.Gesture.LEFT_PUCKER -> faceFlip(1)
                FaceRecognitionManager.Gesture.LEFT_WINK,
                FaceRecognitionManager.Gesture.RIGHT_PUCKER -> faceFlip(-1)
                FaceRecognitionManager.Gesture.NONE -> {}
            }
        }
        restoreSession()
        // 冷启动静默检查更新（无网络时静默失败）
        viewModelScope.launch(Dispatchers.IO) {
            updateRepo.cleanupDownloadedApk(updateRepo.installedVersionName)
            checkUpdate(manual = false)
        }
    }

    fun handleShareIntent(intent: Intent?) {
        if (intent == null || intent.action == Intent.ACTION_MAIN) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { listOf(it) } ?: emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                } ?: emptyList()
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { listOf(it) } ?: emptyList()
            }
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            onEvent(ViewerEvent.FilesSelected(uris))
            intent.action = Intent.ACTION_MAIN
        }
    }

    fun onEvent(event: ViewerEvent) {
        when (event) {
            is ViewerEvent.FilesSelected -> handleFilesSelected(event.uris)
            is ViewerEvent.GoToPage -> goToPage(event.page)
            ViewerEvent.NextPage -> {
                val step = if (_uiState.value.isSpreadMode) 2 else 1
                goToPage(_uiState.value.currentPage + step)
            }
            ViewerEvent.PrevPage -> {
                val step = if (_uiState.value.isSpreadMode) 2 else 1
                goToPage(_uiState.value.currentPage - step)
            }
            is ViewerEvent.SetZoom -> setZoom(event.zoom)
            is ViewerEvent.PanBy -> panBy(event.dx, event.dy)
            is ViewerEvent.UpdateViewportSize -> updateViewportSize(event.width, event.height)
            ViewerEvent.ToggleUI -> toggleUI()
            ViewerEvent.ToggleThumbnails -> toggleThumbnails()
            ViewerEvent.ToggleTheme -> toggleTheme()
            ViewerEvent.ResetZoom -> resetZoom()
            ViewerEvent.Reset -> reset()
            ViewerEvent.Reload -> reload()
            ViewerEvent.ToggleShelf -> toggleShelf()
            is ViewerEvent.OpenShelfFile -> openShelfFile(event.uri)
            is ViewerEvent.RenameShelfFile -> renameShelfFile(event.uri, event.newName)
            is ViewerEvent.SetShelfSort -> setShelfSort(event.sort)
            is ViewerEvent.DeleteShelfFile -> deleteShelfFile(event.uri)
            is ViewerEvent.SetSpreadMode -> setSpreadMode(event.spread)
            ViewerEvent.ToggleFace -> toggleFace()
            ViewerEvent.ShowFaceOverlay -> showFaceOverlay()
            ViewerEvent.HideFaceOverlay -> hideFaceOverlay()
            ViewerEvent.FlipDone -> flipDone()
            ViewerEvent.ToggleSettings -> toggleSettings()
            ViewerEvent.CheckUpdate -> checkUpdate(manual = true)
            ViewerEvent.DownloadUpdate -> downloadUpdate()
            ViewerEvent.InstallUpdate -> installUpdate()
            ViewerEvent.DismissUpdateDialog -> dismissUpdateDialog()
            ViewerEvent.ToggleVersionLog -> toggleVersionLog()
            is ViewerEvent.AddBookmark -> addBookmark(event.page, event.title, event.color)
            is ViewerEvent.DeleteBookmark -> deleteBookmark(event.id)
            is ViewerEvent.RenameBookmark -> renameBookmark(event.id, event.title, event.color)
        }
    }

    private fun handleFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        thumbnailCache.clear()
        fileRepo.takePersistablePermissions(uris)
        viewModelScope.launch(Dispatchers.IO) {
            val uri = uris.first()
            val name = fileRepo.getFileName(uri)
            val localUri = fileRepo.copyToLocal(uri, name) ?: uri

            when {
                fileRepo.isPdf(name) -> openPdf(localUri, name)
                fileRepo.isImage(name) -> {
                    val localUris = uris.mapIndexed { i, u ->
                        val imgName = fileRepo.getFileName(u)
                        fileRepo.copyToLocal(u, imgName) ?: u
                    }
                    openImages(localUris, name)
                }
                else -> {
                    _uiState.update {
                        it.copy(statusMessage = "不支持的文件类型", showThumbnails = false)
                    }
                }
            }
            if (_uiState.value.showShelf) loadShelfFiles()
        }
    }

    private fun openPdf(uri: Uri, name: String, restorePage: Int = 0) {
        loadJob?.cancel()
        cancelPageRendering()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                pdfUri = uri
                imageUris = emptyList()
                val pageCount = pdfRenderer.open(uri)
                if (pageCount == 0) {
                    pdfUri = null
                    pdfRenderer.close()
                    _uiState.update { it.copy(statusMessage = "无法打开 PDF") }
                    return@launch
                }
                val rp = restorePage.coerceIn(0, pageCount - 1)
                val pw = pdfRenderer.pageWidth.toFloat()
                val ph = pdfRenderer.pageHeight.toFloat()
                val ratio = pw / ph
                _uiState.update {
                    it.copy(
                        mode = Mode.Pdf,
                        isLoading = true,
                        pageCount = pageCount,
                        currentPage = rp,
                        fileName = name,
                        statusMessage = "已加载: $name",
                        zoom = 1f,
                        panOffsetX = 0f,
                        panOffsetY = 0f,
                        pageUris = emptyMap(),
                        pageWidth = 0,
                        pageHeight = 0
                    )
                }
                pageScales.clear()
                _uiState.update { it.copy(bookmarks = readPdfBookmarks(uri)) }
                renderPageToCacheComputeSize(rp, ratio)
                preloadAround(rp)
                preloadThumbnails()
                saveSession()
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "PDF 加载失败: ${e.message}") }
            }
        }
    }

    private fun openImages(uris: List<Uri>, name: String, initialPage: Int = 0) {
        cancelPageRendering()
        imageUris = uris.sortedBy { it.lastPathSegment }
        pdfUri = null
        pdfRenderer.close()
        val page = initialPage.coerceIn(0, uris.size - 1)
        _uiState.update {
            it.copy(
                mode = Mode.Image,
                isLoading = true,
                pageCount = uris.size,
                currentPage = page,
                fileName = name,
                statusMessage = "已加载: $name (${uris.size} 页)",
                zoom = 1f,
                panOffsetX = 0f,
                panOffsetY = 0f,
                pageUris = uris.mapIndexed { i, u -> i to u }.toMap(),
                pageWidth = 0,
                pageHeight = 0
            )
        }
        // 图片模式直接使用原图，全部视为全分辨率；书签仅支持 PDF
        pageScales.clear()
        for (i in uris.indices) pageScales[i] = 1f
        _uiState.update { it.copy(bookmarks = emptyList()) }
        saveSession()
    }

    /** 切换谱子时立刻停止旧谱子的所有页面渲染任务，让新谱子优先渲染，避免新旧任务争抢 IO/CPU 卡顿 */
    private fun cancelPageRendering() {
        preloadJob?.cancel()
        preloadJob = null
        singleRenderJob?.cancel()
        singleRenderJob = null
        settleJob?.cancel()
        settleJob = null
        flipTimestamps.clear()
        pageScales.clear()
    }

    private fun goToPage(page: Int) {
        val state = _uiState.value
        if (state.pageCount == 0) return
        val target = page.coerceIn(0, state.pageCount - 1)
        if (target == state.currentPage) return
        _uiState.update { it.copy(currentPage = target) }
        onFlipHappened(target)
        saveSession()
    }

    /** 记录一次翻页；始终按当前页重算渲染优先级（快速翻动模式自动切压缩渲染，停止 1s 后恢复正常分辨率） */
    private fun onFlipHappened(center: Int) {
        flipTimestamps.addLast(System.currentTimeMillis())
        preloadAround(center)
        if (isFastFlipping()) {
            settleJob?.cancel()
            settleJob = viewModelScope.launch {
                delay(FLIP_SETTLE_DELAY_MS)
                preloadAround(_uiState.value.currentPage, forceFullRes = true)
            }
        }
    }

    private fun isFastFlipping(): Boolean {
        val now = System.currentTimeMillis()
        flipTimestamps.removeAll { now - it > FLIP_WINDOW_MS }
        return flipTimestamps.size >= FAST_FLIP_COUNT
    }

    private fun renderPageToCacheComputeSize(pageIndex: Int, ratio: Float) {
        val vw = _uiState.value.viewportWidth
        val zw = _uiState.value.pageWidth
        if (vw <= 0 || zw > 0) return
        val pageW = vw
        val pageH = (vw / ratio).toInt()
        _uiState.update { it.copy(pageWidth = pageW, pageHeight = pageH) }
        val zoom = _uiState.value.zoom
        singleRenderJob = viewModelScope.launch(Dispatchers.IO) {
            val uri = when (_uiState.value.mode) {
                is Mode.Pdf -> renderPage(pageIndex, pageW, pageH, zoom)
                is Mode.Image -> imageUris.getOrNull(pageIndex)
                else -> null
            }
            if (uri != null) {
                pageScales[pageIndex] = 1f
                _uiState.update { it.copy(pageUris = it.pageUris + (pageIndex to uri)) }
            }
        }
    }

    fun preloadAround(center: Int, forceFullRes: Boolean = false) {
        preloadJob?.cancel()
        val state = _uiState.value
        val total = state.pageCount
        val pageW = state.pageWidth
        val pageH = state.pageHeight
        if (pageW <= 0) return
        if (center !in 0 until total) return
        val zoom = state.zoom
        val keepMin = (center - 5).coerceAtLeast(0)
        val keepMax = (center + 5).coerceAtMost(total - 1)
        val oldUris = state.pageUris
        val fastFlip = !forceFullRes && isFastFlipping()
        preloadJob = viewModelScope.launch(Dispatchers.IO) {
            val needRender = (keepMin..keepMax).filter { !oldUris.containsKey(it) }
            if (fastFlip) {
                // 快速连续翻动：全部 0.6x 压缩渲染保证速度；当前页同步优先渲染，其余并行；停止翻动 1s 后由 settle 恢复正常分辨率
                val sW = (pageW * 0.6f).toInt().coerceAtLeast(1)
                val sH = (pageH * 0.6f).toInt().coerceAtLeast(1)
                if (center in needRender) {
                    renderPage(center, sW, sH, zoom, 85)?.let { uri ->
                        pageScales[center] = 0.6f
                        _uiState.update { it.copy(pageUris = it.pageUris + (center to uri)) }
                    }
                }
                val rest = needRender.filter { it != center }
                if (rest.isNotEmpty()) {
                    val rendered = rest.map { i ->
                        async { renderPage(i, sW, sH, zoom, 85)?.let { i to it } }
                    }.awaitAll().filterNotNull().toMap()
                    if (rendered.isNotEmpty()) {
                        rendered.keys.forEach { pageScales[it] = 0.6f }
                        _uiState.update { it.copy(pageUris = it.pageUris + rendered) }
                    }
                }
            } else {
                // 缺失 或 已存在但非全分辨率（此前按 0.6x/0.4x 预加载的压缩页）都需全尺寸渲染
                fun needsFull(i: Int) = !oldUris.containsKey(i) || pageScales[i] != 1f
                suspend fun replacePage(index: Int, uri: Uri, oldUri: Uri?) {
                    if (oldUri != null && oldUri != uri) {
                        try { java.io.File(oldUri.path!!).delete() } catch (_: Exception) {}
                    }
                    // 预热 Coil 内存缓存后再换 URI，避免已渲染页面在升级瞬间空白（消失又出现）
                    runCatching {
                        val app = getApplication<Application>()
                        coil3.SingletonImageLoader.get(app)
                            .execute(coil3.request.ImageRequest.Builder(app).data(uri).build())
                    }
                    pageScales[index] = 1f
                    _uiState.update { it.copy(pageUris = it.pageUris + (index to uri)) }
                }
                // center / next：Q95 全尺寸（缺失或升级压缩页）
                if (needsFull(center)) {
                    renderPage(center, pageW, pageH, zoom, 95)?.let { replacePage(center, it, oldUris[center]) }
                }
                val next = center + 1
                if (next in 0 until total && needsFull(next)) {
                    renderPage(next, pageW, pageH, zoom, 95)?.let { replacePage(next, it, oldUris[next]) }
                }
                val near = (center - 1..center + 1).filter {
                    it in 0 until total && it != center && it != next && needsFull(it)
                }
                if (near.isNotEmpty()) {
                    val rendered = near.map { i ->
                        async { renderPage(i, pageW, pageH, zoom, 90)?.let { i to it } }
                    }.awaitAll().filterNotNull()
                    for ((i, uri) in rendered) replacePage(i, uri, oldUris[i])
                }
                val mid = needRender.filter { kotlin.math.abs(it - center) in 2..3 }
                if (mid.isNotEmpty()) {
                    val sW = (pageW * 0.6f).toInt().coerceAtLeast(1)
                    val sH = (pageH * 0.6f).toInt().coerceAtLeast(1)
                    val rendered = mid.map { i ->
                        async { renderPage(i, sW, sH, zoom, 85)?.let { i to it } }
                    }.awaitAll().filterNotNull().toMap()
                    if (rendered.isNotEmpty()) {
                        rendered.keys.forEach { pageScales[it] = 0.6f }
                        _uiState.update { it.copy(pageUris = it.pageUris + rendered) }
                    }
                }
                val far = needRender.filter { kotlin.math.abs(it - center) >= 4 }
                if (far.isNotEmpty()) {
                    val sW = (pageW * 0.4f).toInt().coerceAtLeast(1)
                    val sH = (pageH * 0.4f).toInt().coerceAtLeast(1)
                    val rendered = far.map { i ->
                        async { renderPage(i, sW, sH, zoom, 80)?.let { i to it } }
                    }.awaitAll().filterNotNull().toMap()
                    if (rendered.isNotEmpty()) {
                        rendered.keys.forEach { pageScales[it] = 0.4f }
                        _uiState.update { it.copy(pageUris = it.pageUris + rendered) }
                    }
                }
            }
            _uiState.update { prev ->
                val merged = prev.pageUris.toMutableMap()
                val toRemove = merged.keys.filter { it !in keepMin..keepMax }
                for (page in toRemove) {
                    val uri = merged.remove(page)
                    pageScales.remove(page)
                    if (uri != null) {
                        try { java.io.File(uri.path!!).delete() } catch (_: Exception) {}
                    }
                }
                prev.copy(pageUris = merged)
            }
            if (_uiState.value.isLoading) {
                val loaded = _uiState.value.pageUris
                val required = (center - 1..center + 2).filter { it in 0 until total }
                if (required.all { loaded.containsKey(it) }) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private val docCacheKey: String
        get() = pdfUri?.path?.hashCode()?.toString(36) ?: "0"

    private fun renderPage(pageIndex: Int, pageW: Int, pageH: Int, zoom: Float, quality: Int = 95): Uri? {
        if (pageW <= 0 || pageH <= 0) return null
        val bmp = pdfRenderer.renderPage(pageIndex, pageW, pageH, zoom) ?: return null
        val cachedFile = java.io.File(
            getApplication<Application>().cacheDir,
            "page_${docCacheKey}_${pageIndex}_${pageW}x${pageH}.jpg"
        )
        cachedFile.delete()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, cachedFile.outputStream())
        bmp.recycle()
        return Uri.fromFile(cachedFile)
    }

    private fun updateViewportSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val state = _uiState.value
        val changed = state.viewportWidth != width || state.viewportHeight != height
        _uiState.update { it.copy(viewportWidth = width, viewportHeight = height) }
        if (changed && state.pageWidth == 0 && state.mode != Mode.Idle) {
            val ratio = if (state.mode is Mode.Pdf) {
                pdfRenderer.pageWidth.toFloat() / pdfRenderer.pageHeight.toFloat()
            } else 1f
            renderPageToCacheComputeSize(state.currentPage, ratio)
            preloadAround(state.currentPage)
        }
    }

    private fun setZoom(zoom: Float) {
        _uiState.update { it.copy(zoom = zoom.coerceIn(0.5f, 8f)) }
    }

    private fun panBy(dx: Float, dy: Float) {
        _uiState.update {
            it.copy(panOffsetX = it.panOffsetX + dx, panOffsetY = it.panOffsetY + dy)
        }
    }

    private fun toggleUI() {
        _uiState.update { it.copy(showUI = !it.showUI) }
    }

    private fun toggleThumbnails() {
        val show = !_uiState.value.showThumbnails
        _uiState.update { it.copy(showThumbnails = show, showSettings = if (show) false else it.showSettings) }
        if (show) preloadThumbnails()
    }

    private fun toggleShelf() {
        val show = !_uiState.value.showShelf
        _uiState.update { it.copy(showShelf = show, showSettings = if (show) false else it.showSettings) }
        if (show) loadShelfFiles()
    }

    private fun toggleSettings() {
        val show = !_uiState.value.showSettings
        _uiState.update {
            it.copy(
                showSettings = show,
                showThumbnails = if (show) false else it.showThumbnails,
                showShelf = if (show) false else it.showShelf
            )
        }
    }

    private fun setShelfSort(sort: ShelfSort) {
        if (_uiState.value.shelfSortBy == sort) return
        _uiState.update { it.copy(shelfSortBy = sort, statusMessage = if (sort == ShelfSort.NAME) "已按名称排序" else "已按日期排序") }
        loadShelfFiles()
    }

    /** 删除谱架中的乐谱：删除文件 + 清理其页面/缩略图缓存；若删除的是当前打开的谱子则回到空闲态 */
    private fun deleteShelfFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = java.io.File(uri.path ?: return@launch)
            val name = file.name
            if (!file.exists() || !file.delete()) {
                _uiState.update { it.copy(statusMessage = "删除失败: $name") }
                return@launch
            }
            val dk = uri.path?.hashCode()?.toString(36)
            val pathHash = uri.path?.hashCode()
            getApplication<Application>().cacheDir.listFiles()?.forEach { f ->
                val isPage = dk != null && (f.name.startsWith("page_${dk}_") || f.name.startsWith("thumb_${dk}_"))
                val isShelfThumb = pathHash != null && f.name.startsWith("shelf_thumb_${pathHash}_")
                if (isPage || isShelfThumb) f.delete()
            }
            if (uri == pdfUri || uri in imageUris) {
                cancelPageRendering()
                pdfRenderer.close()
                imageUris = emptyList()
                pdfUri = null
                thumbnailCache.clear()
                _uiState.update {
                    ViewerState(isDarkTheme = it.isDarkTheme, appVersionName = it.appVersionName, appVersionCode = it.appVersionCode)
                }
                sessionRepo.clearSession()
            }
            _uiState.update { it.copy(statusMessage = "已删除: $name") }
            loadShelfFiles()
        }
    }

    private fun setSpreadMode(spread: Boolean) {
        val state = _uiState.value
        if (state.isSpreadMode == spread) return
        _uiState.update { it.copy(isSpreadMode = spread) }
        if (spread && state.currentPage % 2 != 0) {
            goToPage(state.currentPage + 1)
        }
    }

    private fun loadShelfFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val sortBy = _uiState.value.shelfSortBy
            val files = fileRepo.listLocalFiles().let { list ->
                when (sortBy) {
                    ShelfSort.NAME -> list.sortedBy { it.name.lowercase() }
                    ShelfSort.DATE -> list.sortedByDescending { it.lastModified() }
                }
            }.map { file ->
                ShelfFile(
                    name = file.name,
                    uri = Uri.fromFile(file),
                    thumbnailUri = if (fileRepo.isImage(file.name)) Uri.fromFile(file) else null,
                    lastModified = file.lastModified()
                )
            }
            _uiState.update { it.copy(shelfFiles = files) }
            for (sf in files) {
                if (sf.thumbnailUri != null) continue
                val thumb = generatePdfThumbnail(sf.uri)
                if (thumb != null) {
                    _uiState.update { state ->
                        val updated = state.shelfFiles.map { f ->
                            if (f.uri == sf.uri) f.copy(thumbnailUri = thumb) else f
                        }
                        state.copy(shelfFiles = updated)
                    }
                }
            }
        }
    }

    private fun generatePdfThumbnail(fileUri: Uri): Uri? {
        return try {
            val app = getApplication<Application>()
            val filePath = fileUri.path ?: fileUri.toString()
            val cacheKey = "shelf_thumb_${filePath.hashCode()}_${fileUri.lastPathSegment?.hashCode() ?: 0}"
            val cachedFile = java.io.File(app.cacheDir, "$cacheKey.png")
            if (cachedFile.exists() && cachedFile.length() > 0) return Uri.fromFile(cachedFile)
            val fd = app.contentResolver.openFileDescriptor(fileUri, "r") ?: return null
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            if (renderer.pageCount == 0) { renderer.close(); return null }
            val page = renderer.openPage(0)
            val maxDim = 200f
            val scale = maxDim / kotlin.math.max(page.width.toFloat(), page.height.toFloat())
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            cachedFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, it) }
            bmp.recycle()
            Uri.fromFile(cachedFile)
        } catch (_: Exception) { null }
    }

    private fun renameShelfFile(oldUri: Uri, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = java.io.File(oldUri.path ?: return@launch)
            if (!oldFile.exists()) return@launch
            val ext = oldFile.extension
            val newFileName = if (newName.endsWith(".$ext")) newName else "$newName.$ext"
            val newFile = java.io.File(oldFile.parentFile, newFileName)
            if (newFile.exists() || !oldFile.renameTo(newFile)) {
                _uiState.update { it.copy(statusMessage = "重命名失败") }
                return@launch
            }
            val newUri = Uri.fromFile(newFile)
            loadShelfFiles()
            if (oldUri == pdfUri || oldUri in imageUris) {
                val cp = _uiState.value.currentPage
                val name = fileRepo.getFileName(newUri)
                if (fileRepo.isPdf(name)) openPdf(newUri, name, cp)
                else if (fileRepo.isImage(name)) openImages(listOf(newUri), name, cp)
            }
        }
    }

    private fun openShelfFile(uri: Uri) {
        if (_uiState.value.showShelf) {
            _uiState.update { it.copy(showShelf = false) }
        }
        thumbnailCache.clear()
        viewModelScope.launch {
            val name = fileRepo.getFileName(uri)
            val restorePage = pageMap[name] ?: 0
            when {
                fileRepo.isPdf(name) -> openPdf(uri, name, restorePage)
                fileRepo.isImage(name) -> openImages(listOf(uri), name, restorePage)
            }
        }
    }

    // ── PDF 书签（原生 Document Outline 持久化；颜色编码进 outline 标题 "#RRGGBB|标题"）──

    private fun readPdfBookmarks(uri: Uri): List<PageBookmark> = try {
        PDDocument.load(java.io.File(uri.path!!)).use { doc ->
            val outline = doc.documentCatalog.documentOutline ?: return@use emptyList()
            val result = mutableListOf<PageBookmark>()
            for (item in outline.children()) {
                val page = try { item.findDestinationPage(doc) } catch (_: Exception) { null } ?: continue
                val idx = doc.pages.indexOf(page)
                if (idx < 0) continue
                val raw = item.title ?: continue
                val m = Regex("^#([0-9A-Fa-f]{6})\\|(.*)$").find(raw)
                result += PageBookmark(
                    id = "${idx}_${result.size}",
                    page = idx,
                    title = (m?.groupValues?.get(2) ?: raw).take(50),
                    color = ((m?.groupValues?.get(1)?.toLong(16) ?: 0x8CC8FFL) or 0xFF000000L).toInt()
                )
            }
            result
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writePdfBookmarks() {
        val uri = pdfUri ?: return
        val bookmarks = _uiState.value.bookmarks
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cancelPageRendering()
                pdfRenderer.close()
                PDDocument.load(java.io.File(uri.path!!)).use { doc ->
                    val outline = PDDocumentOutline()
                    bookmarks.forEach { b ->
                        val item = PDOutlineItem()
                        item.title = "#%06X|%s".format(b.color and 0xFFFFFF, b.title.take(50))
                        val dest = PDPageFitWidthDestination()
                        dest.setPage(doc.pages[b.page.coerceIn(0, doc.numberOfPages - 1)])
                        item.setDestination(dest)
                        outline.addLast(item)
                    }
                    doc.documentCatalog.documentOutline = outline
                    doc.save(java.io.File(uri.path!!))
                }
                pdfRenderer.open(uri)
                preloadAround(_uiState.value.currentPage)
            } catch (e: Exception) {
                FaceLog.e("MSV_VM", "写入书签失败: ${e.message}", e)
                runCatching { pdfRenderer.open(uri) }
            }
        }
    }

    private fun addBookmark(page: Int, title: String, color: Int) {
        if (_uiState.value.mode !is Mode.Pdf) return
        _uiState.update {
            it.copy(bookmarks = it.bookmarks + PageBookmark(id = UUID.randomUUID().toString(), page = page, title = title.take(50), color = color))
        }
        writePdfBookmarks()
    }

    private fun deleteBookmark(id: String) {
        _uiState.update { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == id }) }
        writePdfBookmarks()
    }

    private fun renameBookmark(id: String, title: String, color: Int) {
        _uiState.update { it.copy(bookmarks = it.bookmarks.map { b -> if (b.id == id) b.copy(title = title.take(50), color = color) else b }) }
        writePdfBookmarks()
    }

    private fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    private fun toggleFace() {
        val newState = !_uiState.value.faceEnabled
        FaceLog.i("MSV_VM", "toggleFace: ${if (newState) "开启" else "关闭"}, isReady=${faceManager.isReady()}")
        _uiState.update { it.copy(faceEnabled = newState) }
        if (newState) {
            faceManager.updateState { it.copy(running = true, enabled = true) }
        } else {
            faceManager.updateState { it.copy(running = false, enabled = false) }
        }
    }

    private fun faceFlip(dir: Int) {
        FaceLog.i("MSV_VM", "faceFlip: dir=$dir")
        _uiState.update { it.copy(pendingFlip = dir) }
    }

    private fun flipDone() {
        FaceLog.i("MSV_VM", "flipDone")
        _uiState.update { it.copy(pendingFlip = null) }
    }

    private fun showFaceOverlay() {
        FaceLog.i("MSV_VM", "showFaceOverlay")
        _uiState.update { it.copy(showFaceOverlay = true) }
    }

    private fun hideFaceOverlay() {
        FaceLog.i("MSV_VM", "hideFaceOverlay")
        _uiState.update { it.copy(showFaceOverlay = false) }
        viewModelScope.launch { faceRepo.save(faceManager) }
    }

    // ── OTA 更新 ──

    /** 检查更新：Gitee/GitHub 并行查询，仅接受比当前版本新的候选，平手时 Gitee 优先 */
    private fun checkUpdate(manual: Boolean) {
        _uiState.update { it.copy(updateStatus = UpdateStatus.Checking, updateMessage = "") }
        viewModelScope.launch(Dispatchers.IO) {
            val gitee = updateRepo.fetchGiteeLatest()
            val github = updateRepo.fetchGitHubLatest()
            val installed = _uiState.value.appVersionName
            val best = listOfNotNull(gitee, github)
                .filter { compareVersions(it.tag, installed) > 0 }
                .reduceOrNull { acc, info -> if (compareVersions(info.tag, acc.tag) > 0) info else acc }
            when {
                best != null -> {
                    // 已下载未安装：跳过下载阶段直接进入安装
                    if (updateRepo.isDownloaded(best.apkUrl, best.tag)) {
                        _uiState.update {
                            it.copy(updateStatus = UpdateStatus.Downloaded, updateInfo = best, downloadProgress = 100, showUpdateDialog = false, statusMessage = "更新包已就绪")
                        }
                        installUpdate()
                    } else {
                        _uiState.update {
                            it.copy(updateStatus = UpdateStatus.Available, updateInfo = best, showUpdateDialog = true)
                        }
                    }
                }
                gitee != null || github != null -> _uiState.update {
                    if (manual) it.copy(updateStatus = UpdateStatus.UpToDate, updateMessage = "已是最新版本 v$installed")
                    else it.copy(updateStatus = UpdateStatus.Idle)
                }
                else -> _uiState.update {
                    if (manual) it.copy(updateStatus = UpdateStatus.Error, updateMessage = "检查失败，请检查网络")
                    else it.copy(updateStatus = UpdateStatus.Idle)
                }
            }
        }
    }

    /** 应用内下载更新包（带进度回调），完成后自动调起安装；设置面板同步显示进度与安装按钮 */
    private fun downloadUpdate() {
        val info = _uiState.value.updateInfo ?: return
        if (_uiState.value.updateStatus == UpdateStatus.Downloading) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            // 断点续传：本地已与远端等长时跳过下载直接进入安装
            if (updateRepo.isDownloaded(info.apkUrl, info.tag)) {
                _uiState.update {
                    it.copy(updateStatus = UpdateStatus.Downloaded, downloadProgress = 100, showUpdateDialog = false, statusMessage = "更新包已就绪")
                }
                installUpdate()
                return@launch
            }
            _uiState.update {
                it.copy(updateStatus = UpdateStatus.Downloading, showUpdateDialog = false, downloadProgress = 0, statusMessage = "开始下载 v${info.tag} 更新包…")
            }
            val file = updateRepo.downloadApk(info.apkUrl, info.tag) { pct ->
                _uiState.update { it.copy(downloadProgress = pct) }
            }
            if (file != null) {
                _uiState.update { it.copy(updateStatus = UpdateStatus.Downloaded, downloadProgress = 100, statusMessage = "更新包下载完成") }
                // 下载完成后自动调起安装（未授权"安装未知应用"时会在 installUpdate 内引导授权）
                installUpdate()
            } else {
                _uiState.update { it.copy(updateStatus = UpdateStatus.Error, updateMessage = "下载失败，已保留进度，请重试") }
            }
        }
    }

    private fun installUpdate() {
        val tag = _uiState.value.updateInfo?.tag ?: updateRepo.installedVersionName
        if (!updateRepo.isInstallPermissionGranted()) {
            _uiState.update { it.copy(statusMessage = "请先允许安装未知应用，授权后重试") }
            startActivity(updateRepo.unknownSourcesIntent())
            return
        }
        val intent = updateRepo.installIntent(tag)
        if (intent == null) {
            _uiState.update { it.copy(updateStatus = UpdateStatus.Error, updateMessage = "未找到更新包，请重新下载") }
            return
        }
        startActivity(intent)
    }

    private fun startActivity(intent: Intent) {
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    private fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    /** 展开/收起本版本更新日志；首次展开时按已安装版本 tag 从两平台拉取 release body */
    private fun toggleVersionLog() {
        val show = !_uiState.value.showVersionLog
        _uiState.update { it.copy(showVersionLog = show) }
        if (show && _uiState.value.versionNotes.isEmpty() && !_uiState.value.versionNotesLoading) {
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(versionNotesLoading = true) }
                val notes = updateRepo.fetchTagNotes(updateRepo.installedVersionName)
                _uiState.update {
                    it.copy(
                        versionNotes = notes?.ifEmpty { null } ?: "未找到当前版本的更新日志（可能离线或该版本未发布 Release）",
                        versionNotesLoading = false
                    )
                }
            }
        }
    }

    private fun resetZoom() {
        _uiState.update { it.copy(zoom = 1f, panOffsetX = 0f, panOffsetY = 0f) }
    }

    private fun reset() {
        pdfRenderer.close()
        imageUris = emptyList()
        pdfUri = null
        thumbnailCache.clear()
        cancelPageRendering()
        _uiState.update {
            ViewerState(
                isDarkTheme = it.isDarkTheme,
                appVersionName = it.appVersionName,
                appVersionCode = it.appVersionCode
            )
        }
        viewModelScope.launch { sessionRepo.clearSession() }
    }

    private fun reload() {
        val state = _uiState.value
        thumbnailCache.clear()
        val cp = state.currentPage
        when (state.mode) {
            is Mode.Pdf -> {
                pdfUri?.let { openPdf(it, state.fileName, cp) }
            }
            is Mode.Image -> {
                if (imageUris.isNotEmpty()) {
                    openImages(imageUris, state.fileName, cp)
                }
            }
            Mode.Idle -> {}
        }
    }

    private fun saveSession() {
        val state = _uiState.value
        viewModelScope.launch {
            val modeStr = when (state.mode) {
                is Mode.Pdf -> "pdf"
                is Mode.Image -> "image"
                Mode.Idle -> return@launch
            }
            val uris = when (state.mode) {
                is Mode.Pdf -> listOfNotNull(pdfUri?.toString())
                is Mode.Image -> imageUris.map { it.toString() }
                Mode.Idle -> emptyList()
            }
            sessionRepo.saveSession(
                mode = modeStr,
                currentPage = state.currentPage,
                uris = uris,
                fileName = state.fileName
            )
        }
    }

    fun getThumbnailUri(pageIndex: Int): Uri? = thumbnailCache[pageIndex]
        ?: imageUris.getOrNull(pageIndex)

    fun preloadPage(pageIndex: Int) {
        val state = _uiState.value
        val total = state.pageCount
        if (pageIndex !in 0 until total) return
        if (state.pageUris.containsKey(pageIndex)) return
        val pageW = state.pageWidth
        val pageH = state.pageHeight
        if (pageW <= 0) return
        val zoom = state.zoom
        viewModelScope.launch(Dispatchers.IO) {
            val uri = when (state.mode) {
                is Mode.Pdf -> renderPage(pageIndex, pageW, pageH, zoom)
                is Mode.Image -> imageUris.getOrNull(pageIndex)
                else -> null
            }
            if (uri != null) {
                _uiState.update { it.copy(pageUris = it.pageUris + (pageIndex to uri)) }
            }
        }
    }

    private fun preloadThumbnails() {
        val state = _uiState.value
        if (state.mode !is Mode.Pdf) return
        if (state.pageCount == 0) return
        _uiState.update { it.copy(thumbnailsLoading = true) }
        val dk = docCacheKey
        viewModelScope.launch(Dispatchers.IO) {
            for (i in 0 until state.pageCount) {
                if (thumbnailCache.containsKey(i)) continue
                val cachedFile = java.io.File(
                    getApplication<Application>().cacheDir,
                    "thumb_${dk}_$i.png"
                )
                if (cachedFile.exists()) {
                    thumbnailCache[i] = Uri.fromFile(cachedFile)
                    continue
                }
                val bmp = pdfRenderer.renderThumbnail(i, 200) ?: continue
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, cachedFile.outputStream())
                bmp.recycle()
                thumbnailCache[i] = Uri.fromFile(cachedFile)
            }
            _uiState.update { it.copy(thumbnailsLoading = false) }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val session = sessionRepo.sessionFlow.first()
            if (session == null || _uiState.value.mode != Mode.Idle) return@launch

            val uris = session.uris.mapNotNull { Uri.parse(it) }
            if (uris.isEmpty()) return@launch

            val accessible = try {
                getApplication<Application>().contentResolver.openInputStream(uris.first())?.close()
                true
            } catch (_: Exception) { false }

            if (!accessible) {
                sessionRepo.clearSession()
                _uiState.update { it.copy(statusMessage = "上次文件无法访问，请重新打开") }
                return@launch
            }

            if (session.mode == "pdf") {
                openPdf(uris.first(), session.fileName, session.currentPage)
            } else if (session.mode == "image") {
                openImages(uris, session.fileName, session.currentPage)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer.close()
    }
}
