package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Message(
    val role: MessageRole,
    var content: MessageContent? = null,
    var reasoning: String? = null,
    @SerialName("tool_calls") var toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
) {
    data class Flow(
        val content: String = "",
        val reasoning: String = "",
        val toolCalls: List<ToolCall> = emptyList()
    )

    fun forApi(): Message = Message(
        role = role,
        content = when (val c = content) {
            is MessageContent.Parts -> MessageContent.Parts(c.parts.map { it.withoutFileName() })
            else -> c
        },
        toolCalls = toolCalls?.ifEmpty { null },
        toolCallId = toolCallId
    )

    fun streamDisplay(streamContent: String) {
        val currentContent = content
        if (currentContent is MessageContent.Parts) {
            val updatedParts = currentContent.parts.map { part ->
                if (part is ContentPart.TextPart) {
                    part.copy(text = part.text + streamContent)
                } else {
                    part
                }
            }
            content = MessageContent.Parts(updatedParts)
        }
    }
    val display: String
        get() = when (val c = content) {
            is MessageContent.Text -> c.value
            is MessageContent.Parts -> c.parts
                .filterIsInstance<ContentPart.TextPart>()
                .filter { it.fileName == null } // содержимое файлов в чате не показываем, только плашку
                .joinToString("\n") { it.text }
            null -> ""
        }

    @Serializable
    enum class MessageRole {
        @SerialName("system")
        SYSTEM,
        @SerialName("user")
        USER,
        @SerialName("assistant")
        ASSISTANT,
        @SerialName("tool")
        TOOL
    }
}
