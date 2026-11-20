package com.campus.meshtalk

import android.app.Application
import android.content.Context
import android.provider.Settings
import com.campus.meshtalk.crypto.CryptoEngine
import com.campus.meshtalk.data.local.MeshTalkDatabase
import com.campus.meshtalk.data.repository.ContactRepository
import com.campus.meshtalk.data.repository.IdentityRepository
import com.campus.meshtalk.data.repository.ProfileRepository
import com.campus.meshtalk.mesh.MeshRouter
import com.campus.meshtalk.mesh.NearbyMeshManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class MeshTalkApp : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    lateinit var database: MeshTalkDatabase
        private set

    lateinit var nearbyManager: NearbyMeshManager
        private set

    lateinit var meshRouter: MeshRouter
        private set

    lateinit var deviceId: String
        private set

    lateinit var keyPair: KeyPair
        private set

    val profileRepository by lazy { ProfileRepository(this) }

    val identityRepository by lazy { IdentityRepository(this) }

    val batteryPrefsRepo by lazy { com.campus.meshtalk.data.preferences.MeshBatteryPreferencesRepository(this) }

    lateinit var contactRepository: ContactRepository
        private set

    lateinit var contactSignaling: com.campus.meshtalk.mesh.ContactSignaling
        private set

    lateinit var nearbyPeopleRepository: com.campus.meshtalk.data.repository.NearbyPeopleRepository
        private set

    lateinit var meshTalkId: String
        private set

    var nickname: String = ""
    var userStatus: String = ""
    var avatarUri: String? = null
        private set

    val isFirstLaunch: Boolean
        get() {
            val prefs = getSharedPreferences("meshtalk_prefs", Context.MODE_PRIVATE)
            return !prefs.getBoolean("onboarding_complete", false)
        }

    val isProfileSetup: Boolean
        get() {
            val prefs = getSharedPreferences("meshtalk_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("profile_setup", false)
        }

    override fun onCreate() {
        super.onCreate()

        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: java.util.UUID.randomUUID().toString().take(8)

        // Load profile first to get nickname/identity info
        loadProfile()

        // Initialize identity keys - this sets keyPair
        initIdentityKeys()

        // CRITICAL FIX: Validate keyPair before proceeding with mesh setup
        // If keyPair is null or invalid, regenerate it immediately
        if (keyPair == null || keyPair.public == null) {
            // Force key regeneration by clearing old preferences first
            val prefs = getSharedPreferences("meshtalk_identity", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            initIdentityKeys()
        }

        database = MeshTalkDatabase.getInstance(this)
        nearbyManager = NearbyMeshManager(this)

        // Only compute meshTalkId after keyPair is confirmed valid
        if (keyPair != null && keyPair.public != null) {
            meshTalkId = CryptoEngine.deriveHumanId(keyPair.public.encoded)
        } else {
            // Fallback: generate deterministic ID from device ID if keys failed
            meshTalkId = "device-${deviceId}"
        }
        nearbyManager.setLocalIdentity(meshTalkId, nickname)

        meshRouter = MeshRouter(
            context = this,
            myMeshId = meshTalkId,
            myNickname = nickname,
            keyPair = keyPair,
            nearbyManager = nearbyManager,
            messageDao = database.messageDao(),
            seenPacketDao = database.seenPacketDao(),
            queuedMessageDao = database.queuedMessageDao(),
            relayPacketDao = database.relayPacketDao(),
            contactDao = database.contactDao(),
            blockedUserDao = database.blockedUserDao(),
            connectionRequestDao = database.connectionRequestDao(),
            batteryPrefsRepo = batteryPrefsRepo,
            scope = appScope
        )

        contactSignaling = com.campus.meshtalk.mesh.ContactSignaling(
            context = this,
            myMeshId = meshTalkId,
            myNickname = nickname,
            keyPair = keyPair,
            nearbyManager = nearbyManager,
            contactDao = database.contactDao(),
            connectionRequestDao = database.connectionRequestDao(),
            blockedUserDao = database.blockedUserDao(),
            signalingOutboxDao = database.signalingOutboxDao(),
            batteryPrefsRepo = batteryPrefsRepo,
            scope = appScope
        )

        nearbyManager.addOnPeerConnectedListener { peerMtId ->
            contactSignaling.onPeerConnected(peerMtId)
        }

        meshRouter.contactSignaling = contactSignaling

        contactRepository = ContactRepository(
            contactDao = database.contactDao(),
            connectionRequestDao = database.connectionRequestDao(),
            myMeshId = meshTalkId,
            myDeviceId = deviceId,
            myNickname = nickname,
            myAvatarUri = avatarUri,
            keyPair = keyPair,
            contactSignaling = contactSignaling
        )

        nearbyPeopleRepository = com.campus.meshtalk.data.repository.NearbyPeopleRepository(
            myMeshId = meshTalkId,
            nearbyManager = nearbyManager,
            contactDao = database.contactDao(),
            connectionRequestDao = database.connectionRequestDao(),
            blockedUserDao = database.blockedUserDao(),
            contactSignaling = contactSignaling,
            contactRepository = contactRepository,
            meshRouter = meshRouter,
            scope = appScope
        )
    }

    private fun loadProfile() {
        val prefs = getSharedPreferences("meshtalk_prefs", Context.MODE_PRIVATE)
        // Use stored nickname, or device model as fallback, or generate from key if available
        val storedNickname = prefs.getString("nickname", null)
        if (storedNickname != null && storedNickname.isNotEmpty() && storedNickname != android.os.Build.MODEL) {
            nickname = storedNickname
        } else {
            // Use stored status if available, otherwise empty string
            val storedStatus = prefs.getString("user_status", null)
            userStatus = storedStatus ?: ""
            // Nickname not properly set, this will be handled during profile setup
            // The keyPair should have been generated by this point
            nickname = "MeshUser-${deviceId.substring(0, 4).uppercase()}"
        }
        avatarUri = prefs.getString("avatar_uri", null)
    }

    fun saveProfile(newNickname: String, newStatus: String, newAvatarUri: String? = avatarUri) {
        nickname = newNickname
        userStatus = newStatus
        avatarUri = newAvatarUri
        getSharedPreferences("meshtalk_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("nickname", newNickname)
            .putString("user_status", newStatus)
            .putString("avatar_uri", newAvatarUri)
            .putBoolean("profile_setup", true)
            .apply()

        nearbyManager.setLocalIdentity(meshTalkId, newNickname)

        appScope.launch {
            profileRepository.save(
                com.campus.meshtalk.data.repository.UserProfile(
                    id = meshTalkId,
                    displayName = newNickname,
                    avatarPath = newAvatarUri,
                    statusText = newStatus,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun initIdentityKeys() {
        val prefs = getSharedPreferences("meshtalk_identity", Context.MODE_PRIVATE)
        val encryptedPriv = prefs.getString("enc_private_key", null)
        val legacyPriv = prefs.getString("private_key", null)
        val pubBase64 = prefs.getString("public_key", null)

        // Case 1: We have both public key and encrypted private key - try to restore
        if (pubBase64 != null && encryptedPriv != null) {
            try {
                val privBytes = CryptoEngine.decryptPrivateKeyAtRest(encryptedPriv)
                val kf = KeyFactory.getInstance("EC")
                val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = kf.generatePublic(X509EncodedKeySpec(CryptoEngine.base64ToBytes(pubBase64)))
                keyPair = KeyPair(pubKey, privKey)
                // Verify the key pair is valid by checking it can be used
                if (keyPair.public != null) {
                    return // Successfully restored
                }
            } catch (e: Exception) {
                // Key restoration failed, fall through to regeneration
                // Log error for debugging
                android.util.Log.w("MeshTalkApp", "Failed to restore identity keys, regenerating", e)
            }
        }

        // Case 2: We have legacy unencrypted private key - migrate to encrypted
        if (legacyPriv != null && pubBase64 != null) {
            try {
                val privBytes = CryptoEngine.base64ToBytes(legacyPriv)
                val kf = KeyFactory.getInstance("EC")
                val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = kf.generatePublic(X509EncodedKeySpec(CryptoEngine.base64ToBytes(pubBase64)))
                keyPair = KeyPair(pubKey, privKey)

                // Migrate to encrypted storage
                val encPriv = CryptoEngine.encryptPrivateKeyAtRest(privBytes)
                prefs.edit()
                    .putString("enc_private_key", encPriv)
                    .remove("private_key")
                    .apply()
                return
            } catch (e: Exception) {
                android.util.Log.w("MeshTalkApp", "Failed to migrate legacy keys, regenerating", e)
            }
        }

        // Case 3: No valid keys found - generate new key pair
        val newPair = CryptoEngine.generateKeyPair()
        val encPriv = CryptoEngine.encryptPrivateKeyAtRest(newPair.private.encoded)
        keyPair = newPair

        prefs.edit()
            .putString("enc_private_key", encPriv)
            .putString("public_key", CryptoEngine.keyToBase64(newPair.public.encoded))
            .remove("private_key")
            .apply()
    }

    fun resetIdentityAndData() {
        // Stop mesh
        nearbyManager.stopAll()

        // Clear Room database
        database.clearAllTables()

        // Clear shared preferences
        getSharedPreferences("meshtalk_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("meshtalk_identity", Context.MODE_PRIVATE).edit().clear().apply()

        // Regenerate new keypair and reinit
        initIdentityKeys()
        loadProfile()

        meshTalkId = CryptoEngine.deriveHumanId(keyPair.public.encoded)
        nearbyManager.setLocalIdentity(meshTalkId, nickname)

        meshRouter = MeshRouter(
            context = this,
            myMeshId = meshTalkId,
            myNickname = nickname,
            keyPair = keyPair,
            nearbyManager = nearbyManager,
            messageDao = database.messageDao(),
            seenPacketDao = database.seenPacketDao(),
            queuedMessageDao = database.queuedMessageDao(),
            relayPacketDao = database.relayPacketDao(),
            contactDao = database.contactDao(),
            blockedUserDao = database.blockedUserDao(),
            connectionRequestDao = database.connectionRequestDao(),
            batteryPrefsRepo = batteryPrefsRepo,
            scope = appScope
        )

        contactSignaling = com.campus.meshtalk.mesh.ContactSignaling(
            context = this,
            myMeshId = meshTalkId,
            myNickname = nickname,
            keyPair = keyPair,
            nearbyManager = nearbyManager,
            contactDao = database.contactDao(),
            connectionRequestDao = database.connectionRequestDao(),
            blockedUserDao = database.blockedUserDao(),
            signalingOutboxDao = database.signalingOutboxDao(),
            batteryPrefsRepo = batteryPrefsRepo,
            scope = appScope
        )

        nearbyManager.addOnPeerConnectedListener { peerMtId ->
            contactSignaling.onPeerConnected(peerMtId)
        }

        meshRouter.contactSignaling = contactSignaling

        contactRepository = ContactRepository(
            contactDao = database.contactDao(),
            connectionRequestDao = database.connectionRequestDao(),
            myMeshId = meshTalkId,
            myDeviceId = deviceId,
            myNickname = nickname,
            myAvatarUri = avatarUri,
            keyPair = keyPair,
            contactSignaling = contactSignaling
        )

        nearbyPeopleRepository = com.campus.meshtalk.data.repository.NearbyPeopleRepository(
            myMeshId = meshTalkId,
            nearbyManager = nearbyManager,
            contactDao = database.contactDao(),
            connectionRequestDao = database.connectionRequestDao(),
            blockedUserDao = database.blockedUserDao(),
            contactSignaling = contactSignaling,
            contactRepository = contactRepository,
            meshRouter = meshRouter,
            scope = appScope
        )

        nearbyManager.restartMesh()
    }
}
