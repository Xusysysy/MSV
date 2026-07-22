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
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class FaceRecognitionManager(private val context: Context) {

    data class FaceState(val isRunning: Boolean = false, val isEnabled: Boolean = true, val showMesh: Boolean = true, val triggerMode: TriggerMode = TriggerMode.BOTH, val thresholds: Thresholds = Thresholds(), val cooldownMs: Long = 800L)
    data class Thresholds(val blink: Float = 0.35f, val winkDiff: Float = 0.14f, val pucker: Float = 0.25f, val puckerBias: Float = 0.21f)
    enum class TriggerMode { WINK, PUCKER, BOTH }
    enum class Gesture { LEFT_WINK, RIGHT_WINK, LEFT_PUCKER, RIGHT_PUCKER, NONE }
    data class GestureScores(val leftWink: Float = 0f, val rightWink: Float = 0f, val leftPucker: Float = 0f, val rightPucker: Float = 0f)
    data class FaceDebugInfo(val fps: Int = 0, val scores: GestureScores = GestureScores(), val landmarks: List<NormalizedLandmark>? = null, val log: String = "")

    private var landmarker: FaceLandmarker? = null
    @Volatile private var state = FaceState()
    private var onGestureDetected: ((Gesture) -> Unit)? = null
    private val _debugInfoFlow = MutableStateFlow(FaceDebugInfo())
    val debugInfoFlow: StateFlow<FaceDebugInfo> = _debugInfoFlow.asStateFlow()
    private val smoothState = mutableMapOf<String, Float>()
    private val activeState = mutableMapOf<String, Boolean>()
    private var lastActionTime = 0L
    private var fpsFrames = 0; private var fpsTime = System.currentTimeMillis()
    private val eyeAtk = 0.94f; private val eyeRel = 0.40f; private val mouthAtk = 0.18f; private val mouthRel = 0.45f
    private val hystAtk = 1.0f; private val hystDeact = 0.60f

    fun setOnGestureDetected(l: (Gesture) -> Unit) { onGestureDetected = l }
    fun getState() = state
    fun updateState(s: FaceState) { state = s }
    fun isInitialized() = landmarker != null

    fun initialize(): Boolean = try {
        landmarker = FaceLandmarker.createFromOptions(context,
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                .setOutputFaceBlendshapes(true).setNumFaces(1).setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> onResult(result) }
                .build())
        Log.d("FaceMgr", "OK")
        true
    } catch (e: Exception) { Log.e("FaceMgr", "INIT FAIL", e); false }

    fun processImageProxy(ip: ImageProxy) {
        val l = landmarker ?: run { ip.close(); return }
        if (!state.isRunning) { ip.close(); return }
        try {
            val bmp = toBmp(ip)
            l.detectAsync(BitmapImageBuilder(bmp).build(), SystemClock.elapsedRealtime())
            bmp.recycle()
        } catch (e: Exception) { Log.e("FaceMgr", "process: ${e.message}") }
        finally { ip.close() }
    }

    private fun onResult(result: FaceLandmarkerResult) {
        try {
            var landmarks: List<NormalizedLandmark>? = null
            var log = ""

            val rawLM = result.faceLandmarks()
            log += "LM type:${rawLM.javaClass.simpleName} size:${rawLM.size} "
            if (rawLM.isNotEmpty()) {
                val first = rawLM[0]
                if (first is List<*>) {
                    landmarks = first.filterIsInstance<NormalizedLandmark>()
                    log += "list[0]=${landmarks.size}pts "
                } else {
                    landmarks = rawLM.filterIsInstance<NormalizedLandmark>()
                    log += "flat=${landmarks.size}pts "
                }
            }

            var gesture = Gesture.NONE
            val rawBS = result.faceBlendshapes()
            log += "BS type:${rawBS.javaClass.simpleName}"

            if (rawBS is java.util.Optional<*>) {
                if (rawBS.isPresent) {
                    val inner = rawBS.get()
                    @Suppress("UNCHECKED_CAST")
                    val bsList = inner as? List<List<Category>>
                    if (bsList != null && bsList.isNotEmpty()) {
                        gesture = processBlendshapes(bsList[0])
                        log += " opt[ok]"
                    } else log += " opt[empty]"
                } else log += " opt[absent]"
            } else if (rawBS is List<*>) {
                if (rawBS.isNotEmpty()) {
                    val first = rawBS[0]
                    @Suppress("UNCHECKED_CAST")
                    val cats = first as? List<Category> ?: rawBS.filterIsInstance<Category>()
                    gesture = processBlendshapes(cats)
                    log += " list[ok]"
                } else log += " list[empty]"
            }

            fpsFrames++
            val now = System.currentTimeMillis()
            val fps = if (now - fpsTime >= 1000) { val f = fpsFrames; fpsFrames = 0; fpsTime = now; f } else _debugInfoFlow.value.fps
            _debugInfoFlow.value = FaceDebugInfo(fps, _debugInfoFlow.value.scores, landmarks, log)

            if (gesture != Gesture.NONE && System.currentTimeMillis() - lastActionTime >= state.cooldownMs) {
                lastActionTime = System.currentTimeMillis()
                onGestureDetected?.invoke(gesture)
            }
        } catch (e: Exception) {
            Log.e("FaceMgr", "onResult: ${e.message}")
            _debugInfoFlow.value = _debugInfoFlow.value.copy(log = "ERR: ${e.message}")
        }
    }

    private fun toBmp(proxy: ImageProxy): Bitmap {
        val buf = proxy.planes[0].buffer; val bytes = ByteArray(buf.remaining()); buf.get(bytes)
        val yuv = YuvImage(bytes, ImageFormat.NV21, proxy.width, proxy.height, null)
        val out = ByteArrayOutputStream(); yuv.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 90, out)
        val jpg = out.toByteArray(); out.close()
        val bmp = BitmapFactory.decodeByteArray(jpg, 0, jpg.size)
        val mat = Matrix(); mat.postRotate(proxy.imageInfo.rotationDegrees.toFloat()); mat.preScale(-1f, 1f)
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true).also { bmp.recycle() }
    }

    private fun processBlendshapes(cats: List<Category>): Gesture {
        val eBL = ema("eBL", sc(cats, "eyeBlinkLeft"), true); val eBR = ema("eBR", sc(cats, "eyeBlinkRight"), true)
        val avg = (eBL+eBR)/2f; val diff = kotlin.math.abs(eBL-eBR)
        val mP = ema("mP", sc(cats, "mouthPucker"), false); val mF = ema("mF", sc(cats, "mouthFunnel"), false)
        val mL = ema("mL", sc(cats, "mouthLeft"), false); val mR = ema("mR", sc(cats, "mouthRight"), false)
        val blink = hyst("bl",avg,state.thresholds.blink) && diff<state.thresholds.winkDiff
        val lW = !blink && hyst("wl",eBL,state.thresholds.blink) && diff>=state.thresholds.winkDiff
        val rW = !blink && hyst("wr",eBR,state.thresholds.blink) && diff>=state.thresholds.winkDiff
        val puck = maxOf(mP,mF*0.7f); val bL = mL-mR; val bR = mR-mL
        val lP = hyst("lp",puck,state.thresholds.pucker) && bL>=state.thresholds.puckerBias
        val rP = hyst("rp",puck,state.thresholds.pucker) && bR>=state.thresholds.puckerBias
        val cWL = if(lW) (diff/0.5f).coerceIn(0f,1f) else 0f; val cWR = if(rW) (diff/0.5f).coerceIn(0f,1f) else 0f
        val cPL = if(lP) ((puck*maxOf(bL,0f))/0.25f).coerceIn(0f,1f) else 0f; val cPR = if(rP) ((puck*maxOf(bR,0f))/0.25f).coerceIn(0f,1f) else 0f
        _debugInfoFlow.value = _debugInfoFlow.value.copy(scores = GestureScores(cWL,cWR,cPL,cPR))
        val aw = state.triggerMode!=TriggerMode.PUCKER; val ap = state.triggerMode!=TriggerMode.WINK
        return when { aw&&rW->Gesture.RIGHT_WINK; aw&&lW->Gesture.LEFT_WINK; ap&&lP->Gesture.LEFT_PUCKER; ap&&rP->Gesture.RIGHT_PUCKER; else->Gesture.NONE }
    }

    private fun sc(cats: List<Category>, n: String) = cats.find{it.categoryName()==n}?.score()?:0f
    private fun ema(k: String, raw: Float, eye: Boolean): Float {
        val cur = smoothState[k]?:raw; val f = if(raw>cur) (if(eye)eyeAtk else mouthAtk) else (if(eye)eyeRel else mouthRel)
        return (cur*(1f-f)+raw*f).also{smoothState[k]=it}
    }
    private fun hyst(k: String, raw: Float, thr: Float): Boolean {
        val cur = activeState[k]?:false
        return if(cur){ if(raw<thr*hystDeact){activeState[k]=false;false}else true }
        else{ if(raw>=thr*hystAtk){activeState[k]=true;true}else false }
    }

    fun release() { landmarker?.close(); landmarker=null; smoothState.clear(); activeState.clear() }
}
