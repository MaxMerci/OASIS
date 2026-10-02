package mm.oasis.serialization.storage

import mm.oasis.serialization.dto.LLMRaw
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable
data class ProfileData(
    var apiKey: String,
    var endPoint: String,
    var model: LLMRaw? = null,
    val id: String = UUID.randomUUID().toString(),
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