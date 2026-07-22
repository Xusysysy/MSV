package com.music.msv.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.msv.data.model.Mode
import com.music.msv.data.model.ShelfFile
import com.music.msv.data.model.ShelfSort
import com.music.msv.data.model.ViewerEvent
import com.music.msv.data.model.ViewerState
import com.music.msv.data.pdf.PdfPageRenderer
import com.music.msv.data.repository.FileRepository
import com.music.msv.data.repository.SessionRepository
import com.music.msv.facer.FaceRecognitionManager
import com.music.msv.facer.FaceRecognitionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepo = FileRepository(application)
    private val sessionRepo = SessionRepository(application)
    private val pdfRenderer = PdfPageRenderer(application)
    private val faceRepo = FaceRecognitionRepository(application)
    val faceManager = FaceRecognitionManager(application)

    private val _uiState = MutableStateFlow(ViewerState())
    val uiState: StateFlow<ViewerState> = _uiState.asStateFlow()

    private var imageUris: List<Uri> = emptyList()
    private var pdfUri: Uri? = null
    private var loadJob: Job? = null
    private var preloadJob: Job? = null
    private val thumbnailCache = java.util.concurrent.ConcurrentHashMap<Int, Uri>()
    private var pageMap = mapOf<String, Int>()

    init {
        viewModelScope.launch {
            sessionRepo.getPageMap().collect { pageMap = it }
        }
        viewModelScope.launch {
            faceRepo.prefsFlow.collect { prefs ->
                faceManager.updateState(
                    FaceRecognitionManager.FaceState(
                        isEnabled = prefs.enabled,
                        showMesh = prefs.showMesh,
                        triggerMode = when (prefs.triggerMode) {
                            "WINK" -> FaceRecognitionManager.TriggerMode.WINK
                            "PUCKER" -> FaceRecognitionManager.TriggerMode.PUCKER
                            else -> FaceRecognitionManager.TriggerMode.BOTH
                        },
                        thresholds = FaceRecognitionManager.Thresholds(
                            blink = prefs.blinkThreshold,
                            winkDiff = prefs.winkDiff,
                            pucker = prefs.puckerThreshold,
                            puckerBias = prefs.puckerBias
                        ),
                        cooldownMs = prefs.cooldownMs.toLong()
                    )
                )
            }
        }
        faceManager.setOnGestureDetected { gesture ->
            when (gesture) {
                FaceRecognitionManager.Gesture.RIGHT_WINK,
                FaceRecognitionManager.Gesture.LEFT_PUCKER -> {
                    onEvent(ViewerEvent.NextPage)
                }
                FaceRecognitionManager.Gesture.LEFT_WINK,
                FaceRecognitionManager.Gesture.RIGHT_PUCKER -> {
                    onEvent(ViewerEvent.PrevPage)
                }
                FaceRecognitionManager.Gesture.NONE -> {}
            }
        }
        restoreSession()
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
            ViewerEvent.ToggleShelfSort -> toggleShelfSort()
            is ViewerEvent.SetSpreadMode -> setSpreadMode(event.spread)
            ViewerEvent.ToggleFace -> toggleFace()
            ViewerEvent.ShowFaceOverlay -> showFaceOverlay()
            ViewerEvent.HideFaceOverlay -> hideFaceOverlay()
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
        saveSession()
    }

    private fun goToPage(page: Int) {
        val state = _uiState.value
        if (state.pageCount == 0) return
        val target = page.coerceIn(0, state.pageCount - 1)
        if (target == state.currentPage) return
        _uiState.update { it.copy(currentPage = target) }
        preloadAround(target)
        saveSession()
    }

    private fun renderPageToCacheComputeSize(pageIndex: Int, ratio: Float) {
        val vw = _uiState.value.viewportWidth
        val zw = _uiState.value.pageWidth
        if (vw <= 0 || zw > 0) return
        val pageW = vw
        val pageH = (vw / ratio).toInt()
        _uiState.update { it.copy(pageWidth = pageW, pageHeight = pageH) }
        val zoom = _uiState.value.zoom
        viewModelScope.launch(Dispatchers.IO) {
            val uri = when (_uiState.value.mode) {
                is Mode.Pdf -> renderPage(pageIndex, pageW, pageH, zoom)
                is Mode.Image -> imageUris.getOrNull(pageIndex)
                else -> null
            }
            if (uri != null) {
                _uiState.update { it.copy(pageUris = it.pageUris + (pageIndex to uri)) }
            }
        }
    }

    fun preloadAround(center: Int) {
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
        preloadJob = viewModelScope.launch(Dispatchers.IO) {
            val needRender = (keepMin..keepMax).filter { !oldUris.containsKey(it) }
            for (i in needRender) {
                if (i == center) {
                    val uri = renderPage(i, pageW, pageH, zoom, 95)
                    if (uri != null) _uiState.update { it.copy(pageUris = it.pageUris + (i to uri)) }
                }
            }
            val next = center + 1
            if (next in needRender && next in 0 until total) {
                val uri = renderPage(next, pageW, pageH, zoom, 95)
                if (uri != null) _uiState.update { it.copy(pageUris = it.pageUris + (next to uri)) }
            }
            val near = needRender.filter { it != center && it != next && kotlin.math.abs(it - center) <= 1 }
            if (near.isNotEmpty()) {
                val rendered = near.map { i ->
                    async { renderPage(i, pageW, pageH, zoom, 90)?.let { i to it } }
                }.awaitAll().filterNotNull().toMap()
                if (rendered.isNotEmpty()) _uiState.update { it.copy(pageUris = it.pageUris + rendered) }
            }
            val mid = needRender.filter { kotlin.math.abs(it - center) in 2..3 }
            if (mid.isNotEmpty()) {
                val sW = (pageW * 0.6f).toInt().coerceAtLeast(1)
                val sH = (pageH * 0.6f).toInt().coerceAtLeast(1)
                val rendered = mid.map { i ->
                    async { renderPage(i, sW, sH, zoom, 85)?.let { i to it } }
                }.awaitAll().filterNotNull().toMap()
                if (rendered.isNotEmpty()) _uiState.update { it.copy(pageUris = it.pageUris + rendered) }
            }
            val far = needRender.filter { kotlin.math.abs(it - center) >= 4 }
            if (far.isNotEmpty()) {
                val sW = (pageW * 0.4f).toInt().coerceAtLeast(1)
                val sH = (pageH * 0.4f).toInt().coerceAtLeast(1)
                val rendered = far.map { i ->
                    async { renderPage(i, sW, sH, zoom, 80)?.let { i to it } }
                }.awaitAll().filterNotNull().toMap()
                if (rendered.isNotEmpty()) _uiState.update { it.copy(pageUris = it.pageUris + rendered) }
            }
            _uiState.update { prev ->
                val merged = prev.pageUris.toMutableMap()
                val toRemove = merged.keys.filter { it !in keepMin..keepMax }
                for (page in toRemove) {
                    val uri = merged.remove(page)
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
            "page_${docCacheKey}_$pageIndex.jpg"
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
        _uiState.update { it.copy(showThumbnails = show) }
        if (show) preloadThumbnails()
    }

    private fun toggleShelf() {
        val show = !_uiState.value.showShelf
        _uiState.update { it.copy(showShelf = show) }
        if (show) loadShelfFiles()
    }

    private fun toggleShelfSort() {
        val current = _uiState.value.shelfSortBy
        val next = if (current == ShelfSort.DATE) ShelfSort.NAME else ShelfSort.DATE
        _uiState.update { it.copy(shelfSortBy = next) }
        loadShelfFiles()
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
                    thumbnailUri = if (fileRepo.isImage(file.name)) Uri.fromFile(file) else null
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

    private fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    private fun toggleFace() {
        val newState = !_uiState.value.faceEnabled
        _uiState.update { it.copy(faceEnabled = newState) }
        if (newState) {
            if (!faceManager.isInitialized()) {
                val ok = faceManager.initialize()
                if (!ok) {
                    _uiState.update { it.copy(faceEnabled = false, statusMessage = "面部识别模型加载失败") }
                    return
                }
            }
            faceManager.updateState(faceManager.getState().copy(isEnabled = true, isRunning = true))
        } else {
            faceManager.updateState(faceManager.getState().copy(isEnabled = false, isRunning = false))
        }
        val managerState = faceManager.getState()
        viewModelScope.launch {
            faceRepo.savePrefs(
                FaceRecognitionRepository.FacePrefs(
                    enabled = managerState.isEnabled,
                    showMesh = managerState.showMesh,
                    triggerMode = managerState.triggerMode.name,
                    blinkThreshold = managerState.thresholds.blink,
                    winkDiff = managerState.thresholds.winkDiff,
                    puckerThreshold = managerState.thresholds.pucker,
                    puckerBias = managerState.thresholds.puckerBias,
                    cooldownMs = managerState.cooldownMs.toInt()
                )
            )
        }
    }

    private fun showFaceOverlay() {
        _uiState.update { it.copy(showFaceOverlay = true) }
    }

    private fun hideFaceOverlay() {
        _uiState.update { it.copy(showFaceOverlay = false) }
        val managerState = faceManager.getState()
        viewModelScope.launch {
            faceRepo.savePrefs(
                FaceRecognitionRepository.FacePrefs(
                    enabled = managerState.isEnabled,
                    showMesh = managerState.showMesh,
                    triggerMode = managerState.triggerMode.name,
                    blinkThreshold = managerState.thresholds.blink,
                    winkDiff = managerState.thresholds.winkDiff,
                    puckerThreshold = managerState.thresholds.pucker,
                    puckerBias = managerState.thresholds.puckerBias,
                    cooldownMs = managerState.cooldownMs.toInt()
                )
            )
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
        _uiState.update { ViewerState(isDarkTheme = it.isDarkTheme) }
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
