package com.music.msv.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.music.msv.data.model.Mode
import com.music.msv.data.model.ViewerEvent
import com.music.msv.ui.components.BottomFooter
import com.music.msv.ui.components.EmptyView
import com.music.msv.ui.components.LoadingOverlay
import com.music.msv.ui.components.Stage
import com.music.msv.ui.components.ThumbnailPanel
import com.music.msv.ui.components.TopBar
import com.music.msv.viewmodel.ViewerViewModel
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(viewModel: ViewerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onEvent(ViewerEvent.FilesSelected(uris))
        }
    }

    val openFilePicker: () -> Unit = {
        filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
    }

    val isDark = state.isDarkTheme
    val shellBg = if (isDark) Color(0xFF141824) else Color(0xFFE8ECF5)
    val shellBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val shade = if (isDark) Color(0x5C05080E) else Color(0x99E4E8F3)
    val appBg = if (isDark) Color(0xFF0F1220) else Color(0xFFDFE6F5)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBg)
            .padding(16.dp)
    ) {
        // Shell
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(shellBg)
                .border(1.dp, shellBorder, RoundedCornerShape(28.dp))
        ) {
            when (state.mode) {
                Mode.Idle -> {
                    EmptyView(isDark = isDark, onUploadClick = openFilePicker)
                }
                else -> {
                    // Stage
                    Stage(
                        isDark = isDark,
                        contentUri = state.currentPageUri,
                        prevUri = state.prevPageUri,
                        zoom = state.zoom,
                        panOffsetX = state.panOffsetX,
                        panOffsetY = state.panOffsetY,
                        showUI = state.showUI,
                        onEdgeLeftTap = { viewModel.onEvent(ViewerEvent.PrevPage) },
                        onEdgeRightTap = { viewModel.onEvent(ViewerEvent.NextPage) },
                        onCenterTap = { viewModel.onEvent(ViewerEvent.ToggleUI) },
                        onDoubleTap = { viewModel.onEvent(ViewerEvent.ResetZoom) },
                        onZoomChange = { viewModel.onEvent(ViewerEvent.SetZoom(it)) },
                        onPanChange = { dx, dy -> viewModel.onEvent(ViewerEvent.PanBy(dx, dy)) },
                        onSwipeLeft = { viewModel.onEvent(ViewerEvent.NextPage) },
                        onSwipeRight = { viewModel.onEvent(ViewerEvent.PrevPage) }
                    )

                    // Loading overlay
                    AnimatedVisibility(
                        visible = state.isLoading,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        LoadingOverlay(isDark = isDark, visible = state.isLoading)
                    }
                }
            }

            // TopBar
            AnimatedVisibility(
                visible = state.showUI && state.mode != Mode.Idle,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TopBar(
                    isDark = isDark,
                    fileName = state.fileName,
                    currentPage = state.currentPage + 1,
                    pageCount = state.pageCount,
                    showPageNav = state.mode != Mode.Idle,
                    onUploadClick = openFilePicker,
                    onPageNumberSubmit = { page -> viewModel.onEvent(ViewerEvent.GoToPage(page)) },
                    onThumbnailsClick = { viewModel.onEvent(ViewerEvent.ToggleThumbnails) },
                    onFullscreenClick = { viewModel.onEvent(ViewerEvent.ToggleTheme) },
                    onResetClick = { viewModel.onEvent(ViewerEvent.Reset) }
                )
            }

            // Footer
            AnimatedVisibility(
                visible = state.showUI && state.mode != Mode.Idle,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            ) {
                BottomFooter(
                    isDark = isDark,
                    statusMessage = state.statusMessage
                )
            }

            // Thumbnail panel
            AnimatedVisibility(
                visible = state.showThumbnails && state.pageCount > 0,
                enter = slideInHorizontally { it / 3 } + fadeIn(),
                exit = slideOutHorizontally { it / 3 } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box {
                    ThumbnailPanel(
                        isDark = isDark,
                        pageCount = state.pageCount,
                        currentPage = state.currentPage,
                        getThumbnailUri = { page ->
                            state.currentPageUri // TODO: generate thumbnails
                        },
                        onPageSelected = { page -> viewModel.onEvent(ViewerEvent.GoToPage(page)) },
                        onClose = { viewModel.onEvent(ViewerEvent.ToggleThumbnails) }
                    )
                }
            }

            // Shade backdrop for thumbnails
            if (state.showThumbnails && state.pageCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(shade)
                        .padding(end = 300.dp)
                        .then(Modifier.offset { IntOffset.Zero })
                )
            }
        }
    }
}
