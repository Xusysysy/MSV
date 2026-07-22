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
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

private const val TAG = "FaceRecMgr"

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
    private var fpsFrameCount = 0
    private var fpsLastTime = System.currentTimeMillis()

    private val eyeAttack = 0.94f
    private val eyeRelease = 0.40f
    private val mouthAttack = 0.18f
    private val mouthRelease = 0.45f
    private val hystAttack = 1.0f
    private val hystDeact = 0.60f

    fun setOnGestureDetected(listener: (Gesture) -> Unit) {
        onGestureDetected = listener
    }

    fun getState(): FaceState = state

    fun updateState(newState: FaceState) {
        state = newState
    }

    fun isInitialized(): Boolean = landmarker != null

    fun initialize(): Boolean {
        return try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task")
                        .build()
                )
                .setOutputFaceBlendshapes(true)
                .setNumFaces(1)
                .build()
            landmarker = FaceLandmarker.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun processImageProxy(imageProxy: ImageProxy) {
        val currentLandmarker = landmarker
        if (currentLandmarker == null) {
            Log.w(TAG, "landmarker is null, skipping frame")
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = currentLandmarker.detectForVideo(mpImage, SystemClock.elapsedRealtime())

            var lm: List<NormalizedLandmark>? = null
            try {
                val allLandmarks = result.faceLandmarks()
                if (allLandmarks != null && allLandmarks.isNotEmpty()) {
                    lm = allLandmarks[0]
                }
            } catch (e: Exception) {
                Log.w(TAG, "faceLandmarks access failed: ${e.message}")
            }

            var scores = GestureScores()
            try {
                val blendshapesOpt = result.faceBlendshapes()
                if (blendshapesOpt != null && blendshapesOpt.isPresent) {
                    val bsList = blendshapesOpt.get()
                    if (bsList.isNotEmpty()) {
                        val categories = bsList[0]
                        val gesture = processBlendshapes(categories)

                        fpsFrameCount++
                        val now = System.currentTimeMillis()
                        val fps = if (now - fpsLastTime >= 1000) {
                            val f = fpsFrameCount
                            fpsFrameCount = 0
                            fpsLastTime = now
                            f
                        } else {
                            _debugInfoFlow.value.fps
                        }

                        scores = _debugInfoFlow.value.scores
                        _debugInfoFlow.value = FaceDebugInfo(fps = fps, scores = scores, landmarks = lm)

                        if (gesture != Gesture.NONE) {
                            val gestureNow = System.currentTimeMillis()
                            if (gestureNow - lastActionTime >= state.cooldownMs) {
                                lastActionTime = gestureNow
                                Log.d(TAG, "Gesture detected: $gesture")
                                onGestureDetected?.invoke(gesture)
                            }
                        }
                    } else {
                        Log.w(TAG, "No blendshapes in result")
                    }
                } else {
                    Log.w(TAG, "blendshapes is null or empty")
                }
            } catch (e: Exception) {
                Log.e(TAG, "blendshapes error: ${e.message}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val yuvImage = YuvImage(bytes, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        val jpegBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            matrix.preScale(-1f, 1f)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            val matrix = Matrix()
            matrix.preScale(-1f, 1f)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }

    private fun processBlendshapes(categories: List<com.google.mediapipe.tasks.components.containers.Category>): Gesture {
        val eBL = emaAR("eBL", getScore(categories, "eyeBlinkLeft"), true)
        val eBR = emaAR("eBR", getScore(categories, "eyeBlinkRight"), true)
        val avg = (eBL + eBR) / 2f
        val diff = kotlin.math.abs(eBL - eBR)

        val mP = emaAR("mP", getScore(categories, "mouthPucker"), false)
        val mF = emaAR("mF", getScore(categories, "mouthFunnel"), false)
        val mL = emaAR("mL", getScore(categories, "mouthLeft"), false)
        val mR = emaAR("mR", getScore(categories, "mouthRight"), false)

        val isBlink = hyst("bl", avg, state.thresholds.blink) && diff < state.thresholds.winkDiff
        val isLWink = !isBlink && hyst("wl", eBL, state.thresholds.blink) && diff >= state.thresholds.winkDiff
        val isRWink = !isBlink && hyst("wr", eBR, state.thresholds.blink) && diff >= state.thresholds.winkDiff

        val puck = maxOf(mP, mF * 0.7f)
        val biasL = mL - mR
        val biasR = mR - mL
        val isLPuck = hyst("lp", puck, state.thresholds.pucker) && biasL >= state.thresholds.puckerBias
        val isRPuck = hyst("rp", puck, state.thresholds.pucker) && biasR >= state.thresholds.puckerBias

        val cWL = if (isLWink) (diff / 0.5f).coerceIn(0f, 1f) else 0f
        val cWR = if (isRWink) (diff / 0.5f).coerceIn(0f, 1f) else 0f
        val cPL = if (isLPuck) ((puck * biasL.coerceAtLeast(0f)) / 0.25f).coerceIn(0f, 1f) else 0f
        val cPR = if (isRPuck) ((puck * biasR.coerceAtLeast(0f)) / 0.25f).coerceIn(0f, 1f) else 0f

        _debugInfoFlow.value = _debugInfoFlow.value.copy(scores = GestureScores(cWL, cWR, cPL, cPR))

        val allowWink = state.triggerMode != TriggerMode.PUCKER
        val allowPucker = state.triggerMode != TriggerMode.WINK

        return when {
            allowWink && isRWink -> Gesture.RIGHT_WINK
            allowWink && isLWink -> Gesture.LEFT_WINK
            allowPucker && isLPuck -> Gesture.LEFT_PUCKER
            allowPucker && isRPuck -> Gesture.RIGHT_PUCKER
            else -> Gesture.NONE
        }
    }

    private fun getScore(categories: List<com.google.mediapipe.tasks.components.containers.Category>, name: String): Float {
        return categories.find { it.categoryName() == name }?.score() ?: 0f
    }

    private fun emaAR(key: String, raw: Float, isEye: Boolean): Float {
        val current = smoothState[key] ?: raw
        val f = if (raw > current) {
            if (isEye) eyeAttack else mouthAttack
        } else {
            if (isEye) eyeRelease else mouthRelease
        }
        val smoothed = current * (1f - f) + raw * f
        smoothState[key] = smoothed
        return smoothed
    }

    private fun hyst(key: String, raw: Float, threshold: Float): Boolean {
        val current = activeState[key] ?: false
        return if (current) {
            if (raw < threshold * hystDeact) {
                activeState[key] = false
                false
            } else {
                true
            }
        } else {
            if (raw >= threshold * hystAttack) {
                activeState[key] = true
                true
            } else {
                false
            }
        }
    }

    fun release() {
        landmarker?.close()
        landmarker = null
        smoothState.clear()
        activeState.clear()
    }
}
