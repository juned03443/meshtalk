package com.campus.meshtalk.mesh

object EndpointNameHelper {
    private const val MAX_DISPLAY_NAME_CHARS = 20
    private const val MAX_TOTAL_BYTES = 99 // under 100 bytes

    /**
     * Compact "MT-XXXX-XXXX|DisplayName"
     */
    fun format(meshId: String, rawName: String): String {
        val sanitizedName = rawName.replace("|", "").trim().take(MAX_DISPLAY_NAME_CHARS)
        var formatted = "\$meshId|\$sanitizedName"
        while (formatted.toByteArray(Charsets.UTF_8).size > MAX_TOTAL_BYTES && formatted.length > meshId.length + 1) {
            formatted = formatted.dropLast(1)
        }
        return formatted
    }

    data class ParsedEndpoint(val meshId: String, val displayName: String)

    fun parse(endpointName: String?): ParsedEndpoint? {
        if (endpointName.isNullOrBlank()) return null
        val parts = endpointName.split("|")
        if (parts.size != 2) return null
        val meshId = parts[0].trim().uppercase()
        val displayName = parts[1].trim()
        if (!com.campus.meshtalk.crypto.CryptoEngine.isValidMeshTalkId(meshId)) return null
        return ParsedEndpoint(meshId, displayName.ifBlank { meshId })
    }
}
