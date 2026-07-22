package com.music.msv.facer

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

@Composable
fun FaceRecognitionOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    manager: FaceRecognitionManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            Toast.makeText(context, "相机权限已授予", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(visible) {
        if (visible && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission, visible) {
        if (hasCameraPermission && visible) {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).get()
            } catch (_: Exception) { }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    manager.updateState(manager.getState().copy(isEnabled = true))
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F1220))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { }
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "面部识别设置",
                    color = Color(0xFFF5F7FF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (hasCameraPermission) {
                    CameraPreview(
                        cameraProvider = cameraProvider,
                        lifecycleOwner = lifecycleOwner,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1A1E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "📷",
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "需要相机权限",
                                color = Color(0xFFB0B8C8),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "点击授予权限",
                                color = Color(0xFF8CC8FF),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TriggerModeSelector(
                    currentMode = manager.getState().triggerMode,
                    onModeSelected = { mode ->
                        manager.updateState(manager.getState().copy(triggerMode = mode))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ThresholdSlider(
                    label = "眨眼阈值",
                    value = manager.getState().thresholds.blink,
                    onValueChange = { value ->
                        val t = manager.getState().thresholds
                        manager.updateState(manager.getState().copy(thresholds = t.copy(blink = value)))
                    },
                    valueRange = 0.10f..0.95f
                )

                ThresholdSlider(
                    label = "Wink 差值",
                    value = manager.getState().thresholds.winkDiff,
                    onValueChange = { value ->
                        val t = manager.getState().thresholds
                        manager.updateState(manager.getState().copy(thresholds = t.copy(winkDiff = value)))
                    },
                    valueRange = 0.05f..0.80f
                )

                ThresholdSlider(
                    label = "撅嘴阈值",
                    value = manager.getState().thresholds.pucker,
                    onValueChange = { value ->
                        val t = manager.getState().thresholds
                        manager.updateState(manager.getState().copy(thresholds = t.copy(pucker = value)))
                    },
                    valueRange = 0.05f..0.90f
                )

                ThresholdSlider(
                    label = "撅嘴偏移",
                    value = manager.getState().thresholds.puckerBias,
                    onValueChange = { value ->
                        val t = manager.getState().thresholds
                        manager.updateState(manager.getState().copy(thresholds = t.copy(puckerBias = value)))
                    },
                    valueRange = 0.02f..0.60f
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatusChip(
                        label = "状态",
                        value = if (manager.getState().isRunning) "运行中" else "未启动",
                        isActive = manager.getState().isRunning
                    )
                    StatusChip(
                        label = "模式",
                        value = when (manager.getState().triggerMode) {
                            FaceRecognitionManager.TriggerMode.WINK -> "仅 Wink"
                            FaceRecognitionManager.TriggerMode.PUCKER -> "仅撅嘴"
                            FaceRecognitionManager.TriggerMode.BOTH -> "两者皆可"
                        },
                        isActive = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "点击外部区域保存并关闭",
                    color = Color(0xFF8CC8FF),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    cameraProvider: ProcessCameraProvider?,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                cameraProvider?.let { provider ->
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(surfaceProvider)
                    }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview
                        )
                    } catch (_: Exception) { }
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun TriggerModeSelector(
    currentMode: FaceRecognitionManager.TriggerMode,
    onModeSelected: (FaceRecognitionManager.TriggerMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth()
    ) {
        ModeButton(
            text = "仅 Wink",
            isSelected = currentMode == FaceRecognitionManager.TriggerMode.WINK,
            onClick = { onModeSelected(FaceRecognitionManager.TriggerMode.WINK) }
        )
        ModeButton(
            text = "仅撅嘴",
            isSelected = currentMode == FaceRecognitionManager.TriggerMode.PUCKER,
            onClick = { onModeSelected(FaceRecognitionManager.TriggerMode.PUCKER) }
        )
        ModeButton(
            text = "两者皆可",
            isSelected = currentMode == FaceRecognitionManager.TriggerMode.BOTH,
            onClick = { onModeSelected(FaceRecognitionManager.TriggerMode.BOTH) }
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF8CC8FF) else Color(0xFF1A1E2E))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color(0xFFB0B8C8),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color(0xFFB0B8C8),
                fontSize = 13.sp
            )
            Text(
                text = String.format("%.2f", value),
                color = Color(0xFF8CC8FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color(0xFF8CC8FF),
                activeTrackColor = Color(0xFF8CC8FF),
                inactiveTrackColor = Color(0xFF1A1E2E)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    value: String,
    isActive: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1E2E))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF6B7280),
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = if (isActive) Color(0xFF4ADE80) else Color(0xFFB0B8C8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
