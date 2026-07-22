package com.music.msv.facer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.faceStore by preferencesDataStore(name = "face_prefs")

class FaceRecognitionRepository(private val context: Context) {

    data class FacePrefs(
        val mirrored: Boolean = true,
        val blinkThreshold: Float = 0.35f,
        val puckerThreshold: Float = 0.25f,
        val puckerBiasL: Float = 0.21f,
        val puckerBiasR: Float = 0.21f,
        val actionThreshold: Float = 0.1f
    )

    companion object {
        private val K_MIRROR = booleanPreferencesKey("mirrored")
        private val K_BLINK = floatPreferencesKey("blink")
        private val K_PUCKER = floatPreferencesKey("pucker")
        private val K_BIAS_L = floatPreferencesKey("bias_l")
        private val K_BIAS_R = floatPreferencesKey("bias_r")
        private val K_ACTION = floatPreferencesKey("action")
    }

    val prefsFlow: Flow<FacePrefs> = context.faceStore.data.map { p ->
        FacePrefs(
            mirrored = p[K_MIRROR] ?: true,
            blinkThreshold = p[K_BLINK] ?: 0.35f,
            puckerThreshold = p[K_PUCKER] ?: 0.25f,
            puckerBiasL = p[K_BIAS_L] ?: 0.21f,
            puckerBiasR = p[K_BIAS_R] ?: 0.21f,
            actionThreshold = p[K_ACTION] ?: 0.1f
        )
    }

    suspend fun save(manager: FaceRecognitionManager) {
        val s = manager.currentState()
        context.faceStore.edit {
            it[K_MIRROR] = s.mirrored
            it[K_BLINK] = s.thresholds.blink
            it[K_PUCKER] = s.thresholds.pucker
            it[K_BIAS_L] = s.thresholds.puckerBiasL
            it[K_BIAS_R] = s.thresholds.puckerBiasR
            it[K_ACTION] = s.actionThreshold
        }
    }

    suspend fun load(manager: FaceRecognitionManager) {
        context.faceStore.data.first().let { p ->
            manager.updateState {
                it.copy(
                    mirrored = p[K_MIRROR] ?: true,
                    thresholds = it.thresholds.copy(
                        blink = p[K_BLINK] ?: 0.35f,
                        pucker = p[K_PUCKER] ?: 0.25f,
                        puckerBiasL = p[K_BIAS_L] ?: 0.21f,
                        puckerBiasR = p[K_BIAS_R] ?: 0.21f
                    ),
                    actionThreshold = p[K_ACTION] ?: 0.1f
                )
            }
        }
    }
}
