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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@Composable
fun FaceCamera(manager: FaceRecognitionManager, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lc = LocalLifecycleOwner.current
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val pL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it; if (!it) Toast.makeText(ctx, "需相机权限", Toast.LENGTH_SHORT).show() }

    LaunchedEffect(Unit) { if (!hasPerm) pL.launch(Manifest.permission.CAMERA) }

    if (!hasPerm) return

    FaceLog.d("MSV_CAM", "FaceCamera进入组合, 确保模型就绪")
    if (!manager.isReady()) {
        val ok = manager.init()
        if (!ok) {
            FaceLog.e("MSV_CAM", "模型加载失败, 不启动相机")
            return
        }
    }

    val exec = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { FaceLog.d("MSV_CAM", "FaceCamera离开组合"); exec.shutdown() } }

    Box(Modifier.size(1.dp).then(modifier)) {
        AndroidView(factory = { ctx2 -> PreviewView(ctx2).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            val future = ProcessCameraProvider.getInstance(ctx2)
            future.addListener({
                val p = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                val a = ImageAnalysis.Builder().setTargetResolution(android.util.Size(320, 240)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                a.setAnalyzer(exec) { ip: ImageProxy ->
                    try { manager.process(ip) } catch (e: Exception) { FaceLog.e("MSV_CAM", "分析器异常: ${e.message}", e); ip.close() }
                }
                try { p.unbindAll(); p.bindToLifecycle(lc, CameraSelector.DEFAULT_FRONT_CAMERA, preview, a); FaceLog.d("MSV_CAM", "FaceCamera绑定成功") }
                catch (e: Exception) { FaceLog.e("MSV_CAM", "FaceCamera绑定失败: ${e.message}", e) }
            }, ctx2.mainExecutor)
        } }, Modifier.size(1.dp))
    }
}
