package com.campus.meshtalk.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.campus.meshtalk.data.preferences.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserProfile(
    val id: String,
    val displayName: String,
    val avatarPath: String?,
    val statusText: String,
    val createdAt: Long
)

class ProfileRepository(private val context: Context) {
    private val profileSetupKey = booleanPreferencesKey("profile_setup")
    private val idKey = stringPreferencesKey("profile_id")
    private val displayNameKey = stringPreferencesKey("profile_display_name")
    private val avatarPathKey = stringPreferencesKey("profile_avatar_path")
    private val statusTextKey = stringPreferencesKey("profile_status_text")
    private val createdAtKey = longPreferencesKey("profile_created_at")

    val isProfileSetup: Flow<Boolean> = context.dataStore.data.map { it[profileSetupKey] ?: false }

    val profile: Flow<UserProfile?> = context.dataStore.data.map { preferences ->
        val id = preferences[idKey] ?: return@map null
        val displayName = preferences[displayNameKey] ?: return@map null
        UserProfile(
            id = id,
            displayName = displayName,
            avatarPath = preferences[avatarPathKey],
            statusText = preferences[statusTextKey] ?: "",
            createdAt = preferences[createdAtKey] ?: 0L
        )
    }

    suspend fun save(profile: UserProfile) {
        context.dataStore.edit { preferences ->
            preferences[idKey] = profile.id
            preferences[displayNameKey] = profile.displayName
            if (profile.avatarPath.isNullOrBlank()) preferences.remove(avatarPathKey)
            else preferences[avatarPathKey] = profile.avatarPath
            preferences[statusTextKey] = profile.statusText
            preferences[createdAtKey] = profile.createdAt
            preferences[profileSetupKey] = true
        }
    }
}
