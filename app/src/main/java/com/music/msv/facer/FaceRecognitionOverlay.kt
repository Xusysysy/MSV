package com.music.msv.facer

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@Composable
fun FaceRecognitionOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    manager: FaceRecognitionManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) Toast.makeText(context, "需要相机权限才能使用面部识别", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(visible) {
        if (visible && !hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
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
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F1220))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { }
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("面部识别设置", color = Color(0xFFF5F7FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                if (hasPermission) {
                    CameraPreviewCard(manager, lifecycleOwner)
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷", fontSize = 28.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("需要相机权限", color = Color(0xFFB0B8C8), fontSize = 13.sp)
                            Text(
                                "点击授予",
                                color = Color(0xFF8CC8FF),
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { permLauncher.launch(Manifest.permission.CAMERA) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ModeBtn("仅 Wink", manager.getState().triggerMode == FaceRecognitionManager.TriggerMode.WINK) {
                        manager.updateState(manager.getState().copy(triggerMode = FaceRecognitionManager.TriggerMode.WINK))
                    }
                    ModeBtn("仅撅嘴", manager.getState().triggerMode == FaceRecognitionManager.TriggerMode.PUCKER) {
                        manager.updateState(manager.getState().copy(triggerMode = FaceRecognitionManager.TriggerMode.PUCKER))
                    }
                    ModeBtn("两者", manager.getState().triggerMode == FaceRecognitionManager.TriggerMode.BOTH) {
                        manager.updateState(manager.getState().copy(triggerMode = FaceRecognitionManager.TriggerMode.BOTH))
                    }
                }

                Spacer(Modifier.height(12.dp))

                SliderRow("眨眼阈值", manager.getState().thresholds.blink, 0.10f..0.95f) {
                    manager.updateState(manager.getState().copy(thresholds = manager.getState().thresholds.copy(blink = it)))
                }
                SliderRow("Wink 差值", manager.getState().thresholds.winkDiff, 0.05f..0.80f) {
                    manager.updateState(manager.getState().copy(thresholds = manager.getState().thresholds.copy(winkDiff = it)))
                }
                SliderRow("撅嘴阈值", manager.getState().thresholds.pucker, 0.05f..0.90f) {
                    manager.updateState(manager.getState().copy(thresholds = manager.getState().thresholds.copy(pucker = it)))
                }
                SliderRow("撅嘴偏移", manager.getState().thresholds.puckerBias, 0.02f..0.60f) {
                    manager.updateState(manager.getState().copy(thresholds = manager.getState().thresholds.copy(puckerBias = it)))
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatusChip("状态", if (manager.getState().isRunning) "运行中" else "未启动", manager.getState().isRunning)
                    StatusChip(
                        "模式", when (manager.getState().triggerMode) {
                            FaceRecognitionManager.TriggerMode.WINK -> "仅Wink"
                            FaceRecognitionManager.TriggerMode.PUCKER -> "仅撅嘴"
                            FaceRecognitionManager.TriggerMode.BOTH -> "两者皆可"
                        }, true
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("点击外部保存并关闭", color = Color(0xFF8CC8FF), fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CameraPreviewCard(manager: FaceRecognitionManager, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1E2E))
            .padding(8.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                        val analyzer = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analyzer.setAnalyzer(analysisExecutor) { imageProxy: ImageProxy ->
                            if (manager.getState().isRunning && manager.getState().isEnabled) {
                                manager.processImageProxy(imageProxy)
                            } else {
                                imageProxy.close()
                            }
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                analyzer
                            )
                        } catch (_: Exception) { }
                    }, ctx.mainExecutor)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (manager.getState().isRunning) "● LIVE" else "○ OFF",
                color = if (manager.getState().isRunning) Color(0xFF4ADE80) else Color(0xFF6B7280),
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Text(
                text = if (manager.getState().isEnabled) "识别: 开" else "识别: 关",
                color = if (manager.getState().isEnabled) Color(0xFF8CC8FF) else Color(0xFF6B7280),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ModeBtn(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF8CC8FF) else Color(0xFF1A1E2E))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) Color.Black else Color(0xFFB0B8C8), fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFB0B8C8), fontSize = 12.sp)
            Text(String.format("%.2f", value), color = Color(0xFF8CC8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        androidx.compose.material3.Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color(0xFF8CC8FF), activeTrackColor = Color(0xFF8CC8FF), inactiveTrackColor = Color(0xFF1A1E2E)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusChip(label: String, value: String, active: Boolean) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1E2E))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color(0xFF6B7280), fontSize = 10.sp)
        Text(value, color = if (active) Color(0xFF4ADE80) else Color(0xFFB0B8C8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
