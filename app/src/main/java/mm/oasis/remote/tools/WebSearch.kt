package mm.oasis.remote.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Tool
import org.jsoup.Jsoup
import kotlin.math.min

object WebSearch : ToolI {
    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class Args(
        val query: String,
        val sources: List<String> = emptyList(),
        val max: Int = 3
    )

    override fun getTool(): Tool {
        return Tool(
            function = FunctionDefinition(
                name = "web_search",
                description = "Web search. Never use a function twice in a row, even if no results are found, use what you can find.",
                parameters = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "query" to JsonSchemaProperty(
                            type = "str",
                            description = "text query, write only in English (translate manually)"
                        ),
                        "sources" to JsonSchemaProperty(
                            type = "list",
                            description = "sources for searching them (list of exact site URLs)"
                        ),
                        "max" to JsonSchemaProperty(
                            type = "int",
                            description = "maximum number of links to display information"
                        )
                    ),
                    required = listOf("query")
                )
            ),
            execute = { args ->
                val args = json.decodeFromString<Args>(args)

                val result = coroutineScope {
                    search(
                        args.query,
                        args.sources,
                        min(5, args.max)
                    )
                }

                result.entries.joinToString(separator="\n") { it.key + ": " + it.value}
            }
        )
    }

    suspend fun search(
        q: String,
        sources: List<String> = emptyList(),
        max: Int = 3
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val sourcesBody = sources.joinToString(" OR ") { "site:$it" }
        val query = if (sourcesBody.isNotEmpty()) "$q $sourcesBody" else q

        val url = "https://duckduckgo.com/html/?q=" + query.replace(" ", "+")
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .timeout(10000)
            .get()

        val map = mutableMapOf<String, String>()

        val results = doc.select("a.result__a").filter { it.hasText() }.take(max)
        val snippets = doc.select("a.result__snippet").filter { it.hasText() }.take(max)

        results.zip(snippets).forEach { (result, snippet) ->
            val rawHref = snippet.attr("href")
            val cleanUrl = rawHref
                .substringAfter("uddg=")
                .substringBefore("&")
                .let { java.net.URLDecoder.decode(it, "UTF-8") }

            val title = result.text()
            val snippetText = snippet.text()

            if (cleanUrl.isNotEmpty()) {
                map["$cleanUrl ($title)"] = snippetText
            }
        }

        map
    }
}