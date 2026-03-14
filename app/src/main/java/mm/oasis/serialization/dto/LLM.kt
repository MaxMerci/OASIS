package mm.oasis.serialization.dto

import kotlinx.serialization.Serializable

@Serializable
data class LLMResponse(
    val data: List<LLMRaw>
)

@Serializable
data class LLMRaw(
    val id: String,
    val avatar: String? = null,
    val pricing: Pricing? = null,
    val ownedBy: String? = null,
)

@Serializable
data class Pricing(
    val input: Double? = null,
    val output: Double? = null
)
