package com.campus.meshtalk.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshLogicTest {

    @Test
    fun testTieBreaker() {
        val idA = "MT-AAAA-1111"
        val idB = "MT-BBBB-2222"

        assertTrue(NearbyMeshManager.shouldInitiateConnection(idA, idB))
        assertFalse(NearbyMeshManager.shouldInitiateConnection(idB, idA))
        assertFalse(NearbyMeshManager.shouldInitiateConnection(idA, idA))
        assertFalse(NearbyMeshManager.shouldInitiateConnection("", idB))
    }

    @Test
    fun testExponentialBackoff() {
        assertEquals(2000L, NearbyMeshManager.calculateBackoff(0))
        assertEquals(2000L, NearbyMeshManager.calculateBackoff(1))
        assertEquals(4000L, NearbyMeshManager.calculateBackoff(2))
        assertEquals(8000L, NearbyMeshManager.calculateBackoff(3))
        assertEquals(16000L, NearbyMeshManager.calculateBackoff(4))
        assertEquals(30000L, NearbyMeshManager.calculateBackoff(5))
        assertEquals(30000L, NearbyMeshManager.calculateBackoff(10))
    }

    @Test
    fun testHelloPayloadSerialization() {
        val hello = HelloPayload(
            meshId = "MT-2345-6789",
            displayName = "Alice",
            publicKeyBase64 = "dGVzdGtleQ==",
            timestamp = 123456789L,
            signature = "c2ln"
        )
        val bytes = hello.toByteArray()
        val decoded = HelloPayload.fromByteArray(bytes)
        assertEquals(hello.meshId, decoded?.meshId)
        assertEquals(hello.displayName, decoded?.displayName)
        assertEquals(hello.publicKeyBase64, decoded?.publicKeyBase64)
        assertEquals(hello.timestamp, decoded?.timestamp)
        assertEquals(hello.signature, decoded?.signature)
    }

    @Test
    fun testConnectionRequestPayloadSerialization() {
        val cr = ConnRequestPayload(
            requestId = "req-1",
            fromMeshId = "MT-AAAA-1111",
            toMeshId = "MT-BBBB-2222",
            fromName = "Bob",
            fromPublicKey = "Ym9iX3B1Yg==",
            timestamp = 987654321L
        )
        val bytes = cr.toByteArray()
        val decoded = ConnRequestPayload.fromByteArray(bytes)
        assertEquals(cr.requestId, decoded?.requestId)
        assertEquals(cr.fromMeshId, decoded?.fromMeshId)
        assertEquals(cr.toMeshId, decoded?.toMeshId)
        assertEquals(cr.fromName, decoded?.fromName)
        assertEquals(cr.fromPublicKey, decoded?.fromPublicKey)
    }
}
