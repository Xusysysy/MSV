package com.music.msv.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

class SessionRepository(private val context: Context) {

    companion object {
        private val KEY_MODE = stringPreferencesKey("mode")
        private val KEY_CURRENT_PAGE = stringPreferencesKey("current_page")
        private val KEY_URIS = stringPreferencesKey("uris")
        private val KEY_FILE_NAME = stringPreferencesKey("file_name")
    }

    data class SessionData(
        val mode: String,
        val currentPage: Int,
        val uris: List<String>,
        val fileName: String
    )

    val sessionFlow: Flow<SessionData?> = context.dataStore.data.map { prefs ->
        SessionData(
            mode = prefs[KEY_MODE] ?: return@map null,
            currentPage = prefs[KEY_CURRENT_PAGE]?.toIntOrNull() ?: 0,
            uris = prefs[KEY_URIS]?.split("|||") ?: return@map null,
            fileName = prefs[KEY_FILE_NAME] ?: ""
        )
    }

    suspend fun saveSession(
        mode: String,
        currentPage: Int,
        uris: List<String>,
        fileName: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MODE] = mode
            prefs[KEY_CURRENT_PAGE] = currentPage.toString()
            prefs[KEY_URIS] = uris.joinToString("|||")
            prefs[KEY_FILE_NAME] = fileName
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
