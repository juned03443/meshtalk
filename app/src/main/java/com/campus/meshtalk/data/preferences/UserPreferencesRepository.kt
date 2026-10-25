package com.campus.meshtalk.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_preferences")

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        when (preferences[KEY_THEME_MODE]) {
            ThemeMode.DARK.name -> ThemeMode.DARK
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }

    val reduceTransparencyFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_REDUCE_TRANSPARENCY] ?: false
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = themeMode.name
        }
    }

    suspend fun setReduceTransparency(reduce: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REDUCE_TRANSPARENCY] = reduce
        }
    }
}
