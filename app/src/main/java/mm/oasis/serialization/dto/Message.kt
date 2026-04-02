package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

data class UiData(
    var isAnimating: Boolean = false,
    var reasoningParagraph: String = ""
)

@Serializable
data class Message(
    val role: MessageRole,
    var content: MessageContent? = null,
    var reasoning: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @Transient val uiData: UiData = UiData()
) {
    val display: String
        get() = when (val c = content) {
            is MessageContent.Text -> c.value
            is MessageContent.Parts -> c.parts
                .filterIsInstance<ContentPart.TextPart>()
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
