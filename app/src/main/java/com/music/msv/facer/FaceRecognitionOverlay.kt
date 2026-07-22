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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.concurrent.Executors

private const val TAG = "FaceRec"

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
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var debugLog by remember { mutableStateOf("等待初始化...") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        debugLog = if (granted) "✓ 相机权限已授予" else "✗ 相机权限被拒绝"
        if (!granted) Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(visible) {
        if (visible && !hasPermission) {
            debugLog = "请求相机权限..."
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            debugLog = "初始化模型..."
            if (!manager.isInitialized()) {
                val ok = manager.initialize()
                debugLog = if (ok) "✓ 模型加载成功" else "✗ 模型加载失败"
                Log.e(TAG, "Model init: $ok")
            } else {
                debugLog = "✓ 模型已就绪"
            }
        }
    }

    val debugInfo by manager.debugInfoFlow.collectAsState()
    val scores = debugInfo.scores

    val panelBg = if (isDark) Color(0xF00F121C) else Color(0xF8FFFFFF)
    val cardBg = if (isDark) Color(0xFF1A1E2E) else Color(0x141A2230)
    val bdr = if (isDark) Color(0x28FFFFFF) else Color(0x201A2230)
    val textOn = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xCCFFFFFF) else Color(0xCC000000)
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
                    .fillMaxWidth(if (isLandscape) 0.75f else 0.92f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(panelBg)
                    .border(1.dp, bdr, RoundedCornerShape(16.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("面部识别", color = textOn, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val running = manager.getState().isRunning
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (running) green.copy(alpha = 0.2f) else cardBg).border(1.dp, if (running) green else bdr, RoundedCornerShape(8.dp)).clickable {
                            if (running) {
                                manager.updateState(manager.getState().copy(isRunning = false, isEnabled = false))
                                debugLog = "已停止识别"
                            } else {
                                if (!manager.isInitialized()) {
                                    debugLog = "模型未加载，正在初始化..."
                                    val ok = manager.initialize()
                                    if (!ok) { debugLog = "✗ 模型加载失败"; return@clickable }
                                }
                                manager.updateState(manager.getState().copy(isRunning = true, isEnabled = true))
                                debugLog = "✓ 识别已启动"
                            }
                        }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(if (running) "LIVE ${debugInfo.fps}fps" else "OFF", color = if (running) green else textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(cardBg).border(1.dp, bdr, RoundedCornerShape(8.dp)).clickable { manager.updateState(manager.getState().copy(isEnabled = true)); onDismiss() }, contentAlignment = Alignment.Center) {
                            Text("✕", color = textMuted, fontSize = 14.sp)
                        }
                    }
                }

                // Preview + Action cards
                if (isLandscape) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0D18)).border(1.dp, bdr, RoundedCornerShape(12.dp))) {
                            if (hasPermission) CamPreviewMesh(manager, lifecycleOwner, Modifier.fillMaxSize()) else PermPlaceholder(permLauncher, textMuted, accent)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ActionCardsRow(scores, accent, danger, cardBg, bdr, textOn, textMuted)
                            ThresholdSection(manager, textOn, textMuted, accent, cardBg, bdr)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0D18)).border(1.dp, bdr, RoundedCornerShape(12.dp))) {
                        if (hasPermission) CamPreviewMesh(manager, lifecycleOwner, Modifier.fillMaxSize()) else PermPlaceholder(permLauncher, textMuted, accent)
                    }
                    ActionCardsRow(scores, accent, danger, cardBg, bdr, textOn, textMuted)
                    ThresholdSection(manager, textOn, textMuted, accent, cardBg, bdr)
                }

                // Mode selector
                ModeSelector(manager, textOn, textMuted, accent, cardBg, bdr, green)

                // Debug log
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0D18)).border(1.dp, bdr, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text("诊断", color = Color(0xFF888888), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(debugLog, color = Color(0xFF4ADE80), fontSize = 10.sp)
                    val lm = debugInfo.landmarks
                    if (lm != null) {
                        Text("关键点: ${lm.size}个", color = Color(0xFF8CC8FF), fontSize = 10.sp)
                    } else {
                        Text("关键点: 未检测到面部", color = Color(0xFFFF9AA8), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CamPreviewMesh(manager: FaceRecognitionManager, lifecycleOwner: androidx.lifecycle.LifecycleOwner, modifier: Modifier = Modifier) {
    val exec = remember { Executors.newSingleThreadExecutor() }
    val debugInfo by manager.debugInfoFlow.collectAsState()
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
                        try {
                            if (manager.getState().isRunning && manager.getState().isEnabled) {
                                manager.processImageProxy(ip)
                            } else {
                                ip.close()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Analyzer error", e)
                            ip.close()
                        }
                    }
                    try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analyzer) } catch (e: Exception) { Log.e(TAG, "Camera bind failed", e) }
                }, ctx.mainExecutor)
            }
        }, Modifier.fillMaxSize())

        if (manager.getState().showMesh && debugInfo.landmarks != null) {
            val lm = debugInfo.landmarks
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                fun drawConns(conns: IntArray, color: Color, sw: Float) {
                    if (lm == null) return
                    var i = 0
                    while (i < conns.size - 1) {
                        val idx1 = conns[i]; val idx2 = conns[i + 1]
                        if (idx1 < lm!!.size && idx2 < lm!!.size) {
                            val p1 = lm!![idx1]; val p2 = lm!![idx2]
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
private fun PermPlaceholder(launcher: androidx.activity.result.ActivityResultLauncher<String>, muted: Color, accent: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷", fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text("需要相机权限", color = muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text("点击授予权限", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { launcher.launch(Manifest.permission.CAMERA) })
        }
    }
}

@Composable
private fun ActionCardsRow(scores: FaceRecognitionManager.GestureScores, accent: Color, danger: Color, cardBg: Color, bdr: Color, textOn: Color, textMuted: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ActionCard(Modifier.weight(1f), "😜", "左Wink", scores.leftWink, accent, cardBg, bdr, textOn, textMuted)
        ActionCard(Modifier.weight(1f), "😉", "右Wink", scores.rightWink, accent, cardBg, bdr, textOn, textMuted)
        ActionCard(Modifier.weight(1f), "😗", "左撅嘴", scores.leftPucker, danger, cardBg, bdr, textOn, textMuted)
        ActionCard(Modifier.weight(1f), "😙", "右撅嘴", scores.rightPucker, danger, cardBg, bdr, textOn, textMuted)
    }
}

@Composable
private fun ActionCard(mod: Modifier, emoji: String, label: String, score: Float, activeColor: Color, cardBg: Color, bdr: Color, textOn: Color, textMuted: Color) {
    val on = score > 0.01f
    Column(mod.clip(RoundedCornerShape(10.dp)).background(if (on) activeColor.copy(alpha = 0.15f) else cardBg).border(1.dp, if (on) activeColor.copy(alpha = 0.5f) else bdr, RoundedCornerShape(10.dp)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = textOn, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text("${(score * 100).toInt()}%", color = if (on) activeColor else textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(bdr)) {
            Box(Modifier.height(4.dp).width(40.dp * score.coerceIn(0f, 1f)).clip(RoundedCornerShape(2.dp)).background(activeColor))
        }
    }
}

@Composable
private fun ThresholdSection(manager: FaceRecognitionManager, textOn: Color, textMuted: Color, accent: Color, cardBg: Color, bdr: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cardBg).border(1.dp, bdr, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("灵敏度调节", color = textOn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val state = manager.getState()
        ThresholdRow("眨眼阈值", state.thresholds.blink, 0.10f..0.95f, textOn, textMuted, accent, bdr) {
            manager.updateState(state.copy(thresholds = state.thresholds.copy(blink = it)))
        }
        ThresholdRow("撅嘴阈值", state.thresholds.pucker, 0.05f..0.90f, textOn, textMuted, accent, bdr) {
            manager.updateState(state.copy(thresholds = state.thresholds.copy(pucker = it)))
        }
    }
}

@Composable
private fun ThresholdRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, textOn: Color, textMuted: Color, accent: Color, bdr: Color, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = textOn, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = bdr))
        Text(String.format("%.2f", value), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun ModeSelector(manager: FaceRecognitionManager, textOn: Color, textMuted: Color, accent: Color, cardBg: Color, bdr: Color, green: Color) {
    val state = manager.getState()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cardBg).border(1.dp, bdr, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("触发模式", color = textOn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeBtn(Modifier.weight(1f), "关闭", !state.isEnabled, cardBg, bdr, textMuted) { manager.updateState(state.copy(isEnabled = false, isRunning = false)) }
            ModeBtn(Modifier.weight(1f), "Wink", state.triggerMode == FaceRecognitionManager.TriggerMode.WINK && state.isEnabled, cardBg, bdr, accent) {
                if (!manager.isInitialized()) manager.initialize()
                manager.updateState(state.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.WINK))
            }
            ModeBtn(Modifier.weight(1f), "撅嘴", state.triggerMode == FaceRecognitionManager.TriggerMode.PUCKER && state.isEnabled, cardBg, bdr, accent) {
                if (!manager.isInitialized()) manager.initialize()
                manager.updateState(state.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.PUCKER))
            }
            ModeBtn(Modifier.weight(1f), "两者", state.triggerMode == FaceRecognitionManager.TriggerMode.BOTH && state.isEnabled, cardBg, bdr, accent) {
                if (!manager.isInitialized()) manager.initialize()
                manager.updateState(state.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.BOTH))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot("模型", manager.isInitialized(), green, textMuted)
            StatusDot("相机", state.isRunning, green, textMuted)
            StatusDot("翻页", state.isEnabled, accent, textMuted)
        }
    }
}

@Composable
private fun ModeBtn(mod: Modifier, text: String, sel: Boolean, cardBg: Color, bdr: Color, activeColor: Color, onClick: () -> Unit) {
    Box(mod.clip(RoundedCornerShape(8.dp)).background(if (sel) activeColor.copy(alpha = 0.2f) else cardBg).border(1.dp, if (sel) activeColor else bdr, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (sel) activeColor else Color(0xFF888888), fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StatusDot(label: String, ok: Boolean, okColor: Color, muted: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (ok) okColor else Color(0xFF555555)))
        Text(label, color = if (ok) Color.White else muted, fontSize = 11.sp)
    }
}
