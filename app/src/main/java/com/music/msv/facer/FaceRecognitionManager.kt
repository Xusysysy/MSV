package com.music.msv.facer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class FaceRecognitionManager(context: Context) {
    companion object { const val TAG = "MSV_FACE" }
    private val appContext = context
    private val mainHandler = Handler(Looper.getMainLooper())

    data class FaceState(
        val running: Boolean = false, val enabled: Boolean = true, val triggerMode: TriggerMode = TriggerMode.BOTH,
        val thresholds: Thresholds = Thresholds(), val mirrored: Boolean = true,
        val actionThreshold: Float = 0.1f, val actionActive: Boolean = false,
        val fps: Int = 0, val scores: GestureScores = GestureScores(),
        val landmarks: List<NormalizedLandmark>? = null, val status: String = ""
    )
    data class Thresholds(val blink: Float = 0.35f, val pucker: Float = 0.25f, val puckerBiasL: Float = 0.21f, val puckerBiasR: Float = 0.21f)
    data class GestureScores(val lWink: Float = 0f, val rWink: Float = 0f, val lPucker: Float = 0f, val rPucker: Float = 0f)
    enum class TriggerMode { WINK, PUCKER, BOTH }
    enum class Gesture { LEFT_WINK, RIGHT_WINK, LEFT_PUCKER, RIGHT_PUCKER, NONE }

    private val _state = MutableStateFlow(FaceState())
    val stateFlow: StateFlow<FaceState> = _state.asStateFlow()
    var onGesture: ((Gesture) -> Unit)? = null

    private var landmarker: FaceLandmarker? = null
    private val smooth = mutableMapOf<String, Float>(); private val act = mutableMapOf<String, Boolean>()
    private var lastAct = 0L; private var fpsC = 0; private var fpsT = System.currentTimeMillis()
    private val eyeA = 0.94f; private val eyeR = 0.40f; private val mA = 0.18f; private val mR = 0.45f

    fun init(): Boolean {
        FaceLog.d(TAG, ">>> init() 开始加载face_landmarker.task")
        return try {
            landmarker = FaceLandmarker.createFromOptions(appContext,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                    .setOutputFaceBlendshapes(true).setNumFaces(1).setRunningMode(RunningMode.IMAGE).build())
            FaceLog.d(TAG, "<<< init() 模型加载成功")
            _state.update { it.copy(status = "模型就绪") }; true
        } catch (e: Exception) {
            FaceLog.e(TAG, "<<< init() 模型加载失败: ${e.message}", e)
            _state.update { it.copy(status = "加载失败: ${e.message}") }; false
        }
    }

    fun updateState(block: (FaceState) -> FaceState) { _state.update(block) }
    fun currentState() = _state.value
    fun isReady() = landmarker != null

    private var frameCount = 0
    @OptIn(ExperimentalGetImage::class)
    fun process(ip: ImageProxy) {
        val l = landmarker ?: run { FaceLog.w(TAG, "process: landmarker为空,关闭ImageProxy"); ip.close(); return }
        if (!_state.value.running) { FaceLog.d(TAG, "process: 未运行,跳过"); ip.close(); return }
        frameCount++
        try {
            val bmp = decode(ip)
            if (frameCount <= 3) FaceLog.d(TAG, "process $frameCount: decode完成 ${bmp.width}x${bmp.height}")
            val result = l.detect(BitmapImageBuilder(bmp).build()); bmp.recycle()
            if (frameCount <= 3) FaceLog.d(TAG, "process $frameCount: detect完成")
            val rawLM = result.faceLandmarks(); val lm = if (rawLM.isNotEmpty()) rawLM[0] else null
            val bs = result.faceBlendshapes()
            val cats: List<Category>? = if (bs.isPresent) { val b = bs.get(); if (b.isNotEmpty()) b[0] else null } else null
            val gesture = if (cats != null) processBlendshapes(cats) else Gesture.NONE
            fpsC++; val now = System.currentTimeMillis()
            val fps = if (now - fpsT >= 1000) { val f = fpsC; fpsC = 0; fpsT = now; f } else _state.value.fps
            _state.update { it.copy(fps = fps, landmarks = lm, status = "LM:${rawLM.size}f ${lm?.size ?: 0}pts") }
            if (gesture != Gesture.NONE && System.currentTimeMillis() - lastAct >= 800) {
                lastAct = System.currentTimeMillis(); val g = gesture
                FaceLog.i(TAG, "process: 检测到手势 $g")
                mainHandler.post { onGesture?.invoke(g) }
            }
        } catch (e: Exception) {
            FaceLog.e(TAG, "process $frameCount: 异常 ${e.message}", e)
            _state.update { it.copy(status = "ERR: ${e.message}") }
        } finally { ip.close() }
    }

    private fun processBlendshapes(cats: List<Category>): Gesture {
        val eBL = ema("L", score(cats, "eyeBlinkLeft"), true); val eBR = ema("R", score(cats, "eyeBlinkRight"), true)
        val avg = (eBL+eBR)/2f; val diff = kotlin.math.abs(eBL-eBR)
        val mP = ema("P", score(cats, "mouthPucker"), false); val mF = ema("F", score(cats, "mouthFunnel"), false)
        val mL = ema("l", score(cats, "mouthLeft"), false); val mR = ema("r", score(cats, "mouthRight"), false)
        val th = _state.value.thresholds
        val blink = hyst("b",avg,th.blink) && diff<0.14f
        val lW = !blink && hyst("w",eBL,th.blink) && diff>=0.14f; val rW = !blink && hyst("x",eBR,th.blink) && diff>=0.14f
        val puck = maxOf(mP,mF*0.7f); val bL = mL-mR; val bR = mR-mL
        val lP = hyst("p",puck,th.pucker) && bL>=th.puckerBiasL; val rP = hyst("q",puck,th.pucker) && bR>=th.puckerBiasR
        val cWL = if(lW) (diff/0.5f).coerceIn(0f,1f) else 0f; val cWR = if(rW) (diff/0.5f).coerceIn(0f,1f) else 0f
        val cPL = if(lP) ((puck*maxOf(bL,0f))/0.25f).coerceIn(0f,1f) else 0f; val cPR = if(rP) ((puck*maxOf(bR,0f))/0.25f).coerceIn(0f,1f) else 0f
        _state.update { it.copy(scores = GestureScores(cWL,cWR,cPL,cPR)) }
        val at = _state.value.actionThreshold; val aw = _state.value.triggerMode!=TriggerMode.PUCKER; val ap = _state.value.triggerMode!=TriggerMode.WINK
        val active = cWL >= at || cWR >= at || cPL >= at || cPR >= at
        _state.update { it.copy(scores = GestureScores(cWL,cWR,cPL,cPR), actionActive = active) }
        return when { aw&&rW&&cWR>=at->Gesture.RIGHT_WINK; aw&&lW&&cWL>=at->Gesture.LEFT_WINK; ap&&lP&&cPL>=at->Gesture.LEFT_PUCKER; ap&&rP&&cPR>=at->Gesture.RIGHT_PUCKER; else->Gesture.NONE }
    }

    private fun score(cats: List<Category>, n: String) = cats.find{it.categoryName()==n}?.score()?:0f
    private fun ema(k: String, raw: Float, eye: Boolean): Float {
        val cur = smooth[k]?:raw; val f = if(raw>cur) (if(eye)eyeA else mA) else (if(eye)eyeR else mR)
        return (cur*(1f-f)+raw*f).also{smooth[k]=it}
    }
    private fun hyst(k: String, raw: Float, thr: Float): Boolean {
        val cur = act[k]?:false
        return if(cur){ if(raw<thr*0.6f){act[k]=false;false}else true }else{ if(raw>=thr){act[k]=true;true}else false }
    }
    private fun decode(ip: ImageProxy): Bitmap {
        try {
            val planes = ip.planes
            if (frameCount <= 3) FaceLog.d(TAG, "decode $frameCount: format=${ip.format} w=${ip.width} h=${ip.height} planes=${planes.size}")
            val yBuf = planes[0].buffer
            val uBuf = planes[1].buffer
            val vBuf = planes[2].buffer
            val ySize = yBuf.remaining()
            val uSize = uBuf.remaining()
            val vSize = vBuf.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuf.get(nv21, 0, ySize)
            vBuf.get(nv21, ySize, vSize)
            uBuf.get(nv21, ySize + vSize, uSize)
            val w = ip.width; val h = ip.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            convertYuvToBitmap(nv21, w, h, bmp)
            val rotation = ip.imageInfo.rotationDegrees
            if (rotation != 0 || _state.value.mirrored) {
                val mat = Matrix()
                mat.postRotate(rotation.toFloat())
                if (_state.value.mirrored) mat.preScale(-1f, 1f)
                val rotated = Bitmap.createBitmap(bmp, 0, 0, w, h, mat, true)
                bmp.recycle()
                return rotated
            }
            return bmp
        } catch (e: Exception) {
            FaceLog.e(TAG, "decode $frameCount: 异常 ${e.message}", e)
            throw e
        }
    }

    private fun convertYuvToBitmap(nv21: ByteArray, w: Int, h: Int, out: Bitmap) {
        val pixels = IntArray(w * h)
        var yp = 0; var uvp = w * h
        for (j in 0 until h) {
            for (i in 0 until w) {
                val y = nv21[yp + j * w + i].toInt() and 0xFF
                val uvIdx = uvp + (j shr 1) * w + (i and -2)
                val u = nv21[uvIdx].toInt() and 0xFF
                val v = nv21[uvIdx + 1].toInt() and 0xFF
                val yVal = y - 16
                val uVal = u - 128
                val vVal = v - 128
                var r = (1.164f * yVal + 1.596f * vVal).toInt()
                var g = (1.164f * yVal - 0.392f * uVal - 0.813f * vVal).toInt()
                var b = (1.164f * yVal + 2.017f * uVal).toInt()
                r = r.coerceIn(0, 255); g = g.coerceIn(0, 255); b = b.coerceIn(0, 255)
                pixels[j * w + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
    }
    fun close() { landmarker?.close();landmarker=null;smooth.clear();act.clear() }
    private inline fun <T> MutableStateFlow<T>.update(func: (T) -> T) { value = func(value) }
}
