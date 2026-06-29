package com.music.msv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun Stage(
    isDark: Boolean,
    contentUri: Any?,
    prevUri: Any?,
    zoom: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    showUI: Boolean,
    onEdgeLeftTap: () -> Unit,
    onEdgeRightTap: () -> Unit,
    onCenterTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float, Float) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isDark) Color(0x2E000000) else Color(0x8CFFFFFF)
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var currentZoom by remember { mutableFloatStateOf(zoom) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .onSizeChanged { stageSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    currentZoom = (currentZoom * zoomChange).coerceIn(0.5f, 8f)
                    onZoomChange(currentZoom)
                    onPanChange(pan.x, pan.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val thirdWidth = stageSize.width / 3f
                        when {
                            offset.x < thirdWidth -> onEdgeLeftTap()
                            offset.x > stageSize.width - thirdWidth -> onEdgeRightTap()
                            else -> onCenterTap()
                        }
                    },
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        if (contentUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(contentUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "page",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
