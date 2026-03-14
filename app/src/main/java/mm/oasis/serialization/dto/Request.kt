package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Request(
    var model: String = "",
    var messages: List<Message> = emptyList(),
    val stream: Boolean = true, // не надо это трогать, мое
    var temperature: Double? = 1.0,
    @SerialName("top_p") var topP: Double? = null,
    @SerialName("max_tokens") var maxTokens: Int? = null,
    @SerialName("include_reasoning") var includeReasoning: Boolean = false,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    @SerialName("extra") val extra: JsonObject? = null
) {
    @Serializable
    data class Tool(
        val type: String = "function",
        val function: FunctionDefinition
    ) {
        @Serializable
        data class FunctionDefinition(
            val name: String,
            val description: String? = null,
            val parameters: JsonElement
        )
    }
}