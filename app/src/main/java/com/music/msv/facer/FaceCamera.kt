package com.music.msv.facer

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

private val F = intArrayOf(10,338,338,297,297,332,332,284,284,251,251,389,389,356,356,454,454,323,323,361,361,288,288,397,397,365,365,379,379,378,378,400,400,377,377,152,152,148,148,176,176,149,149,150,150,136,136,172,172,58,58,132,132,93,93,234,234,127,127,162,162,21,21,54,54,103,103,67,67,109,109,10)
private val E = intArrayOf(33,7,7,163,163,144,144,145,145,153,153,154,154,155,155,133,133,173,173,157,157,158,158,159,159,160,160,161,161,246,246,33,362,382,382,381,381,380,380,374,374,373,373,390,390,249,249,263,263,466,466,388,388,387,387,386,386,385,385,384,384,398,398,362)
private val L = intArrayOf(61,146,146,91,91,181,181,84,84,17,17,314,314,405,405,321,321,375,375,291,291,409,409,270,270,269,269,267,267,0,0,37,37,39,39,40,40,185,185,61)
private val B = intArrayOf(276,283,283,282,282,295,295,285,285,300,300,293,293,334,334,296,296,336,46,53,53,52,52,65,65,55,55,70,70,63,63,105,105,66,66,107)

@Composable
fun FaceCamera(
    manager: FaceRecognitionManager,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val lc = LocalLifecycleOwner.current
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val pL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it; if (!it) Toast.makeText(ctx, "需相机权限", Toast.LENGTH_SHORT).show() }

    LaunchedEffect(Unit) { if (!hasPerm) pL.launch(Manifest.permission.CAMERA) }

    if (!hasPerm) return

    if (!manager.isReady()) {
        val ok = manager.init()
        if (!ok) { FaceLog.e("MSV_CAM", "模型加载失败"); return }
    }

    val exec = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val p = future.get()
                val a = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(320, 240))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                a.setAnalyzer(exec) { ip: ImageProxy ->
                    try { manager.process(ip) } catch (e: Exception) { FaceLog.e("MSV_CAM", "分析器异常: ${e.message}", e); ip.close() }
                }
                // 仅绑定分析流：预览画面直接使用送检位图（WYSIWYG），彻底消除预览/叠加变换不一致
                try { p.unbindAll(); p.bindToLifecycle(lc, CameraSelector.DEFAULT_FRONT_CAMERA, a) }
                catch (e: Exception) { FaceLog.e("MSV_CAM", "绑定失败: ${e.message}", e) }
            } catch (e: Exception) {
                FaceLog.e("MSV_CAM", "相机初始化失败: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            FaceLog.d("MSV_CAM", "FaceCamera离开组合")
            runCatching { future.get().unbindAll() }
            exec.shutdown()
        }
    }

    val state by manager.stateFlow.collectAsState()
    var drawn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { drawn = true }
    val alpha by animateFloatAsState(if (drawn && visible) 1f else 0f, tween(if (visible) 200 else 0), label = "camAlpha")

    val camModifier = if (visible) modifier.fillMaxWidth().aspectRatio(4f / 3f) else modifier.size(1.dp)

    Box(camModifier.graphicsLayer { this.alpha = if (visible) alpha else 0f }) {
        val previewImage = state.previewImage
        val fw = state.frameWidth.toFloat()
        val fh = state.frameHeight.toFloat()
        if (visible && previewImage != null && fw > 0f && fh > 0f) {
            // 送检位图即预览画面（已按 mirrored 在显示帧内旋转/镜像），预览与线条同空间必然对齐；
            // 容器锁定位图宽高比，竖屏不失真不偏大
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(fw / fh)) {
                    Image(
                        bitmap = previewImage,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (state.landmarks != null) {
                        val lm = state.landmarks!!
                        Canvas(Modifier.fillMaxSize()) { val w = size.width; val h = size.height
                            fun px(i: Int) = Offset(lm[i].x() * w, lm[i].y() * h)
                            fun d(c: IntArray, cl: Color, sw: Float) { var i = 0; while (i < c.size - 1) { val a = c[i]; val bb = c[i + 1]; if (a < lm.size && bb < lm.size) { drawLine(cl, px(a), px(bb), strokeWidth = sw) }; i += 2 } }
                            d(F, Color(0xCCB08CFF), 3f); d(E, Color(0xDD00D4FF), 3f); d(B, Color(0xCC00D4FF), 2.5f); d(L, Color(0xDDFF3D8F), 3f)
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0D16)), contentAlignment = Alignment.Center) {
                if (visible) Text("正在启动相机…", color = Color(0x66FFFFFF), fontSize = 12.sp)
            }
        }
    }
}
