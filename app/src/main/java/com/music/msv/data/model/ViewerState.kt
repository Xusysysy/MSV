package com.music.msv.data.model

import android.net.Uri

data class ViewerState(
    val mode: Mode = Mode.Idle,
    val currentPage: Int = 0,
    val pageCount: Int = 0,
    val zoom: Float = 1f,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val showUI: Boolean = true,
    val showThumbnails: Boolean = false,
    val showShelf: Boolean = false,
    val isDarkTheme: Boolean = true,
    val statusMessage: String = "",
    val isLoading: Boolean = false,
    val fileName: String = "",
    val pageUris: Map<Int, Uri> = emptyMap(),
    val pageWidth: Int = 0,
    val pageHeight: Int = 0,
    val viewportWidth: Int = 0,
    val viewportHeight: Int = 0,
    val thumbnailsLoading: Boolean = false,
    val shelfFiles: List<ShelfFile> = emptyList()
)

sealed class Mode {
    data object Idle : Mode()
    data object Image : Mode()
    data object Pdf : Mode()
}

sealed class ViewerEvent {
    data class FilesSelected(val uris: List<Uri>) : ViewerEvent()
    data class GoToPage(val page: Int) : ViewerEvent()
    data object NextPage : ViewerEvent()
    data object PrevPage : ViewerEvent()
    data class SetZoom(val zoom: Float) : ViewerEvent()
    data class PanBy(val dx: Float, val dy: Float) : ViewerEvent()
    data class UpdateViewportSize(val width: Int, val height: Int) : ViewerEvent()
    data object ToggleUI : ViewerEvent()
    data object ToggleThumbnails : ViewerEvent()
    data object ToggleTheme : ViewerEvent()
    data object ResetZoom : ViewerEvent()
    data object Reset : ViewerEvent()
    data object Reload : ViewerEvent()
    data object ToggleShelf : ViewerEvent()
    data class OpenShelfFile(val uri: Uri) : ViewerEvent()
}

data class ShelfFile(
    val name: String,
    val uri: Uri,
    val thumbnailUri: Uri? = null
)
