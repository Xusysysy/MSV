package com.music.msv.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.msv.data.model.Mode
import com.music.msv.data.model.ViewerEvent
import com.music.msv.data.model.ViewerState
import com.music.msv.data.pdf.PdfPageRenderer
import com.music.msv.data.repository.FileRepository
import com.music.msv.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val _uiState = MutableStateFlow(ViewerState())
    val uiState: StateFlow<ViewerState> = _uiState.asStateFlow()

    private var imageUris: List<Uri> = emptyList()
    private var pdfUri: Uri? = null
    private var loadJob: Job? = null
    private var preloadJob: Job? = null
    private val thumbnailCache = java.util.concurrent.ConcurrentHashMap<Int, Uri>()

    init {
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
            ViewerEvent.NextPage -> goToPage(_uiState.value.currentPage + 1)
            ViewerEvent.PrevPage -> goToPage(_uiState.value.currentPage - 1)
            is ViewerEvent.SetZoom -> setZoom(event.zoom)
            is ViewerEvent.PanBy -> panBy(event.dx, event.dy)
            is ViewerEvent.UpdateViewportSize -> updateViewportSize(event.width, event.height)
            ViewerEvent.ToggleUI -> toggleUI()
            ViewerEvent.ToggleThumbnails -> toggleThumbnails()
            ViewerEvent.ToggleTheme -> toggleTheme()
            ViewerEvent.ResetZoom -> resetZoom()
            ViewerEvent.Reset -> reset()
            ViewerEvent.Reload -> reload()
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
        }
    }

    private fun openPdf(uri: Uri, name: String, restorePage: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
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
                        isLoading = false,
                        pageCount = pageCount,
                        currentPage = rp,
                        fileName = name,
                        statusMessage = "已加载: $name",
                        zoom = 1f,
                        panOffsetX = 0f,
                        panOffsetY = 0f,
                        pageUris = emptyMap(),
                        pageWidth = 0,
                        pageHeight = 0,
                        viewportWidth = 0,
                        viewportHeight = 0
                    )
                }
                renderPageToCacheComputeSize(rp, ratio)
                preloadAround(rp)
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
                isLoading = false,
                pageCount = uris.size,
                currentPage = page,
                fileName = name,
                statusMessage = "已加载: $name (${uris.size} 页)",
                zoom = 1f,
                panOffsetX = 0f,
                panOffsetY = 0f,
                pageUris = uris.mapIndexed { i, u -> i to u }.toMap(),
                pageWidth = 0,
                pageHeight = 0,
                viewportWidth = 0,
                viewportHeight = 0
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
                _uiState.update {
                    it.copy(pageUris = it.pageUris + (pageIndex to uri))
                }
            }
        }
    }

    fun preloadAround(center: Int) {
        val state = _uiState.value
        val total = state.pageCount
        val pageW = state.pageWidth
        val pageH = state.pageHeight
        if (pageW <= 0) return
        if (center !in 0 until total) return
        val zoom = state.zoom
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch(Dispatchers.IO) {
            val newUris = mutableMapOf<Int, Uri>()
            val uri = when (state.mode) {
                is Mode.Pdf -> renderPage(center, pageW, pageH, zoom)
                is Mode.Image -> imageUris.getOrNull(center)
                else -> null
            }
            if (uri != null) {
                _uiState.update { it.copy(pageUris = it.pageUris + (center to uri)) }
            }
            for (i in (center - 3)..(center + 3)) {
                if (i == center || i !in 0 until total || state.pageUris.containsKey(i)) continue
                val uri = when (state.mode) {
                    is Mode.Pdf -> renderPage(i, pageW, pageH, zoom)
                    is Mode.Image -> imageUris.getOrNull(i)
                    else -> null
                }
                if (uri != null) newUris[i] = uri
            }
            if (newUris.isNotEmpty()) {
                _uiState.update { it.copy(pageUris = it.pageUris + newUris) }
            }
        }
    }

    private fun renderPage(pageIndex: Int, pageW: Int, pageH: Int, zoom: Float): Uri? {
        if (pageW <= 0 || pageH <= 0) return null
        val bmp = pdfRenderer.renderPage(pageIndex, pageW, pageH, zoom) ?: return null
        val cachedFile = java.io.File(
            getApplication<Application>().cacheDir,
            "page_$pageIndex.png"
        )
        cachedFile.delete()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, cachedFile.outputStream())
        bmp.recycle()
        return Uri.fromFile(cachedFile).buildUpon()
            .appendQueryParameter("t", System.currentTimeMillis().toString())
            .build()
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

    private fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
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
        viewModelScope.launch(Dispatchers.IO) {
            for (i in 0 until state.pageCount) {
                if (thumbnailCache.containsKey(i)) continue
                val cachedFile = java.io.File(
                    getApplication<Application>().cacheDir,
                    "thumb_$i.png"
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
