package com.music.msv.data.model

import android.net.Uri

data class ViewerState(
    val mode: Mode = Mode.Idle,
    val currentPage: Int = 0,
    val pageCount: Int = 0,
    val zoom: Float = 1f,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val isAnimating: Boolean = false,
    val isGoingForward: Boolean = true,
    val showUI: Boolean = true,
    val showThumbnails: Boolean = false,
    val isDarkTheme: Boolean = true,
    val statusMessage: String = "",
    val isLoading: Boolean = false,
    val fileName: String = "",
    val currentPageUri: Uri? = null,
    val prevPageUri: Uri? = null
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
    data object ToggleUI : ViewerEvent()
    data object ToggleThumbnails : ViewerEvent()
    data object ToggleTheme : ViewerEvent()
    data object ResetZoom : ViewerEvent()
    data object Reset : ViewerEvent()
    data object AnimationStart : ViewerEvent()
    data object AnimationEnd : ViewerEvent()
}
