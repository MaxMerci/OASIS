package com.maxmerci.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    @Serializable
    data class Delta(
        val role: String? = null,
        val content: String? = null,
        val reasoning: String? = null,
        @SerialName("tool_calls") val toolCalls: List<ToolCallChunk>? = null
    )
}