package com.campus.meshtalk.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.campus.meshtalk.crypto.CryptoEngine
import com.campus.meshtalk.data.preferences.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyPair

class IdentityRepository(private val context: Context) {
    private val identityIdKey = stringPreferencesKey("identity_mesh_talk_id")

    val meshTalkId: Flow<String?> = context.dataStore.data.map { it[identityIdKey] }

    suspend fun getOrCreateId(keyPair: KeyPair): String {
        val generatedId = CryptoEngine.deriveHumanId(keyPair.public.encoded)
        var identityId = generatedId
        context.dataStore.edit { preferences ->
            identityId = preferences[identityIdKey] ?: generatedId
            preferences[identityIdKey] = identityId
        }
        return identityId
    }
}
