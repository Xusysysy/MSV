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
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.Optional

class FaceRecognitionManager(private val context: Context) {

    data class FaceState(
        val isRunning: Boolean = false,
        val isEnabled: Boolean = true,
        val showMesh: Boolean = true,
        val triggerMode: TriggerMode = TriggerMode.BOTH,
        val thresholds: Thresholds = Thresholds(),
        val cooldownMs: Long = 800L
    )

    data class Thresholds(
        val blink: Float = 0.35f,
        val winkDiff: Float = 0.14f,
        val pucker: Float = 0.25f,
        val puckerBias: Float = 0.21f
    )

    enum class TriggerMode { WINK, PUCKER, BOTH }
    enum class Gesture { LEFT_WINK, RIGHT_WINK, LEFT_PUCKER, RIGHT_PUCKER, NONE }

    data class GestureScores(
        val leftWink: Float = 0f,
        val rightWink: Float = 0f,
        val leftPucker: Float = 0f,
        val rightPucker: Float = 0f
    )

    data class FaceDebugInfo(
        val fps: Int = 0,
        val scores: GestureScores = GestureScores(),
        val landmarks: List<NormalizedLandmark>? = null
    )

    private var landmarker: FaceLandmarker? = null
    @Volatile
    private var state = FaceState()
    private var onGestureDetected: ((Gesture) -> Unit)? = null
    private val _debugInfoFlow = MutableStateFlow(FaceDebugInfo())
    val debugInfoFlow: StateFlow<FaceDebugInfo> = _debugInfoFlow.asStateFlow()

    private val smoothState = mutableMapOf<String, Float>()
    private val activeState = mutableMapOf<String, Boolean>()
    private var lastActionTime = 0L
    private var fpsFrames = 0
    private var fpsTime = System.currentTimeMillis()

    private val eyeAtk = 0.94f; private val eyeRel = 0.40f
    private val mouthAtk = 0.18f; private val mouthRel = 0.45f
    private val hystAtk = 1.0f; private val hystDeact = 0.60f

    fun setOnGestureDetected(listener: (Gesture) -> Unit) { onGestureDetected = listener }
    fun getState(): FaceState = state
    fun updateState(newState: FaceState) { state = newState }
    fun isInitialized(): Boolean = landmarker != null

    fun initialize(): Boolean {
        return try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                .setOutputFaceBlendshapes(true)
                .setNumFaces(1)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener({ result, _ -> onLandmarkResult(result!!) })
                .build()
            landmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d("FaceMgr", "FaceLandmarker created OK")
            true
        } catch (e: Exception) {
            Log.e("FaceMgr", "FaceLandmarker create failed", e)
            false
        }
    }

    fun processImageProxy(imageProxy: ImageProxy) {
        val l = landmarker ?: run { imageProxy.close(); return }
        if (!state.isRunning || !state.isEnabled) { imageProxy.close(); return }
        try {
            val bmp = toBitmap(imageProxy)
            val mpImg = BitmapImageBuilder(bmp).build()
            l.detectAsync(mpImg, SystemClock.elapsedRealtime())
            bmp.recycle()
        } catch (e: Exception) {
            Log.e("FaceMgr", "processImageProxy error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun onLandmarkResult(result: FaceLandmarkerResult) {
        try {
            var lm: List<NormalizedLandmark>? = null
            try {
                val raw = result.faceLandmarks()
                when (raw) {
                    is List<*> -> {
                        if (raw.isNotEmpty()) {
                            val first = raw[0]
                            if (first is List<*>) lm = first.filterIsInstance<NormalizedLandmark>().ifEmpty { null }
                            else if (first is NormalizedLandmark) lm = raw.filterIsInstance<NormalizedLandmark>().ifEmpty { null }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("FaceMgr", "landmarks parse: ${e.message}")
            }

            var gesture = Gesture.NONE
            var scores = GestureScores()

            try {
                val bsOpt = result.faceBlendshapes()
                val bsList = when (bsOpt) {
                    is java.util.Optional<*> -> if (bsOpt.isPresent) bsOpt.get() else null
                    is List<*> -> bsOpt
                    else -> null
                }
                if (bsList is List<*> && bsList.isNotEmpty()) {
                    val first = bsList[0]
                    if (first is List<*>) {
                        val cats = first.filterIsInstance<com.google.mediapipe.tasks.components.containers.Category>()
                        if (cats.isNotEmpty()) {
                            gesture = processBlendshapes(cats)
                            scores = _debugInfoFlow.value.scores
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("FaceMgr", "blendshapes parse: ${e.message}")
            }

            fpsFrames++
            val now = System.currentTimeMillis()
            val fps = if (now - fpsTime >= 1000) { val f = fpsFrames; fpsFrames = 0; fpsTime = now; f } else _debugInfoFlow.value.fps
            _debugInfoFlow.value = FaceDebugInfo(fps, scores, lm)

            if (gesture != Gesture.NONE) {
                val gn = System.currentTimeMillis()
                if (gn - lastActionTime >= state.cooldownMs) {
                    lastActionTime = gn
                    onGestureDetected?.invoke(gesture)
                }
            }
        } catch (e: Exception) {
            Log.e("FaceMgr", "onResult error: ${e.message}")
        }
    }

    private fun toBitmap(proxy: ImageProxy): Bitmap {
        val buf = proxy.planes[0].buffer
        val bytes = ByteArray(buf.remaining()); buf.get(bytes)
        val yuv = YuvImage(bytes, ImageFormat.NV21, proxy.width, proxy.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 90, out)
        val jpg = out.toByteArray(); out.close()
        val bmp = BitmapFactory.decodeByteArray(jpg, 0, jpg.size)
        val rot = proxy.imageInfo.rotationDegrees
        val mat = Matrix(); mat.postRotate(rot.toFloat()); mat.preScale(-1f, 1f)
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true).also { bmp.recycle() }
    }

    private fun processBlendshapes(cats: List<com.google.mediapipe.tasks.components.containers.Category>): Gesture {
        val eBL = ema("eBL", score(cats, "eyeBlinkLeft"), true)
        val eBR = ema("eBR", score(cats, "eyeBlinkRight"), true)
        val avg = (eBL + eBR) / 2f; val diff = kotlin.math.abs(eBL - eBR)
        val mP = ema("mP", score(cats, "mouthPucker"), false)
        val mF = ema("mF", score(cats, "mouthFunnel"), false)
        val mL = ema("mL", score(cats, "mouthLeft"), false)
        val mR = ema("mR", score(cats, "mouthRight"), false)

        val blink = hyst("bl", avg, state.thresholds.blink) && diff < state.thresholds.winkDiff
        val lW = !blink && hyst("wl", eBL, state.thresholds.blink) && diff >= state.thresholds.winkDiff
        val rW = !blink && hyst("wr", eBR, state.thresholds.blink) && diff >= state.thresholds.winkDiff

        val puck = maxOf(mP, mF * 0.7f)
        val bL = mL - mR; val bR = mR - mL
        val lP = hyst("lp", puck, state.thresholds.pucker) && bL >= state.thresholds.puckerBias
        val rP = hyst("rp", puck, state.thresholds.pucker) && bR >= state.thresholds.puckerBias

        val cWL = if (lW) (diff / 0.5f).coerceIn(0f, 1f) else 0f
        val cWR = if (rW) (diff / 0.5f).coerceIn(0f, 1f) else 0f
        val cPL = if (lP) ((puck * maxOf(bL, 0f)) / 0.25f).coerceIn(0f, 1f) else 0f
        val cPR = if (rP) ((puck * maxOf(bR, 0f)) / 0.25f).coerceIn(0f, 1f) else 0f
        _debugInfoFlow.value = _debugInfoFlow.value.copy(scores = GestureScores(cWL, cWR, cPL, cPR))

        val aw = state.triggerMode != TriggerMode.PUCKER; val ap = state.triggerMode != TriggerMode.WINK
        return when { aw && rW -> Gesture.RIGHT_WINK; aw && lW -> Gesture.LEFT_WINK; ap && lP -> Gesture.LEFT_PUCKER; ap && rP -> Gesture.RIGHT_PUCKER; else -> Gesture.NONE }
    }

    private fun score(cats: List<com.google.mediapipe.tasks.components.containers.Category>, name: String) = cats.find { it.categoryName() == name }?.score() ?: 0f
    private fun ema(k: String, raw: Float, eye: Boolean): Float {
        val cur = smoothState[k] ?: raw
        val f = if (raw > cur) (if (eye) eyeAtk else mouthAtk) else (if (eye) eyeRel else mouthRel)
        val s = cur * (1f - f) + raw * f; smoothState[k] = s; return s
    }
    private fun hyst(k: String, raw: Float, thr: Float): Boolean {
        val cur = activeState[k] ?: false
        return if (cur) { if (raw < thr * hystDeact) { activeState[k] = false; false } else true }
        else { if (raw >= thr * hystAtk) { activeState[k] = true; true } else false }
    }

    fun release() {
        landmarker?.close(); landmarker = null
        smoothState.clear(); activeState.clear()
    }
}
