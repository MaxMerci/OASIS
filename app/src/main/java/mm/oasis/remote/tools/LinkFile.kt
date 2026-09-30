package mm.oasis.remote.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mm.oasis.remote.Workspace
import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Tool


object LinkFile : ToolI {
    const val NAME = "link_file"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class Args(val path: String = "")

    fun linkedPath(raw: String): String? = try {
        val path = json.decodeFromString<Args>(raw.ifBlank { "{}" }).path
        Workspace.resolve(path)?.let { Workspace.relativePath(it) }
    } catch (e: Exception) {
        null
    }

    override fun getTool(): Tool = Tool(
        repeatable = true,
        available = { Workspace.files().isNotEmpty() },
        function = FunctionDefinition(
            name = NAME,
            description = "Attach a file from the workspace to the end of your answer so the user can open it.",
            parameters = JsonSchema(
                type = "object",
                properties = mapOf(
                    "path" to JsonSchemaProperty(
                        type = "string",
                        description = "File path relative to the workspace root, e.g. \"report.pdf\""
                    )
                ),
                required = listOf("path")
            )
        ),
        execute = { raw ->
            linkedPath(raw)?.let { "File $it attached to the answer." }
                ?: ("Error: file not found in workspace. Available: " +
                        Workspace.files().joinToString().ifEmpty { "none" })
        }
    )
}
