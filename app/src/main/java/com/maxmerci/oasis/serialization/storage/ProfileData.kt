package com.maxmerci.oasis.serialization.storage

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class ProfileData(
    val apiKey: String,
    val endPoint: String,
    var model: String = "gpt-4o",
) {
    fun endpointDomain(): String? {
        return try {
            val uri = URI(endPoint)
            val domain = uri.host
            if (domain?.startsWith("www.") == true) domain.substring(4) else domain
        } catch (e: Exception) {
            "YOU"
        }
    }
}