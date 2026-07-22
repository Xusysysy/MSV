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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.Executors

private val FACE_CONNS = intArrayOf(
    10,338, 338,297, 297,332, 332,284, 284,251, 251,389, 389,356, 356,454,
    454,323, 323,361, 361,288, 288,397, 397,365, 365,379, 379,378, 378,400,
    400,377, 377,152, 152,148, 148,176, 176,149, 149,150, 150,136, 136,172,
    172,58, 58,132, 132,93, 93,234, 234,127, 127,162, 162,21, 21,54, 54,103,
    103,67, 67,109, 109,10
)
private val EYE_CONNS = intArrayOf(
    33,7, 7,163, 163,144, 144,145, 145,153, 153,154, 154,155, 155,133,
    133,173, 173,157, 157,158, 158,159, 159,160, 160,161, 161,246, 246,33,
    362,382, 382,381, 381,380, 380,374, 374,373, 373,390, 390,249, 249,263,
    263,466, 466,388, 388,387, 387,386, 386,385, 385,384, 384,398, 398,362
)
private val LIP_CONNS = intArrayOf(
    61,146, 146,91, 91,181, 181,84, 84,17, 17,314, 314,405, 405,321,
    321,375, 375,291, 291,409, 409,270, 270,269, 269,267, 267,0, 0,37,
    37,39, 39,40, 40,185, 185,61
)
private val BROW_CONNS = intArrayOf(
    276,283, 283,282, 282,295, 295,285, 285,300, 300,293, 293,334, 334,296,
    296,336, 46,53, 53,52, 52,65, 65,55, 55,70, 70,63, 63,105, 105,66, 66,107
)

@Composable
fun FaceRecognitionOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    manager: FaceRecognitionManager,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }
    var faceResult by remember { mutableStateOf<FaceLandmarkerResult?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(visible) {
        if (visible) {
            hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val panelBg = if (isDark) Color(0xF00F121C) else Color(0xF2FFFFFF)
    val cardBg = if (isDark) Color(0xFF141824) else Color(0x0F1A2230)
    val bdr = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val text = if (isDark) Color(0xFFF5F7FF) else Color(0xFF1B2230)
    val muted = if (isDark) Color(0xB8F5F7FF) else Color(0xD11B2230)
    val accent = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)
    val danger = if (isDark) Color(0xFFFF9AA8) else Color(0xFFD9455D)
    val green = Color(0xFF4ADE80)

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    manager.updateState(manager.getState().copy(isEnabled = true))
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(panelBg)
                    .border(1.dp, bdr, RoundedCornerShape(16.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("面部识别", color = muted, fontSize = 11.sp, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FPS ${manager.getDebugInfo().fps}", color = muted, fontSize = 9.sp)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (manager.getState().isRunning) green.copy(alpha = 0.15f) else cardBg).border(1.dp, if (manager.getState().isRunning) green.copy(alpha = 0.4f) else bdr, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(if (manager.getState().isRunning) "LIVE" else "OFF", color = if (manager.getState().isRunning) green else muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.size(22.dp).clip(CircleShape).background(cardBg).border(1.dp, bdr, CircleShape).clickable { manager.updateState(manager.getState().copy(isEnabled = true)); onDismiss() }, contentAlignment = Alignment.Center) {
                            Text("✕", color = muted, fontSize = 10.sp)
                        }
                    }
                }

                // Top row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1.2f).aspectRatio(0.8f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0A0D18)).border(1.dp, bdr, RoundedCornerShape(10.dp))) {
                        if (hasPermission) {
                            CameraPreviewCard(manager, lifecycleOwner, faceResult, { faceResult = it }, Modifier.fillMaxSize())
                        } else {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("📷", fontSize = 20.sp)
                                Spacer(Modifier.height(2.dp))
                                Text("需要权限", color = muted, fontSize = 9.sp)
                                Text("点击授予", color = accent, fontSize = 8.sp, modifier = Modifier.clickable { permLauncher.launch(Manifest.permission.CAMERA) })
                            }
                        }
                    }
                    Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val scores = manager.getScores()
                        ActionRow("😜", "左Wink", scores.leftWink, accent, cardBg, bdr, muted)
                        ActionRow("😉", "右Wink", scores.rightWink, accent, cardBg, bdr, muted)
                        ActionRow("😗", "左撅嘴", scores.leftPucker, danger, cardBg, bdr, muted)
                        ActionRow("😙", "右撅嘴", scores.rightPucker, danger, cardBg, bdr, muted)
                    }
                }

                // Bottom row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("触发模式", color = muted, fontSize = 9.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            ModeBtn("关", !manager.getState().isEnabled, cardBg, bdr, muted) { manager.updateState(manager.getState().copy(isEnabled = false, isRunning = false)) }
                            ModeBtn("Wink", manager.getState().triggerMode == FaceRecognitionManager.TriggerMode.WINK && manager.getState().isEnabled, cardBg, bdr, accent) { manager.updateState(manager.getState().copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.WINK)) }
                            ModeBtn("撅嘴", manager.getState().triggerMode == FaceRecognitionManager.TriggerMode.PUCKER && manager.getState().isEnabled, cardBg, bdr, accent) { manager.updateState(manager.getState().copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.PUCKER)) }
                            ModeBtn("两者", manager.getState().triggerMode == FaceRecognitionManager.TriggerMode.BOTH && manager.getState().isEnabled, cardBg, bdr, accent) { manager.updateState(manager.getState().copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.BOTH)) }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("状态", color = muted, fontSize = 9.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            InfoTag("模型", manager.isInitialized(), cardBg, bdr, green, muted)
                            InfoTag("相机", hasPermission, cardBg, bdr, green, muted)
                            InfoTag("翻页", manager.getState().isEnabled, cardBg, bdr, accent, muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewCard(manager: FaceRecognitionManager, lifecycleOwner: androidx.lifecycle.LifecycleOwner, faceResult: FaceLandmarkerResult?, onResult: (FaceLandmarkerResult) -> Unit, modifier: Modifier = Modifier) {
    val exec = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { exec.shutdown() } }

    Box(modifier) {
        AndroidView(factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                val pf = ProcessCameraProvider.getInstance(ctx)
                pf.addListener({
                    val provider = pf.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                    val analyzer = ImageAnalysis.Builder().setTargetResolution(android.util.Size(320, 240)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analyzer.setAnalyzer(exec) { ip: ImageProxy ->
                        if (manager.getState().isRunning && manager.getState().isEnabled) manager.processImageProxy(ip) else ip.close()
                    }
                    try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analyzer) } catch (_: Exception) {}
                }, ctx.mainExecutor)
            }
        }, Modifier.fillMaxSize())

        if (manager.getState().showMesh && faceResult != null) {
            val lm = faceResult.faceLandmarks().getOrNull(0)
            if (lm != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    fun drawConns(conns: IntArray, color: Color, sw: Float) {
                        var i = 0
                        while (i < conns.size - 1) {
                            val p1 = lm.getOrNull(conns[i]); val p2 = lm.getOrNull(conns[i + 1])
                            if (p1 != null && p2 != null) {
                                drawLine(color, Offset((1f - p1.x()) * w, p1.y() * h), Offset((1f - p2.x()) * w, p2.y() * h), strokeWidth = sw)
                            }
                            i += 2
                        }
                    }
                    drawConns(FACE_CONNS, Color(0x47B08CFF), 1.2f)
                    drawConns(EYE_CONNS, Color(0x7300D4FF), 1.2f)
                    drawConns(BROW_CONNS, Color(0x4200D4FF), 1.0f)
                    drawConns(LIP_CONNS, Color(0x80FF3D8F), 1.3f)
                }
            }
        }

        LaunchedEffect(faceResult) { if (faceResult != null) onResult(faceResult!!) }
    }
}

@Composable
private fun ActionRow(emoji: String, label: String, score: Float, activeColor: Color, cardBg: Color, bdr: Color, muted: Color) {
    val on = score > 0.01f
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) activeColor.copy(alpha = 0.12f) else cardBg).border(1.dp, if (on) activeColor.copy(alpha = 0.35f) else bdr, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(emoji, fontSize = 14.sp, modifier = Modifier.size(18.dp), textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            Text(label, color = if (on) activeColor else muted, fontSize = 9.sp, maxLines = 1)
            Text("${(score * 100).toInt()}%", color = if (on) activeColor else muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(bdr)) {
            Box(Modifier.fillMaxWidth().height((20.dp * score.coerceIn(0f, 1f))).align(Alignment.BottomCenter).clip(RoundedCornerShape(2.dp)).background(activeColor))
        }
    }
}

@Composable
private fun ModeBtn(text: String, sel: Boolean, cardBg: Color, bdr: Color, accent: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (sel) accent.copy(alpha = 0.15f) else cardBg).border(1.dp, if (sel) accent.copy(alpha = 0.4f) else bdr, RoundedCornerShape(6.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (sel) accent else Color(0xB8F5F7FF), fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun InfoTag(label: String, ok: Boolean, cardBg: Color, bdr: Color, activeColor: Color, muted: Color) {
    Column(Modifier.clip(RoundedCornerShape(6.dp)).background(cardBg).border(1.dp, bdr, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = muted, fontSize = 8.sp)
        Text(if (ok) "✓" else "○", color = if (ok) activeColor else muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
