package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LLMResponse(
    val data: List<LLMRaw>
)

@Serializable
data class LLMRaw(
    val id: String,
    var name: String? = null,
    var avatarUrl: String? = null,
    val pricing: Pricing? = null,
    @SerialName("owned_by") var ownedBy: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val tokenizer: String? = null
)

@Serializable
data class Pricing(
    val prompt: Double? = null,
    val completion: Double? = null,
)