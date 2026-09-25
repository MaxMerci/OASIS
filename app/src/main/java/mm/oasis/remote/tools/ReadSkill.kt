package mm.oasis.remote.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mm.oasis.remote.AgentFiles
import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Tool


object ReadSkill : ToolI {
    const val NAME = "read_skill"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class Args(val name: String = "")

    override fun getTool(): Tool = Tool(
        repeatable = true,
        function = FunctionDefinition(
            name = NAME,
            description = "Read the full instructions of a skill listed in <available_skills>.",
            parameters = JsonSchema(
                type = "object",
                properties = mapOf(
                    "name" to JsonSchemaProperty(
                        type = "string",
                        description = "Exact skill <name> from <available_skills>"
                    )
                ),
                required = listOf("name")
            )
        ),
        execute = { raw ->
            val name = json.decodeFromString<Args>(raw.ifBlank { "{}" }).name
            withContext(Dispatchers.IO) {
                AgentFiles.readSkill(name)
                    ?: ("Error: skill '$name' not found. Available: " +
                            AgentFiles.skills().joinToString { it.name }.ifEmpty { "none" })
            }
        }
    )
}
