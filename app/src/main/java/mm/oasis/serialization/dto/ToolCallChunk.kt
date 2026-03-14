package mm.oasis.serialization.dto

import kotlinx.serialization.Serializable

@Serializable
data class ToolCallChunk(
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
)