package com.campus.meshtalk.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class BleScanMode {
    LOW_POWER,
    BALANCED,
    HIGH_PERFORMANCE
}

enum class MediaAutoDownload {
    NEVER,
    CONTACTS_ONLY,
    ALWAYS
}

class MeshBatteryPreferencesRepository(private val context: Context) {

    private val scanModeKey = stringPreferencesKey("mesh_scan_mode")
    private val relayForOthersKey = booleanPreferencesKey("mesh_relay_for_others")
    private val relayOnlyWhileChargingKey = booleanPreferencesKey("mesh_relay_only_charging")
    private val autoDownloadKey = stringPreferencesKey("mesh_auto_download_media")
    private val dataSaverKey = booleanPreferencesKey("mesh_data_saver")
    private val maxMediaSizeBytesKey = intPreferencesKey("mesh_max_media_size_bytes")
    private val keepMeshInBackgroundKey = booleanPreferencesKey("mesh_keep_in_background")

    val scanModeFlow: Flow<BleScanMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[scanModeKey] ?: BleScanMode.BALANCED.name
        try { BleScanMode.valueOf(raw) } catch (_: Exception) { BleScanMode.BALANCED }
    }

    val relayForOthersFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[relayForOthersKey] ?: true
    }

    val relayOnlyWhileChargingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[relayOnlyWhileChargingKey] ?: false
    }

    val autoDownloadFlow: Flow<MediaAutoDownload> = context.dataStore.data.map { prefs ->
        val raw = prefs[autoDownloadKey] ?: MediaAutoDownload.CONTACTS_ONLY.name
        try { MediaAutoDownload.valueOf(raw) } catch (_: Exception) { MediaAutoDownload.CONTACTS_ONLY }
    }

    val dataSaverFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dataSaverKey] ?: false
    }

    val keepMeshInBackgroundFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keepMeshInBackgroundKey] ?: true
    }

    suspend fun setScanMode(mode: BleScanMode) {
        context.dataStore.edit { it[scanModeKey] = mode.name }
    }

    suspend fun setRelayForOthers(enabled: Boolean) {
        context.dataStore.edit { it[relayForOthersKey] = enabled }
    }

    suspend fun setRelayOnlyWhileCharging(enabled: Boolean) {
        context.dataStore.edit { it[relayOnlyWhileChargingKey] = enabled }
    }

    suspend fun setAutoDownload(mode: MediaAutoDownload) {
        context.dataStore.edit { it[autoDownloadKey] = mode.name }
    }

    suspend fun setDataSaver(enabled: Boolean) {
        context.dataStore.edit { it[dataSaverKey] = enabled }
    }

    suspend fun setKeepMeshInBackground(enabled: Boolean) {
        context.dataStore.edit { it[keepMeshInBackgroundKey] = enabled }
    }
}
