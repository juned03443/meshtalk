package com.campus.meshtalk.mesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

@Serializable
data class HelloPayload(
    val meshId: String,
    val displayName: String,
    val publicKeyBase64: String,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = ""
) {
    fun canonicalString(): String = "\$meshId|\$displayName|\$publicKeyBase64|\$timestamp"

    fun toByteArray(): ByteArray = Json.encodeToString(this).toByteArray(StandardCharsets.UTF_8)

    companion object {
        fun fromByteArray(bytes: ByteArray): HelloPayload? {
            return try {
                Json.decodeFromString<HelloPayload>(String(bytes, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Serializable
data class ConnRequestPayload(
    val requestId: String,
    val fromMeshId: String,
    val toMeshId: String,
    val fromName: String,
    val fromPublicKey: String,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
    val hopCount: Int = 0,
    val hopLimit: Int = 4,
    val signature: String = ""
) {
    fun canonicalString(): String = "\$requestId|\$fromMeshId|\$toMeshId|\$fromName|\$fromPublicKey|\$timestamp|\$expiresAt|\$hopLimit"

    fun toByteArray(): ByteArray = Json.encodeToString(this).toByteArray(StandardCharsets.UTF_8)

    companion object {
        fun fromByteArray(bytes: ByteArray): ConnRequestPayload? {
            return try {
                Json.decodeFromString<ConnRequestPayload>(String(bytes, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Serializable
data class ConnResponsePayload(
    val requestId: String,
    val fromMeshId: String, // the responder
    val toMeshId: String,   // the original requester
    val responderName: String,
    val responderPublicKey: String = "", // present on accept, empty on decline
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = ""
) {
    fun canonicalString(): String = "\$requestId|\$fromMeshId|\$toMeshId|\$responderName|\$responderPublicKey|\$timestamp"

    fun toByteArray(): ByteArray = Json.encodeToString(this).toByteArray(StandardCharsets.UTF_8)

    companion object {
        fun fromByteArray(bytes: ByteArray): ConnResponsePayload? {
            return try {
                Json.decodeFromString<ConnResponsePayload>(String(bytes, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Serializable
data class ConnCancelPayload(
    val requestId: String,
    val fromMeshId: String,
    val toMeshId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = ""
) {
    fun canonicalString(): String = "\$requestId|\$fromMeshId|\$toMeshId|\$timestamp"

    fun toByteArray(): ByteArray = Json.encodeToString(this).toByteArray(StandardCharsets.UTF_8)

    companion object {
        fun fromByteArray(bytes: ByteArray): ConnCancelPayload? {
            return try {
                Json.decodeFromString<ConnCancelPayload>(String(bytes, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                null
            }
        }
    }
}
