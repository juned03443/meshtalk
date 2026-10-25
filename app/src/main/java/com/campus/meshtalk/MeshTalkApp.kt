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

        loadProfile()
        initIdentityKeys()

        database = MeshTalkDatabase.getInstance(this)
        nearbyManager = NearbyMeshManager(this)

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
    }

    private fun loadProfile() {
        val prefs = getSharedPreferences("meshtalk_prefs", Context.MODE_PRIVATE)
        nickname = prefs.getString("nickname", android.os.Build.MODEL) ?: android.os.Build.MODEL
        userStatus = prefs.getString("user_status", "") ?: ""
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
    }

    private fun initIdentityKeys() {
        val prefs = getSharedPreferences("meshtalk_identity", Context.MODE_PRIVATE)
        val encryptedPriv = prefs.getString("enc_private_key", null)
        val legacyPriv = prefs.getString("private_key", null)
        val pubBase64 = prefs.getString("public_key", null)

        if (pubBase64 != null && (encryptedPriv != null || legacyPriv != null)) {
            try {
                val privBytes = if (encryptedPriv != null) {
                    CryptoEngine.decryptPrivateKeyAtRest(encryptedPriv)
                } else {
                    CryptoEngine.base64ToBytes(legacyPriv!!)
                }
                val kf = KeyFactory.getInstance("EC")
                val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = kf.generatePublic(X509EncodedKeySpec(CryptoEngine.base64ToBytes(pubBase64)))
                keyPair = KeyPair(pubKey, privKey)

                // Migrate legacy unencrypted private key to encrypted at rest
                if (encryptedPriv == null) {
                    val newEncrypted = CryptoEngine.encryptPrivateKeyAtRest(privBytes)
                    prefs.edit()
                        .putString("enc_private_key", newEncrypted)
                        .remove("private_key")
                        .apply()
                }
                return
            } catch (_: Exception) {
                // fall through to regenerate
            }
        }

        val newPair = CryptoEngine.generateKeyPair()
        val encPriv = CryptoEngine.encryptPrivateKeyAtRest(newPair.private.encoded)
        prefs.edit()
            .putString("enc_private_key", encPriv)
            .putString("public_key", CryptoEngine.keyToBase64(newPair.public.encoded))
            .remove("private_key")
            .apply()

        keyPair = newPair
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
