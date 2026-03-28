package mm.oasis.serialization.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class ChatCompletionChunk(
    val id: String,
    val choices: List<ChunkChoice>
) {
    @Serializable
    data class ChunkChoice(
        val index: Int,
        val delta: Delta,
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
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
)

@Serializable
data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null
)