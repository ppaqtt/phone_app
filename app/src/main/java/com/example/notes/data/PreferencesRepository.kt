package com.example.notes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notes_preferences")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        private val KEY_SYNC_URL = stringPreferencesKey("sync_url")
        private val KEY_LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
        private val KEY_SYNC_INTERVAL = stringPreferencesKey("sync_interval")
    }

    val syncEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SYNC_ENABLED] ?: false
        }

    val syncUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SYNC_URL] ?: "https://api.notes.example.com/"
        }

    val lastSyncTime: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_LAST_SYNC_TIME] ?: ""
        }

    val syncInterval: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SYNC_INTERVAL] ?: "30"
        }

    suspend fun setSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSyncUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYNC_URL] = url
        }
    }

    suspend fun setLastSyncTime(time: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_SYNC_TIME] = time
        }
    }

    suspend fun setSyncInterval(interval: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYNC_INTERVAL] = interval
        }
    }
}
