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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.music.msv.data.model.Mode
import com.music.msv.data.model.ViewerEvent
import com.music.msv.ui.components.EmptyView
import com.music.msv.ui.components.LoadingOverlay
import com.music.msv.ui.components.BottomFooter
import com.music.msv.ui.components.ShelfPanel
import com.music.msv.ui.components.Stage
import com.music.msv.ui.components.ThumbnailPanel
import com.music.msv.ui.components.TopBar
import com.music.msv.facer.FaceRecognitionOverlay
import com.music.msv.viewmodel.ViewerViewModel

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

    var showPageDialog by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    val isDark = state.isDarkTheme
    val appBg = if (isDark) Color(0xFF0F1220) else Color(0xFFDFE6F5)

    val isViewing = state.mode != Mode.Idle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBg)
            .then(if (!isViewing) Modifier.padding(16.dp) else Modifier)
    ) {
        // Shell for idle mode, plain container for viewing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isViewing) {
                        Modifier
                            .shadow(24.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (isDark) Color(0xFF141824) else Color(0xFFE8ECF5))
                            .border(1.dp, if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230), RoundedCornerShape(28.dp))
                    } else Modifier
                )
        ) {
            when (state.mode) {
                Mode.Idle -> {
                    EmptyView(
                        isDark = isDark,
                        onShelfClick = { viewModel.onEvent(ViewerEvent.ToggleShelf) }
                    )
                }
                else -> {
                    Stage(
                        isDark = isDark,
                        pageUris = state.pageUris,
                        currentPage = state.currentPage,
                        pageCount = state.pageCount,
                        pageWidth = state.pageWidth,
                        pageHeight = state.pageHeight,
                        zoom = state.zoom,
                        panOffsetX = state.panOffsetX,
                        panOffsetY = state.panOffsetY,
                        isSpreadMode = state.isSpreadMode,
                        onCenterTap = { viewModel.onEvent(ViewerEvent.ToggleUI) },
                        onDoubleTap = { viewModel.onEvent(ViewerEvent.ResetZoom) },
                        onZoomChange = { viewModel.onEvent(ViewerEvent.SetZoom(it)) },
                        onPanChange = { dx, dy -> viewModel.onEvent(ViewerEvent.PanBy(dx, dy)) },
                        onNextPage = { viewModel.onEvent(ViewerEvent.NextPage) },
                        onPrevPage = { viewModel.onEvent(ViewerEvent.PrevPage) },
                        onViewportSizeChanged = { w, h ->
                            viewModel.onEvent(ViewerEvent.UpdateViewportSize(w, h))
                        },
                        onSpreadModeChanged = { viewModel.onEvent(ViewerEvent.SetSpreadMode(it)) },
                        onPreloadAround = { viewModel.preloadAround(it) }
                    )

                    LoadingOverlay(isDark = isDark, visible = state.isLoading)
                }
            }

            AnimatedVisibility(
                visible = state.showUI && isViewing,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = if (isViewing) 8.dp else 16.dp, vertical = 8.dp)
            ) {
                TopBar(
                    isDark = isDark,
                    fileName = state.fileName,
                    currentPage = state.currentPage + 1,
                    pageCount = state.pageCount,
                    showPageNav = isViewing,
                    onShelfClick = { viewModel.onEvent(ViewerEvent.ToggleShelf) },
                    onPageJumpClick = {
                        pageInput = (state.currentPage + 1).toString()
                        showPageDialog = true
                    },
                    onThumbnailsClick = { viewModel.onEvent(ViewerEvent.ToggleThumbnails) },
                    onResetClick = { showResetDialog = true },
                    onThemeLongClick = { viewModel.onEvent(ViewerEvent.ToggleTheme) },
                    faceEnabled = state.faceEnabled,
                    onFaceClick = { viewModel.onEvent(ViewerEvent.ToggleFace) },
                    onFaceLongClick = { viewModel.onEvent(ViewerEvent.ShowFaceOverlay) }
                )
            }

            AnimatedVisibility(
                visible = state.showUI && isViewing,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = if (isViewing) 8.dp else 16.dp, vertical = 8.dp)
            ) {
                BottomFooter(
                    isDark = isDark,
                    statusMessage = state.statusMessage
                )
            }

            if (state.showThumbnails && state.pageCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { viewModel.onEvent(ViewerEvent.ToggleThumbnails) }
                )
            }

            AnimatedVisibility(
                visible = state.showThumbnails && state.pageCount > 0,
                enter = slideInHorizontally { it / 3 } + fadeIn(),
                exit = slideOutHorizontally { it / 3 } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                ThumbnailPanel(
                    isDark = isDark,
                    pageCount = state.pageCount,
                    currentPage = state.currentPage,
                    getThumbnailUri = { viewModel.getThumbnailUri(it) },
                    onPageSelected = { viewModel.onEvent(ViewerEvent.GoToPage(it)) },
                    onClose = { viewModel.onEvent(ViewerEvent.ToggleThumbnails) }
                )
            }

            if (state.showShelf) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { viewModel.onEvent(ViewerEvent.ToggleShelf) }
                )
            }

            AnimatedVisibility(
                visible = state.showShelf,
                enter = slideInHorizontally { -it / 3 } + fadeIn(),
                exit = slideOutHorizontally { -it / 3 } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                ShelfPanel(
                    isDark = isDark,
                    shelfFiles = state.shelfFiles,
                    shelfSortBy = state.shelfSortBy,
                    onFileSelected = { uri -> viewModel.onEvent(ViewerEvent.OpenShelfFile(uri)) },
                    onImportClick = openFilePicker,
                    onClose = { viewModel.onEvent(ViewerEvent.ToggleShelf) },
                    onRename = { uri, name -> viewModel.onEvent(ViewerEvent.RenameShelfFile(uri, name)) },
                    onToggleSort = { viewModel.onEvent(ViewerEvent.ToggleShelfSort) }
                )
            }
        }

        if (showPageDialog) {
            AlertDialog(
                onDismissRequest = { showPageDialog = false },
                title = { Text("跳转页码") },
                text = {
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = {
                            if (it.all { c -> c.isDigit() } && it.length <= 4) pageInput = it
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("1 - ${state.pageCount}") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val page = pageInput.toIntOrNull()
                        if (page != null && page in 1..state.pageCount) {
                            viewModel.onEvent(ViewerEvent.GoToPage(page - 1))
                        }
                        showPageDialog = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showPageDialog = false }) { Text("取消") }
                }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("操作") },
                text = { Text("选择要执行的操作") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onEvent(ViewerEvent.Reload)
                        showResetDialog = false
                    }) { Text("重新加载") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.onEvent(ViewerEvent.Reset)
                        showResetDialog = false
                    }) { Text("重置关闭") }
                }
            )
        }

        FaceRecognitionOverlay(
            visible = state.showFaceOverlay,
            onDismiss = { viewModel.onEvent(ViewerEvent.HideFaceOverlay) },
            onToggle = { viewModel.onEvent(ViewerEvent.ToggleFace) },
            manager = viewModel.faceManager,
            isDark = isDark
        )
    }
}
