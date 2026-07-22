package com.music.msv.facer

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

private const val TAG = "FaceUI"

private val FACE_CONNS = intArrayOf(10,338,338,297,297,332,332,284,284,251,251,389,389,356,356,454,454,323,323,361,361,288,288,397,397,365,365,379,379,378,378,400,400,377,377,152,152,148,148,176,176,149,149,150,150,136,136,172,172,58,58,132,132,93,93,234,234,127,127,162,162,21,21,54,54,103,103,67,67,109,109,10)
private val EYE_CONNS = intArrayOf(33,7,7,163,163,144,144,145,145,153,153,154,154,155,155,133,133,173,173,157,157,158,158,159,159,160,160,161,161,246,246,33,362,382,382,381,381,380,380,374,374,373,373,390,390,249,249,263,263,466,466,388,388,387,387,386,386,385,385,384,384,398,398,362)
private val LIP_CONNS = intArrayOf(61,146,146,91,91,181,181,84,84,17,17,314,314,405,405,321,321,375,375,291,291,409,409,270,270,269,269,267,267,0,0,37,37,39,39,40,40,185,185,61)
private val BROW_CONNS = intArrayOf(276,283,283,282,282,295,295,285,285,300,300,293,293,334,334,296,296,336,46,53,53,52,52,65,65,55,55,70,70,63,63,105,105,66,66,107)

@Composable
fun FaceRecognitionOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    manager: FaceRecognitionManager,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(visible) {
        if (visible && !hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
        if (visible) {
            val state = manager.getState()
            if (!state.isRunning && state.isEnabled) {
                if (!manager.isInitialized()) manager.initialize()
                manager.updateState(state.copy(isRunning = true))
            }
        }
    }

    val debugInfo by manager.debugInfoFlow.collectAsState()
    val scores = debugInfo.scores

    val panelBg = if (isDark) Color(0xF0121628) else Color(0xF8FFFFFF)
    val cardBg = if (isDark) Color(0xFF1A1E2E) else Color(0x141A2230)
    val bdr = if (isDark) Color(0x28FFFFFF) else Color(0x201A2230)
    val textOn = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xCCFFFFFF) else Color(0xCC000000)
    val accent = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)
    val danger = if (isDark) Color(0xFFFF9AA8) else Color(0xFFD9455D)
    val green = Color(0xFF4ADE80)
    val running = manager.getState().isRunning

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(16.dp)).background(panelBg).border(1.dp, bdr, RoundedCornerShape(16.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("面部识别", color = textOn, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (running) green.copy(alpha = 0.2f) else cardBg).border(1.dp, if (running) green else bdr, RoundedCornerShape(8.dp)).clickable {
                        val s = manager.getState()
                        if (s.isRunning) { manager.updateState(s.copy(isRunning = false, isEnabled = false)) }
                        else { if (!manager.isInitialized()) manager.initialize(); manager.updateState(s.copy(isRunning = true, isEnabled = true)) }
                    }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(if (running) "● LIVE ${debugInfo.fps}fps" else "○ OFF", color = if (running) green else textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Preview
                Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)).border(1.dp, bdr, RoundedCornerShape(12.dp))) {
                    if (hasPermission) {
                        CameraPreview(manager, lifecycleOwner, debugInfo, Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFF0A0D18)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📷", fontSize = 32.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("需要相机权限", color = textOn, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("点击授予", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { permLauncher.launch(Manifest.permission.CAMERA) })
                            }
                        }
                    }
                }

                // Action cards
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionCard(Modifier.weight(1f), "😜", "左Wink", scores.leftWink, accent, cardBg, bdr, textOn, textMuted)
                    ActionCard(Modifier.weight(1f), "😉", "右Wink", scores.rightWink, accent, cardBg, bdr, textOn, textMuted)
                    ActionCard(Modifier.weight(1f), "😗", "左撅嘴", scores.leftPucker, danger, cardBg, bdr, textOn, textMuted)
                    ActionCard(Modifier.weight(1f), "😙", "右撅嘴", scores.rightPucker, danger, cardBg, bdr, textOn, textMuted)
                }

                // Mode selector
                val state = manager.getState()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModeBtn(Modifier.weight(1f), "关闭", !state.isEnabled, cardBg, bdr, textMuted) { manager.updateState(state.copy(isEnabled = false, isRunning = false)) }
                    ModeBtn(Modifier.weight(1f), "Wink", state.isEnabled && state.triggerMode == FaceRecognitionManager.TriggerMode.WINK, cardBg, bdr, accent) {
                        if (!manager.isInitialized()) manager.initialize()
                        manager.updateState(state.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.WINK))
                    }
                    ModeBtn(Modifier.weight(1f), "撅嘴", state.isEnabled && state.triggerMode == FaceRecognitionManager.TriggerMode.PUCKER, cardBg, bdr, accent) {
                        if (!manager.isInitialized()) manager.initialize()
                        manager.updateState(state.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.PUCKER))
                    }
                    ModeBtn(Modifier.weight(1f), "两者", state.isEnabled && state.triggerMode == FaceRecognitionManager.TriggerMode.BOTH, cardBg, bdr, accent) {
                        if (!manager.isInitialized()) manager.initialize()
                        manager.updateState(state.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.BOTH))
                    }
                }

                // Threshold sliders
                ThresholdSlider("眨眼阈值", state.thresholds.blink, 0.10f..0.95f, textOn, textMuted, accent, bdr) {
                    manager.updateState(state.copy(thresholds = state.thresholds.copy(blink = it)))
                }
                ThresholdSlider("撅嘴阈值", state.thresholds.pucker, 0.05f..0.90f, textOn, textMuted, accent, bdr) {
                    manager.updateState(state.copy(thresholds = state.thresholds.copy(pucker = it)))
                }

                // Debug info
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0D18)).border(1.dp, bdr, RoundedCornerShape(8.dp)).padding(6.dp)) {
                    Text("诊断", color = Color(0xFF888888), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("模型: ${if (manager.isInitialized()) "✓ 就绪" else "✗ 未加载"}", color = if (manager.isInitialized()) green else danger, fontSize = 10.sp)
                    Text("相机: ${if (hasPermission) "✓ 已授权" else "✗ 未授权"}", color = if (hasPermission) green else danger, fontSize = 10.sp)
                    Text("运行: ${if (running) "✓ 识别中" else "○ 已停止"}", color = if (running) green else textMuted, fontSize = 10.sp)
                    Text("FPS: ${debugInfo.fps}  面部: ${debugInfo.landmarks?.size ?: 0}点", color = Color(0xFF8CC8FF), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(manager: FaceRecognitionManager, lifecycleOwner: androidx.lifecycle.LifecycleOwner, debugInfo: FaceRecognitionManager.FaceDebugInfo, modifier: Modifier = Modifier) {
    val exec = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { exec.shutdown() } }

    Box(modifier) {
        AndroidView(factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val pf = ProcessCameraProvider.getInstance(ctx)
                pf.addListener({
                    val provider = pf.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                    val analyzer = ImageAnalysis.Builder().setTargetResolution(android.util.Size(320, 240)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analyzer.setAnalyzer(exec) { ip: ImageProxy ->
                        try { manager.processImageProxy(ip) } catch (e: Exception) { Log.e(TAG, "Error processing frame: ${e.message}"); ip.close() }
                    }
                    try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analyzer) } catch (e: Exception) { Log.e(TAG, "Camera bind error: ${e.message}") }
                }, ctx.mainExecutor)
            }
        }, Modifier.fillMaxSize())

        // Face mesh overlay
        if (manager.getState().showMesh && debugInfo.landmarks != null) {
            val lm = debugInfo.landmarks!!
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                fun drawConns(conns: IntArray, color: Color, sw: Float) {
                    var i = 0
                    while (i < conns.size - 1) {
                        val a = conns[i]; val b = conns[i + 1]
                        if (a < lm.size && b < lm.size) {
                            val p1 = lm[a]; val p2 = lm[b]
                            drawLine(color, Offset((1f - p1.x()) * w, p1.y() * h), Offset((1f - p2.x()) * w, p2.y() * h), strokeWidth = sw)
                        }
                        i += 2
                    }
                }
                drawConns(FACE_CONNS, Color(0x60B08CFF), 1.5f)
                drawConns(EYE_CONNS, Color(0x9000D4FF), 1.5f)
                drawConns(BROW_CONNS, Color(0x6000D4FF), 1.2f)
                drawConns(LIP_CONNS, Color(0xA0FF3D8F), 1.6f)
            }
        }
    }
}

@Composable
private fun ActionCard(mod: Modifier, emoji: String, label: String, score: Float, activeColor: Color, cardBg: Color, bdr: Color, textOn: Color, textMuted: Color) {
    val on = score > 0.01f
    Column(mod.clip(RoundedCornerShape(10.dp)).background(if (on) activeColor.copy(alpha = 0.15f) else cardBg).border(1.dp, if (on) activeColor.copy(alpha = 0.5f) else bdr, RoundedCornerShape(10.dp)).padding(vertical = 6.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.height(1.dp))
        Text(label, color = textOn, fontSize = 9.sp, maxLines = 1)
        Text("${(score * 100).toInt()}%", color = if (on) activeColor else textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeBtn(mod: Modifier, text: String, sel: Boolean, cardBg: Color, bdr: Color, activeColor: Color, onClick: () -> Unit) {
    Box(mod.clip(RoundedCornerShape(8.dp)).background(if (sel) activeColor.copy(alpha = 0.2f) else cardBg).border(1.dp, if (sel) activeColor else bdr, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (sel) activeColor else Color(0xFF888888), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ThresholdSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, textOn: Color, textMuted: Color, accent: Color, bdr: Color, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, color = textOn, fontSize = 11.sp)
            Text(String.format("%.2f", value), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = bdr))
    }
}
