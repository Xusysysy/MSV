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
            intent.action = Intent.ACTION_MAIN // mark as handled
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
            ViewerEvent.AnimationStart -> _uiState.update { it.copy(isAnimating = true) }
            ViewerEvent.AnimationEnd -> _uiState.update { it.copy(isAnimating = false) }
        }
    }

    private fun handleFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
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

    private fun openPdf(uri: Uri, name: String) {
        _uiState.update { it.copy(isLoading = true, statusMessage = "正在加载 PDF...") }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pageCount = pdfRenderer.open(uri)
                if (pageCount == 0) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "无法打开 PDF") }
                    return@launch
                }
                pdfUri = uri
                imageUris = emptyList()
                _uiState.update {
                    it.copy(
                        mode = Mode.Pdf,
                        isLoading = false,
                        pageCount = pageCount,
                        currentPage = 0,
                        fileName = name,
                        statusMessage = "已加载: $name",
                        zoom = 1f,
                        panOffsetX = 0f,
                        panOffsetY = 0f
                    )
                }
                loadCurrentPage()
                saveSession()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "PDF 加载失败: ${e.message}") }
            }
        }
    }

    private fun openImages(uris: List<Uri>, name: String) {
        _uiState.update { it.copy(isLoading = true, statusMessage = "正在加载图片...") }
        imageUris = uris.sortedBy { it.lastPathSegment }
        pdfUri = null
        pdfRenderer.close()
        _uiState.update {
            it.copy(
                mode = Mode.Image,
                isLoading = false,
                pageCount = uris.size,
                currentPage = 0,
                fileName = name,
                statusMessage = "已加载: $name (${uris.size} 页)",
                zoom = 1f,
                panOffsetX = 0f,
                panOffsetY = 0f
            )
        }
        loadCurrentPage()
        saveSession()
    }

    private fun goToPage(page: Int) {
        val state = _uiState.value
        if (state.pageCount == 0) return
        val target = page.coerceIn(0, state.pageCount - 1)
        if (target == state.currentPage && !state.isAnimating) return

        _uiState.update {
            it.copy(isGoingForward = target > state.currentPage, currentPage = target)
        }
        loadCurrentPage()
        saveSession()
        preloadAdjacentPages()
    }

    private fun loadCurrentPage() {
        loadJob?.cancel()
        val state = _uiState.value
        val targetPage = state.currentPage
        val vw = state.viewportWidth
        val vh = state.viewportHeight
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val currentUri: Uri? = when (state.mode) {
                is Mode.Image -> imageUris.getOrNull(targetPage)
                is Mode.Pdf -> renderPageToCache(targetPage, vw, vh, state.zoom)
                else -> null
            }
            _uiState.update { it.copy(currentPageUri = currentUri) }
        }
    }

    private fun renderPageToCache(pageIndex: Int, vw: Int, vh: Int, zoom: Float): Uri? {
        if (vw <= 0 || vh <= 0) return null
        val bmp = pdfRenderer.renderPage(pageIndex, vw, vh, zoom) ?: return null
        val cachedFile = java.io.File(
            getApplication<Application>().cacheDir,
            "page_$pageIndex.png"
        )
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, cachedFile.outputStream())
        bmp.recycle()
        return Uri.fromFile(cachedFile)
    }

    private fun preloadAdjacentPages() {
        val state = _uiState.value
        if (state.mode !is Mode.Pdf) return
        val cp = state.currentPage
        val vw = state.viewportWidth
        val vh = state.viewportHeight
        if (vw <= 0 || vh <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            var prevUri: Uri? = null
            var nextUri: Uri? = null
            val prev = (cp - 1).coerceAtLeast(0)
            val next = (cp + 1).coerceAtMost(state.pageCount - 1)
            if (prev != cp) {
                prevUri = renderPageToCache(prev, vw, vh, state.zoom)
            }
            if (next != cp) {
                nextUri = renderPageToCache(next, vw, vh, state.zoom)
            }
            _uiState.update { it.copy(prevPageUri = prevUri, nextPageUri = nextUri) }

            for (offset in 2..3) {
                val pp = (cp - offset).coerceAtLeast(0)
                val nn = (cp + offset).coerceAtMost(state.pageCount - 1)
                if (pp != cp) pdfRenderer.renderPage(pp, vw, vh, state.zoom)
                if (nn != cp) pdfRenderer.renderPage(nn, vw, vh, state.zoom)
            }
        }
    }

    private fun updateViewportSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val state = _uiState.value
        val changed = state.viewportWidth != width || state.viewportHeight != height
        _uiState.update { it.copy(viewportWidth = width, viewportHeight = height) }
        if (changed) {
            loadCurrentPage()
            preloadAdjacentPages()
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
        _uiState.update { it.copy(showThumbnails = !it.showThumbnails) }
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
        _uiState.update {
            ViewerState(isDarkTheme = it.isDarkTheme)
        }
        viewModelScope.launch {
            sessionRepo.clearSession()
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
                openPdf(uris.first(), session.fileName)
                goToPage(session.currentPage)
            } else if (session.mode == "image") {
                openImages(uris, session.fileName)
                goToPage(session.currentPage)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer.close()
    }
}
