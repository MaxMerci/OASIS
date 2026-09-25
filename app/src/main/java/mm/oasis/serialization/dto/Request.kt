package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Request(
    var messages: List<Message> = emptyList(),
    var model: String? = null,
    val stream: Boolean = true, // не надо это трогать, мое
    // если удалить stream структура ответа просто изменится и он не распарсится
    var temperature: Double? = null,
    @SerialName("top_p") var topP: Double? = null,
    @SerialName("max_tokens") var maxTokens: Int? = null,
    @SerialName("include_reasoning") var includeReasoning: Boolean? = null,
    var tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    @Transient val maxIterations: Int = DEFAULT_MAX_ITERATIONS,
) {
    companion object {
        const val DEFAULT_MAX_ITERATIONS = 10
    }
}
