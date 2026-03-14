package mm.oasis.serialization.storage

import mm.oasis.serialization.dto.LLMRaw
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class ProfileData(
    val apiKey: String,
    val endPoint: String,
    var model: LLMRaw? = null,
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