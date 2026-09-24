package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Request(
    var messages: List<Message> = emptyList(),
    var model: String? = null,
    val stream: Boolean = true, // не надо это трогать, мое
    var temperature: Double? = null,
    @SerialName("top_p") var topP: Double? = null,
    @SerialName("max_tokens") var maxTokens: Int? = null,
    @SerialName("include_reasoning") var includeReasoning: Boolean? = null,
    var tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
)
