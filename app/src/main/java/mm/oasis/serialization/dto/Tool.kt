package mm.oasis.serialization.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
)

@Serializable
data class Tool(
    val type: String = "function",
    val function: FunctionDefinition,
    @Transient val execute: (String) -> Any = {}
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: JsonSchema
)

@Serializable
data class JsonSchema(
    val type: String? = null,
    val description: String? = null,
    val properties: Map<String, JsonSchemaProperty>? = null,
    val required: List<String>? = null,
    val items: JsonSchema? = null,
    val enum: List<String>? = null,
    @SerialName("additionalProperties")
    val additionalProperties: Boolean? = null,
    val format: String? = null,
    val extensions: Map<String, JsonElement>? = null,
)

@Serializable
data class JsonSchemaProperty(
    val type: String? = null,
    val description: String? = null,
    val enum: List<String>? = null,
    val items: JsonSchema? = null,
    val properties: Map<String, JsonSchemaProperty>? = null,
    val required: List<String>? = null,
    @SerialName("additionalProperties")
    val additionalProperties: Boolean? = null,
    val format: String? = null,
    val default: JsonElement? = null
)

/* EXAMPLE

val getWeatherTool = Tool(
    function = FunctionDefinition(
        name = "get_weather",
        description = "Get current weather for a location",
        parameters = JsonSchema(
            type = "object",
            properties = mapOf(
                "location" to JsonSchemaProperty(
                    type = "string",
                    description = "City name"
                ),
                "unit" to JsonSchemaProperty(
                    type = "string",
                    enum = listOf("celsius", "fahrenheit")
                )
            ),
            required = listOf("location")
        )
    )
)
*/