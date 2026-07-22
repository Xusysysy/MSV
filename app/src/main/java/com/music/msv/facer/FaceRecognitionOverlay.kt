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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

private val FACE_CONNS = intArrayOf(10,338,338,297,297,332,332,284,284,251,251,389,389,356,356,454,454,323,323,361,361,288,288,397,397,365,365,379,379,378,378,400,400,377,377,152,152,148,148,176,176,149,149,150,150,136,136,172,172,58,58,132,132,93,93,234,234,127,127,162,162,21,21,54,54,103,103,67,67,109,109,10)
private val EYE_CONNS = intArrayOf(33,7,7,163,163,144,144,145,145,153,153,154,154,155,155,133,133,173,173,157,157,158,158,159,159,160,160,161,161,246,246,33,362,382,382,381,381,380,380,374,374,373,373,390,390,249,249,263,263,466,466,388,388,387,387,386,386,385,385,384,384,398,398,362)
private val LIP_CONNS = intArrayOf(61,146,146,91,91,181,181,84,84,17,17,314,314,405,405,321,321,375,375,291,291,409,409,270,270,269,269,267,267,0,0,37,37,39,39,40,40,185,185,61)
private val BROW_CONNS = intArrayOf(276,283,283,282,282,295,295,285,285,300,300,293,293,334,334,296,296,336,46,53,53,52,52,65,65,55,55,70,70,63,63,105,105,66,66,107)

@Composable
fun FaceRecognitionOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onToggleRunning: (Boolean) -> Unit,
    manager: FaceRecognitionManager,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(visible) {
        if (visible && !hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    val debugInfo by manager.debugInfoFlow.collectAsState()
    val scores = debugInfo.scores
    val running = manager.getState().isRunning

    val bg = if (isDark) Color(0xF0121628) else Color(0xF8FFFFFF)
    val cardBg = if (isDark) Color(0xFF1A1E2E) else Color(0x141A2230)
    val bdr = if (isDark) Color(0x28FFFFFF) else Color(0x201A2230)
    val tOn = if (isDark) Color.White else Color.Black
    val tMu = if (isDark) Color(0xCCFFFFFF) else Color(0xCC000000)
    val ac = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)
    val dn = if (isDark) Color(0xFFFF9AA8) else Color(0xFFD9455D)
    val gr = Color(0xFF4ADE80)

    val blinkVal = remember { mutableStateOf(manager.getState().thresholds.blink) }
    val puckerVal = remember { mutableStateOf(manager.getState().thresholds.pucker) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        val maxH = cfg.screenHeightDp.dp * 0.9f
        if (isLandscape) {
            Row(
                Modifier.fillMaxWidth(0.92f).heightIn(max = maxH).clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, bdr, RoundedCornerShape(16.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(10.dp), Arrangement.spacedBy(10.dp)
            ) {
                // Left column: preview + action cards
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                    HeaderBar(manager, running, debugInfo.fps, gr, tMu, cardBg, bdr, tOn, onToggleRunning)
                    Box(Modifier.fillMaxWidth().aspectRatio(4f/3f).clip(RoundedCornerShape(12.dp)).border(1.dp, bdr, RoundedCornerShape(12.dp))) {
                        if (hasPermission) CameraPreview(manager, lifecycleOwner, debugInfo, Modifier.fillMaxSize())
                        else PermPlaceholder(tOn, ac, permLauncher)
                    }
                    ActionCardsRow(scores, ac, dn, cardBg, bdr, tOn, tMu)
                }
                // Right column: controls
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                    ModeSelector(manager, cardBg, bdr, tMu, ac)
                    SliderSection("眨眼阈值", blinkVal, 0.10f..0.95f, tOn, ac, bdr) {
                        blinkVal.value = it; val s = manager.getState(); manager.updateState(s.copy(thresholds = s.thresholds.copy(blink = it)))
                    }
                    SliderSection("撅嘴阈值", puckerVal, 0.05f..0.90f, tOn, ac, bdr) {
                        puckerVal.value = it; val s = manager.getState(); manager.updateState(s.copy(thresholds = s.thresholds.copy(pucker = it)))
                    }
                    DebugBox(manager, hasPermission, running, debugInfo, bdr)
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth(0.9f).heightIn(max = maxH).clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, bdr, RoundedCornerShape(16.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .verticalScroll(rememberScrollState()).padding(12.dp),
                Arrangement.spacedBy(8.dp)
            ) {
                HeaderBar(manager, running, debugInfo.fps, gr, tMu, cardBg, bdr, tOn, onToggleRunning)
                Box(Modifier.fillMaxWidth().aspectRatio(4f/3f).clip(RoundedCornerShape(12.dp)).border(1.dp, bdr, RoundedCornerShape(12.dp))) {
                    if (hasPermission) CameraPreview(manager, lifecycleOwner, debugInfo, Modifier.fillMaxSize())
                    else PermPlaceholder(tOn, ac, permLauncher)
                }
                ActionCardsRow(scores, ac, dn, cardBg, bdr, tOn, tMu)
                ModeSelector(manager, cardBg, bdr, tMu, ac)
                SliderSection("眨眼阈值", blinkVal, 0.10f..0.95f, tOn, ac, bdr) {
                    blinkVal.value = it; val s = manager.getState(); manager.updateState(s.copy(thresholds = s.thresholds.copy(blink = it)))
                }
                SliderSection("撅嘴阈值", puckerVal, 0.05f..0.90f, tOn, ac, bdr) {
                    puckerVal.value = it; val s = manager.getState(); manager.updateState(s.copy(thresholds = s.thresholds.copy(pucker = it)))
                }
                DebugBox(manager, hasPermission, running, debugInfo, bdr)
            }
        }
    }
}

@Composable
private fun HeaderBar(manager: FaceRecognitionManager, running: Boolean, fps: Int, gr: Color, tMu: Color, cardBg: Color, bdr: Color, tOn: Color, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text("面部识别", color = tOn, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (running) gr.copy(alpha = 0.2f) else cardBg).border(1.dp, if (running) gr else bdr, RoundedCornerShape(8.dp)).clickable {
            val s = manager.getState()
            val newRunning = !s.isRunning
            if (newRunning && !manager.isInitialized()) manager.initialize()
            manager.updateState(s.copy(isRunning = newRunning, isEnabled = newRunning))
            onToggle(newRunning)
        }.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(if (running) "● LIVE ${fps}fps" else "○ OFF", color = if (running) gr else tMu, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionCardsRow(scores: FaceRecognitionManager.GestureScores, ac: Color, dn: Color, cardBg: Color, bdr: Color, tOn: Color, tMu: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
        ActionCard(Modifier.weight(1f), "😜", "左Wink", scores.leftWink, ac, cardBg, bdr, tOn, tMu)
        ActionCard(Modifier.weight(1f), "😉", "右Wink", scores.rightWink, ac, cardBg, bdr, tOn, tMu)
        ActionCard(Modifier.weight(1f), "😗", "左撅嘴", scores.leftPucker, dn, cardBg, bdr, tOn, tMu)
        ActionCard(Modifier.weight(1f), "😙", "右撅嘴", scores.rightPucker, dn, cardBg, bdr, tOn, tMu)
    }
}

@Composable
private fun ModeSelector(manager: FaceRecognitionManager, cardBg: Color, bdr: Color, tMu: Color, ac: Color) {
    val s = manager.getState()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cardBg).border(1.dp, bdr, RoundedCornerShape(10.dp)).padding(8.dp), Arrangement.spacedBy(4.dp)) {
        Text("触发模式", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
            ModeBtn(Modifier.weight(1f), "关闭", !s.isEnabled, cardBg, bdr, tMu) { manager.updateState(s.copy(isEnabled = false, isRunning = false)) }
            ModeBtn(Modifier.weight(1f), "Wink", s.isEnabled && s.triggerMode == FaceRecognitionManager.TriggerMode.WINK, cardBg, bdr, ac) {
                if (!manager.isInitialized()) manager.initialize(); manager.updateState(s.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.WINK))
            }
            ModeBtn(Modifier.weight(1f), "撅嘴", s.isEnabled && s.triggerMode == FaceRecognitionManager.TriggerMode.PUCKER, cardBg, bdr, ac) {
                if (!manager.isInitialized()) manager.initialize(); manager.updateState(s.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.PUCKER))
            }
            ModeBtn(Modifier.weight(1f), "两者", s.isEnabled && s.triggerMode == FaceRecognitionManager.TriggerMode.BOTH, cardBg, bdr, ac) {
                if (!manager.isInitialized()) manager.initialize(); manager.updateState(s.copy(isEnabled = true, isRunning = true, triggerMode = FaceRecognitionManager.TriggerMode.BOTH))
            }
        }
    }
}

@Composable
private fun SliderSection(label: String, state: androidx.compose.runtime.MutableState<Float>, range: ClosedFloatingPointRange<Float>, tOn: Color, ac: Color, bdr: Color, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1E2E)).border(1.dp, bdr, RoundedCornerShape(10.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, color = tOn, fontSize = 11.sp)
            Text(String.format("%.2f", state.value), color = ac, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = state.value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = ac, activeTrackColor = ac, inactiveTrackColor = bdr))
    }
}

@Composable
private fun DebugBox(manager: FaceRecognitionManager, hasPerm: Boolean, running: Boolean, debugInfo: FaceRecognitionManager.FaceDebugInfo, bdr: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0D18)).border(1.dp, bdr, RoundedCornerShape(8.dp)).padding(6.dp)) {
        Text("诊断", color = Color(0xFF888888), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text("模型:${if (manager.isInitialized()) "✓" else "✗"} 相机:${if (hasPerm) "✓" else "✗"} 运行:${if (running) "✓" else "○"} FPS:${debugInfo.fps} 关键点:${debugInfo.landmarks?.size ?: 0}", color = Color(0xFF8CC8FF), fontSize = 10.sp)
    }
}

@Composable
private fun PermPlaceholder(tOn: Color, ac: Color, launcher: androidx.activity.result.ActivityResultLauncher<String>) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0D18)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷", fontSize = 32.sp); Spacer(Modifier.height(4.dp)); Text("需要相机权限", color = tOn, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp)); Text("点击授予", color = ac, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { launcher.launch(Manifest.permission.CAMERA) })
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
                scaleType = PreviewView.ScaleType.FIT_CENTER
                val pf = ProcessCameraProvider.getInstance(ctx)
                pf.addListener({
                    val p = pf.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                    val a = ImageAnalysis.Builder().setTargetResolution(android.util.Size(320, 240)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    a.setAnalyzer(exec) { ip: ImageProxy -> try { manager.processImageProxy(ip) } catch (e: Exception) { ip.close() } }
                    try { p.unbindAll(); p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, a) } catch (_: Exception) {}
                }, ctx.mainExecutor)
            }
        }, Modifier.fillMaxSize())

        if (manager.getState().showMesh && debugInfo.landmarks != null) {
            val lm = debugInfo.landmarks!!
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                fun d(conns: IntArray, color: Color, sw: Float) {
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
                d(FACE_CONNS, Color(0x60B08CFF), 1.5f); d(EYE_CONNS, Color(0x9000D4FF), 1.5f)
                d(BROW_CONNS, Color(0x6000D4FF), 1.2f); d(LIP_CONNS, Color(0xA0FF3D8F), 1.6f)
            }
        }
    }
}

@Composable
private fun ActionCard(mod: Modifier, emoji: String, label: String, score: Float, activeColor: Color, cardBg: Color, bdr: Color, tOn: Color, tMu: Color) {
    val on = score > 0.01f
    Column(mod.clip(RoundedCornerShape(10.dp)).background(if (on) activeColor.copy(alpha = 0.15f) else cardBg).border(1.dp, if (on) activeColor.copy(alpha = 0.5f) else bdr, RoundedCornerShape(10.dp)).padding(vertical = 6.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp); Spacer(Modifier.height(1.dp)); Text(label, color = tOn, fontSize = 9.sp, maxLines = 1)
        Text("${(score * 100).toInt()}%", color = if (on) activeColor else tMu, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeBtn(mod: Modifier, text: String, sel: Boolean, cardBg: Color, bdr: Color, activeColor: Color, onClick: () -> Unit) {
    Box(mod.clip(RoundedCornerShape(8.dp)).background(if (sel) activeColor.copy(alpha = 0.2f) else cardBg).border(1.dp, if (sel) activeColor else bdr, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (sel) activeColor else Color(0xFF888888), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}
