package mm.oasis.remote.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Tool
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

object DuckDuckGoAPI : ToolI {
    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class Args(
        val query: String
    )

    override fun getTools(): List<Tool> {
        return listOf(Tool(
            function = FunctionDefinition(
                name = "duckduckgo_search",
                description = "DuckDuckGo API quick web search",
                parameters = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "query" to JsonSchemaProperty(
                            type = "str",
                            description = "text query"
                        )
                    ),
                    required = listOf("query")
                )
            ),
            execute = { args ->
                val args = json.decodeFromString<Args>(args)
                val response = search(args.query)
                response.toString()
            }
        ))
    }

    fun search(query: String): JSONObject {
        val url = "https://api.duckduckgo.com/?q=${query.replace(" ", "+")}&format=json"
        val response = URL(url).readText()
        return JSONObject(response)
    }
}