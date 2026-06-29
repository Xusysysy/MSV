package com.music.msv.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.msv.data.model.Mode
import com.music.msv.data.model.ViewerEvent
import com.music.msv.data.model.ViewerState
import com.music.msv.data.pdf.PdfPageRenderer
import com.music.msv.data.repository.FileRepository
import com.music.msv.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        restoreSession()
    }

    fun onEvent(event: ViewerEvent) {
        when (event) {
            is ViewerEvent.FilesSelected -> handleFilesSelected(event.uris)
            is ViewerEvent.GoToPage -> goToPage(event.page)
            ViewerEvent.NextPage -> goToPage(_uiState.value.currentPage + 1)
            ViewerEvent.PrevPage -> goToPage(_uiState.value.currentPage - 1)
            is ViewerEvent.SetZoom -> setZoom(event.zoom)
            is ViewerEvent.PanBy -> panBy(event.dx, event.dy)
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
        val uri = uris.first()
        val name = fileRepo.getFileName(uri)

        when {
            fileRepo.isPdf(name) -> openPdf(uri, name)
            fileRepo.isImage(name) -> openImages(uris, name)
            else -> {
                _uiState.update {
                    it.copy(statusMessage = "不支持的文件类型", showThumbnails = false)
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
        if (state.isAnimating || state.pageCount == 0) return
        val target = page.coerceIn(0, state.pageCount - 1)
        if (target == state.currentPage) return

        _uiState.update { it.copy(isAnimating = true, currentPage = target) }
        loadCurrentPage()
        saveSession()
        viewModelScope.launch {
            kotlinx.coroutines.delay(160)
            _uiState.update { it.copy(isAnimating = false) }
        }
    }

    private fun loadCurrentPage() {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            val currentUri: Uri? = when (state.mode) {
                is Mode.Image -> imageUris.getOrNull(state.currentPage)
                is Mode.Pdf -> {
                    val bmp = pdfRenderer.renderPage(state.currentPage, 1080, 1920, state.zoom)
                    // Write bitmap to cache file for Coil
                    if (bmp != null) {
                        val fileName = "page_${state.currentPage}.png"
                        val cachedFile = java.io.File(getApplication<android.app.Application>().cacheDir, fileName)
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, cachedFile.outputStream())
                        bmp.recycle()
                        Uri.fromFile(cachedFile)
                    } else null
                }
                else -> null
            }
            _uiState.update { it.copy(currentPageUri = currentUri) }
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
            sessionRepo.sessionFlow.collect { session ->
                if (session != null && _uiState.value.mode == Mode.Idle) {
                    val uris = session.uris.mapNotNull { Uri.parse(it) }
                    if (session.mode == "pdf") {
                        openPdf(uris.first(), session.fileName)
                        goToPage(session.currentPage)
                    } else if (session.mode == "image") {
                        openImages(uris, session.fileName)
                        goToPage(session.currentPage)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer.close()
    }
}
