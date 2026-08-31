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
    val isDarkTheme: Boolean = false,
    val statusMessage: String = "",
    val isLoading: Boolean = false,
    val fileName: String = "",
    val pageUris: Map<Int, Uri> = emptyMap(),
    val pageWidth: Int = 0,
    val pageHeight: Int = 0,
    val viewportWidth: Int = 0,
    val viewportHeight: Int = 0,
    val thumbnailsLoading: Boolean = false,
    val shelfFiles: List<ShelfFile> = emptyList(),
    val shelfSortBy: ShelfSort = ShelfSort.DATE,
    val isSpreadMode: Boolean = false,
    val faceEnabled: Boolean = false,
    val faceActive: Boolean = false,
    val showFaceOverlay: Boolean = false,
    val pendingFlip: Int? = null,
    val showSettings: Boolean = false,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val updateInfo: UpdateInfo? = null,
    val updateMessage: String = "",
    val showUpdateDialog: Boolean = false,
    val showDownloadFailDialog: Boolean = false,
    val showVersionLog: Boolean = false,
    val versionNotes: String = "",
    val versionNotesLoading: Boolean = false,
    val downloadProgress: Int = 0,
    val bookmarks: List<PageBookmark> = emptyList(),
    val zoomMode: Boolean = false
)

enum class ShelfSort { NAME, DATE }

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
    data class RenameShelfFile(val uri: Uri, val newName: String) : ViewerEvent()
    data class SetShelfSort(val sort: ShelfSort) : ViewerEvent()
    data class DeleteShelfFile(val uri: Uri) : ViewerEvent()
    data class SetSpreadMode(val spread: Boolean) : ViewerEvent()
    data object ToggleFace : ViewerEvent()
    data object ShowFaceOverlay : ViewerEvent()
    data object HideFaceOverlay : ViewerEvent()
    data object FlipDone : ViewerEvent()
    data object ToggleSettings : ViewerEvent()
    data object CheckUpdate : ViewerEvent()
    data object DownloadUpdate : ViewerEvent()
    data object InstallUpdate : ViewerEvent()
    data object DismissUpdateDialog : ViewerEvent()
    data object DismissDownloadFailDialog : ViewerEvent()
    data object ToggleVersionLog : ViewerEvent()
    data object PauseDownload : ViewerEvent()
    data class AddBookmark(val page: Int, val title: String, val color: Int) : ViewerEvent()
    data class DeleteBookmark(val id: String) : ViewerEvent()
    data class RenameBookmark(val id: String, val title: String, val color: Int) : ViewerEvent()
    data object EnterZoomMode : ViewerEvent()
    data object ExitZoomMode : ViewerEvent()
}

data class ShelfFile(
    val name: String,
    val uri: Uri,
    val thumbnailUri: Uri? = null,
    val lastModified: Long = 0
)

data class UpdateInfo(
    val tag: String,
    val notes: String,
    val fullNotes: String,
    val apkUrl: String,
    val apkUrlFallback: String = "",
    val source: String
)

data class PageBookmark(
    val id: String,
    val page: Int,
    val title: String,
    val color: Int
)

enum class UpdateStatus {
    Idle, Checking, UpToDate, Available, Downloading, Paused, Downloaded, Error
}
