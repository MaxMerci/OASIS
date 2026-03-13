package com.maxmerci.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Message(
    val role: MessageRole,
    var content: String = "",
    var reasoning: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @Transient
    val name: String? = null,
    val avatarUrl: String? = null,
    @Transient
    val attachments: List<Attachment>? = null
) {
    @Serializable
    enum class MessageRole {
        @SerialName("system") SYSTEM,
        @SerialName("user") USER,
        @SerialName("assistant") ASSISTANT,
        @SerialName("tool") TOOL
    }

    @Serializable
    data class Attachment(
        val uri: String,
        val type: String, // "image", "file", etc.
        val name: String
    )
}
