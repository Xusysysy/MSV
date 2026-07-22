package com.music.msv.facer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.faceDataStore by preferencesDataStore(name = "face_recognition")

class FaceRecognitionRepository(private val context: Context) {

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_SHOW_MESH = booleanPreferencesKey("show_mesh")
        private val KEY_TRIGGER_MODE = stringPreferencesKey("trigger_mode")
        private val KEY_BLINK_THRESHOLD = floatPreferencesKey("blink_threshold")
        private val KEY_WINK_DIFF = floatPreferencesKey("wink_diff")
        private val KEY_PUCKER_THRESHOLD = floatPreferencesKey("pucker_threshold")
        private val KEY_PUCKER_BIAS = floatPreferencesKey("pucker_bias")
        private val KEY_COOLDOWN_MS = intPreferencesKey("cooldown_ms")
    }

    data class FacePrefs(
        val enabled: Boolean = true,
        val showMesh: Boolean = true,
        val triggerMode: String = "BOTH",
        val blinkThreshold: Float = 0.35f,
        val winkDiff: Float = 0.14f,
        val puckerThreshold: Float = 0.25f,
        val puckerBias: Float = 0.21f,
        val cooldownMs: Int = 800
    )

    val prefsFlow: Flow<FacePrefs> = context.faceDataStore.data.map { prefs ->
        FacePrefs(
            enabled = prefs[KEY_ENABLED] ?: true,
            showMesh = prefs[KEY_SHOW_MESH] ?: true,
            triggerMode = prefs[KEY_TRIGGER_MODE] ?: "BOTH",
            blinkThreshold = prefs[KEY_BLINK_THRESHOLD] ?: 0.35f,
            winkDiff = prefs[KEY_WINK_DIFF] ?: 0.14f,
            puckerThreshold = prefs[KEY_PUCKER_THRESHOLD] ?: 0.25f,
            puckerBias = prefs[KEY_PUCKER_BIAS] ?: 0.21f,
            cooldownMs = prefs[KEY_COOLDOWN_MS] ?: 800
        )
    }

    suspend fun savePrefs(prefs: FacePrefs) {
        context.faceDataStore.edit { store ->
            store[KEY_ENABLED] = prefs.enabled
            store[KEY_SHOW_MESH] = prefs.showMesh
            store[KEY_TRIGGER_MODE] = prefs.triggerMode
            store[KEY_BLINK_THRESHOLD] = prefs.blinkThreshold
            store[KEY_WINK_DIFF] = prefs.winkDiff
            store[KEY_PUCKER_THRESHOLD] = prefs.puckerThreshold
            store[KEY_PUCKER_BIAS] = prefs.puckerBias
            store[KEY_COOLDOWN_MS] = prefs.cooldownMs
        }
    }
}
