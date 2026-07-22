package com.music.msv.facer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Log
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
    private val appContext = context

    data class FaceState(
        val running: Boolean = false,
        val enabled: Boolean = true,
        val triggerMode: TriggerMode = TriggerMode.BOTH,
        val thresholds: Thresholds = Thresholds(),
        val fps: Int = 0,
        val scores: GestureScores = GestureScores(),
        val landmarks: List<NormalizedLandmark>? = null,
        val status: String = ""
    )

    data class Thresholds(val blink: Float = 0.35f, val pucker: Float = 0.25f)
    data class GestureScores(val lWink: Float = 0f, val rWink: Float = 0f, val lPucker: Float = 0f, val rPucker: Float = 0f)
    enum class TriggerMode { WINK, PUCKER, BOTH }
    enum class Gesture { LEFT_WINK, RIGHT_WINK, LEFT_PUCKER, RIGHT_PUCKER, NONE }

    private val _state = MutableStateFlow(FaceState())
    val stateFlow: StateFlow<FaceState> = _state.asStateFlow()
    var onGesture: ((Gesture) -> Unit)? = null

    private var landmarker: FaceLandmarker? = null
    private val smooth = mutableMapOf<String, Float>()
    private val act = mutableMapOf<String, Boolean>()
    private var lastAct = 0L
    private var fpsC = 0; private var fpsT = System.currentTimeMillis()

    private val eyeA = 0.94f; private val eyeR = 0.40f; private val mA = 0.18f; private val mR = 0.45f

    fun init(): Boolean {
        try {
            landmarker = FaceLandmarker.createFromOptions(appContext,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                    .setOutputFaceBlendshapes(true).setNumFaces(1).setRunningMode(RunningMode.IMAGE)
                    .build())
            _state.update { it.copy(status = "模型就绪(IMAGE)") }
            return true
        } catch (e: Exception) {
            _state.update { it.copy(status = "加载失败: ${e.message}") }
            return false
        }
    }

    fun updateState(block: (FaceState) -> FaceState) { _state.update(block) }
    fun currentState() = _state.value
    fun isReady() = landmarker != null

    @OptIn(ExperimentalGetImage::class)
    fun process(ip: ImageProxy) {
        val l = landmarker ?: run { ip.close(); return }
        if (!_state.value.running) { ip.close(); return }
        try {
            val bmp = decode(ip)
            val result = l.detect(BitmapImageBuilder(bmp).build())
            bmp.recycle()

            val rawLM = result.faceLandmarks()
            val lm = if (rawLM.isNotEmpty()) rawLM[0] else null
            val bs = result.faceBlendshapes()
            val cats: List<Category>? = if (bs.isPresent) { val b = bs.get(); if (b.isNotEmpty()) b[0] else null } else null

            val gesture = if (cats != null) processBlendshapes(cats) else Gesture.NONE

            fpsC++; val now = System.currentTimeMillis()
            val fps = if (now - fpsT >= 1000) { val f = fpsC; fpsC = 0; fpsT = now; f } else _state.value.fps

            val lmSize = if (lm != null) lm.size else 0
            val bsInfo = if (cats != null) "BS:${cats.size}" else if (bs.isPresent) "BS:empty" else "BS:absent"
            _state.update { it.copy(fps = fps, landmarks = lm, status = "LM:${rawLM.size}faces ${lmSize}pts ${bsInfo}") }

            if (gesture != Gesture.NONE && System.currentTimeMillis() - lastAct >= 800) {
                lastAct = System.currentTimeMillis()
                onGesture?.invoke(gesture)
            }
        } catch (e: Exception) {
            _state.update { it.copy(status = "ERR: ${e.message}") }
        } finally { ip.close() }
    }

    private fun processBlendshapes(cats: List<Category>): Gesture {
        val eBL = ema("L", score(cats, "eyeBlinkLeft"), true)
        val eBR = ema("R", score(cats, "eyeBlinkRight"), true)
        val avg = (eBL + eBR) / 2f; val diff = kotlin.math.abs(eBL - eBR)
        val mP = ema("P", score(cats, "mouthPucker"), false)
        val mF = ema("F", score(cats, "mouthFunnel"), false)
        val mL = ema("l", score(cats, "mouthLeft"), false)
        val mR = ema("r", score(cats, "mouthRight"), false)

        val th = _state.value.thresholds
        val blink = hyst("b", avg, th.blink) && diff < 0.14f
        val lW = !blink && hyst("w", eBL, th.blink) && diff >= 0.14f
        val rW = !blink && hyst("x", eBR, th.blink) && diff >= 0.14f

        val puck = maxOf(mP, mF * 0.7f); val bL = mL - mR; val bR = mR - mL
        val lP = hyst("p", puck, th.pucker) && bL >= 0.21f
        val rP = hyst("q", puck, th.pucker) && bR >= 0.21f

        val cWL = if (lW) (diff / 0.5f).coerceIn(0f, 1f) else 0f; val cWR = if (rW) (diff / 0.5f).coerceIn(0f, 1f) else 0f
        val cPL = if (lP) ((puck * maxOf(bL, 0f)) / 0.25f).coerceIn(0f, 1f) else 0f; val cPR = if (rP) ((puck * maxOf(bR, 0f)) / 0.25f).coerceIn(0f, 1f) else 0f
        _state.update { it.copy(scores = GestureScores(cWL, cWR, cPL, cPR)) }

        val aw = _state.value.triggerMode != TriggerMode.PUCKER; val ap = _state.value.triggerMode != TriggerMode.WINK
        return when { aw && rW -> Gesture.RIGHT_WINK; aw && lW -> Gesture.LEFT_WINK; ap && lP -> Gesture.LEFT_PUCKER; ap && rP -> Gesture.RIGHT_PUCKER; else -> Gesture.NONE }
    }

    private fun score(cats: List<Category>, n: String) = cats.find { it.categoryName() == n }?.score() ?: 0f
    private fun ema(k: String, raw: Float, eye: Boolean): Float {
        val cur = smooth[k] ?: raw; val f = if (raw > cur) (if (eye) eyeA else mA) else (if (eye) eyeR else mR)
        return (cur * (1f - f) + raw * f).also { smooth[k] = it }
    }
    private fun hyst(k: String, raw: Float, thr: Float): Boolean {
        val cur = act[k] ?: false
        return if (cur) { if (raw < thr * 0.6f) { act[k] = false; false } else true } else { if (raw >= thr) { act[k] = true; true } else false }
    }

    private fun decode(ip: ImageProxy): Bitmap {
        val buf = ip.planes[0].buffer; val bytes = ByteArray(buf.remaining()); buf.get(bytes)
        val yuv = YuvImage(bytes, ImageFormat.NV21, ip.width, ip.height, null)
        val out = ByteArrayOutputStream(); yuv.compressToJpeg(Rect(0, 0, ip.width, ip.height), 90, out)
        val jpg = out.toByteArray(); out.close()
        val bmp = BitmapFactory.decodeByteArray(jpg, 0, jpg.size)
        val mat = Matrix(); mat.postRotate(ip.imageInfo.rotationDegrees.toFloat()); mat.preScale(-1f, 1f)
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true).also { bmp.recycle() }
    }

    fun close() { landmarker?.close(); landmarker = null; smooth.clear(); act.clear() }

    private inline fun <T> MutableStateFlow<T>.update(func: (T) -> T) { value = func(value) }
    private fun <T> List<T>?.getOrNull(i: Int): T? = if (this != null && i in indices) get(i) else null
}
