package com.campus.meshtalk.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    private const val ALGORITHM_EC = "EC"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val SIGNATURE_ALGO = "SHA256withECDSA"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "MeshTalkMasterKey_v1"

    // Base32 alphabet without ambiguous chars (0/O, 1/I, 8/B)
    private const val BASE32_ALPHABET = "2345679ACDEFGHJKLMNPQRSTUVWXYZ"

    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance(ALGORITHM_EC)
        keyGen.initialize(256)
        return keyGen.generateKeyPair()
    }

    fun keyToBase64(keyBytes: ByteArray): String {
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    fun base64ToBytes(base64Str: String): ByteArray {
        return Base64.decode(base64Str, Base64.NO_WRAP)
    }

    fun deriveHumanId(publicKeyBytes: ByteArray): String {
        val sha = MessageDigest.getInstance("SHA-256")
        val hash = sha.digest(publicKeyBytes)
        val sb = StringBuilder()
        for (i in 0 until 8) {
            val idx = (hash[i].toInt() and 0xFF) % BASE32_ALPHABET.length
            sb.append(BASE32_ALPHABET[idx])
        }
        return "MT-${sb.substring(0, 4)}-${sb.substring(4, 8)}"
    }

    fun isValidMeshTalkId(id: String): Boolean {
        val regex = Regex("^MT-[${BASE32_ALPHABET}]{4}-[${BASE32_ALPHABET}]{4}$")
        return regex.matches(id.uppercase())
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptPrivateKeyAtRest(privateKeyBytes: ByteArray): String {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(privateKeyBytes)
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptPrivateKeyAtRest(encryptedBase64: String): ByteArray {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        return cipher.doFinal(encrypted)
    }

    fun signData(privateKey: PrivateKey, data: ByteArray): String {
        val signer = Signature.getInstance(SIGNATURE_ALGO)
        signer.initSign(privateKey)
        signer.update(data)
        val sig = signer.sign()
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    fun verifySignature(publicKeyBytes: ByteArray, data: ByteArray, signatureBase64: String): Boolean {
        return try {
            val kf = KeyFactory.getInstance(ALGORITHM_EC)
            val pubKey = kf.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val verifier = Signature.getInstance(SIGNATURE_ALGO)
            verifier.initVerify(pubKey)
            verifier.update(data)
            verifier.verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
        } catch (_: Exception) {
            false
        }
    }

    fun deriveSharedKey(myPrivateKey: PrivateKey, peerPublicKeyBytes: ByteArray): SecretKeySpec {
        val kf = KeyFactory.getInstance(ALGORITHM_EC)
        val keySpec = X509EncodedKeySpec(peerPublicKeyBytes)
        val peerPubKey = kf.generatePublic(keySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        val sha256 = MessageDigest.getInstance("SHA-256")
        val derivedKeyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    data class EncryptedResult(
        val ciphertext: String,
        val nonce: String,
        val ephemeralPublicKey: String
    )

    fun encryptForPeer(peerPublicKeyBase64: String, plaintext: String): EncryptedResult {
        val ephemeralKeyPair = generateKeyPair()
        val peerPubKeyBytes = base64ToBytes(peerPublicKeyBase64)
        val secretKey = deriveSharedKey(ephemeralKeyPair.private, peerPubKeyBytes)

        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptedResult(
            ciphertext = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            nonce = Base64.encodeToString(iv, Base64.NO_WRAP),
            ephemeralPublicKey = keyToBase64(ephemeralKeyPair.public.encoded)
        )
    }

    fun decryptFromPeer(
        myPrivateKey: PrivateKey,
        ephemeralPublicKeyBase64: String,
        nonceBase64: String,
        ciphertextBase64: String
    ): String {
        val ephemeralPubKeyBytes = base64ToBytes(ephemeralPublicKeyBase64)
        val secretKey = deriveSharedKey(myPrivateKey, ephemeralPubKeyBytes)

        val iv = base64ToBytes(nonceBase64)
        val cipherBytes = base64ToBytes(ciphertextBase64)

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}
