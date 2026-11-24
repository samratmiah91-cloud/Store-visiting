package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "storevisit_prefs")

class SessionPreferences(private val context: Context) {
    private object Keys {
        val LOGGED_IN_USER_ID = stringPreferencesKey("logged_in_user_id")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val OFFLINE_SIMULATION = booleanPreferencesKey("offline_simulation")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    }

    val loggedInUserId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOGGED_IN_USER_ID]?.takeIf { it.isNotBlank() }
    }

    val appLanguage: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        val code = prefs[Keys.APP_LANGUAGE]
        AppLanguage.entries.firstOrNull { it.code == code } ?: AppLanguage.ENGLISH
    }

    val offlineSimulationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.OFFLINE_SIMULATION] ?: false
    }

    val lastSyncedAt: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNCED_AT]
    }

    suspend fun setLoggedInUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId.isNullOrBlank()) {
                prefs.remove(Keys.LOGGED_IN_USER_ID)
            } else {
                prefs[Keys.LOGGED_IN_USER_ID] = userId
            }
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.code
        }
    }

    suspend fun setOfflineSimulation(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OFFLINE_SIMULATION] = enabled
        }
    }

    suspend fun setLastSyncedAt(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNCED_AT] = timestamp
        }
    }
}
