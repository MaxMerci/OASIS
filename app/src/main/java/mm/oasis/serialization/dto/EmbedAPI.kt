package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmbedRequest(
    val model: String = "multilingual-e5-small", //"embeddinggemma-300m", ну типа, сами решайте, тяжело
    val input: List<String>
)

@Serializable
data class EmbedResp(
    val data: List<EmbedData> = listOf(EmbedData(0, emptyList())),
    val usage: EmbedUsage = EmbedUsage(0, 0)
)

@Serializable
data class EmbedData(
    val index: Int,
    val embedding: List<Double>
)

@Serializable
data class EmbedUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)