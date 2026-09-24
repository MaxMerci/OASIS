package mm.oasis.serialization.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

// Разные провайдеры шлют разный набор полей, поэтому почти все поля опциональны.
@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    val choices: List<ChunkChoice> = emptyList(),
    // некоторые API присылают ошибку прямо в потоке: data: {"error": {...}}
    val error: JsonElement? = null
) {
    @Serializable
    data class ChunkChoice(
        val index: Int = 0,
        val delta: Delta = Delta(),
        @SerialName("finish_reason") val finishReason: String? = null
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class Delta(
        val role: String? = null,
        val content: String? = null,
        @JsonNames("reasoning_content", "reasoning") val reasoning: String? = null,
        @SerialName("tool_calls") val toolCalls: List<ToolCallChunk>? = null
    )
}

@Serializable
data class ToolCallChunk(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
)

@Serializable
data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null
)
