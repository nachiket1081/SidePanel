package com.sidepanel.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sidepanel_settings")

data class PairingSettings(
    val host: String = "",
    val port: Int = 8765,
    val token: String = "",
    val autoConnect: Boolean = true
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && token.isNotBlank()
}

class PreferencesManager(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("paired_host")
        val PORT = intPreferencesKey("paired_port")
        val TOKEN = stringPreferencesKey("paired_token")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    }

    val pairingSettings: Flow<PairingSettings> = context.dataStore.data.map { prefs ->
        PairingSettings(
            host = prefs[Keys.HOST] ?: "",
            port = prefs[Keys.PORT] ?: 8765,
            token = prefs[Keys.TOKEN] ?: "",
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: true
        )
    }

    suspend fun savePairing(host: String, port: Int, token: String, autoConnect: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = host
            prefs[Keys.PORT] = port
            prefs[Keys.TOKEN] = token
            prefs[Keys.AUTO_CONNECT] = autoConnect
        }
    }

    suspend fun clearPairing() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.HOST)
            prefs.remove(Keys.PORT)
            prefs.remove(Keys.TOKEN)
        }
    }
}
