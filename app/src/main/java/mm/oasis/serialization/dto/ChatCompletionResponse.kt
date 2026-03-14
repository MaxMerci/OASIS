package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage? = null
) {
    @Serializable
    data class Choice(
        val index: Int,
        val message: Message,
        @SerialName("finish_reason") val finishReason: String?
    )

    @Serializable
    data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int,
        @SerialName("completion_tokens") val completionTokens: Int,
        @SerialName("total_tokens") val totalTokens: Int
    )
}