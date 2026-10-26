package com.neonkaiju.kot.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

// Settings du joueur save entre deux lancements de l'application.
data class Settings(
    // Bots fast : 400ms
    val fastBots: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setFastBots(enabled: Boolean)
}

/**
 * Settings stockés avec DataStore.
 *
 * DataStore n'accepte qu'une instance par fichier dans un même processus : ce repository doit être
 * un singleton de l'application.
 */
class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<Settings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> Settings(fastBots = prefs[FAST_BOTS] ?: false) }

    override suspend fun setFastBots(enabled: Boolean) {
        dataStore.edit { it[FAST_BOTS] = enabled }
    }

    companion object {
        private val FAST_BOTS = booleanPreferencesKey("fast_bots")

        // file doit se terminer par .preferences_pb
        fun create(
            file: File,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    }
}
