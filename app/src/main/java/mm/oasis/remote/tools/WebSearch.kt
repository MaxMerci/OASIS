package mm.oasis.remote.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Tool
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
                description = "Web search",
                parameters = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "query" to JsonSchemaProperty(
                            type = "str",
                            description = "text query"
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
                println("WEB SEARCH")
                val args = json.decodeFromString<Args>(args)

                var response = ""

                for (link in searchLinks(args.query)) {
                    response += link + "\n"

                    var doc: Document
                    try {
                        doc = withContext(Dispatchers.IO) {
                            Jsoup.connect("https://www.weather-forecast.com/locations/Moscow/forecasts/latest")
                                .userAgent("Mozilla/5.0")
                                .timeout(15_000)
                                .get()
                        }
                    } catch (e: Exception) {
                        response += "ERROR READING: ${e.message ?: e.toString()}\n"
                        continue
                    }

                    val raw = RelevanceFit.cleanDoc(doc)
                    val relevancePrep = RelevanceFit.create(
                        raw,
                        12, 2, 0.35
                    )
                    val relevant = relevancePrep.getRelevantChunks(args.query)
                    response += relevant.joinToString { it.trimIndent() + "\n" } + "\n"
                }

                response
            }
        )
    }

    suspend fun searchLinks(
        query: String,
        sources: List<String> = emptyList(),
        max: Int = 3
    ): List<String> = withContext(Dispatchers.IO) {
        val max = min(5, max)
        val siteFilter = if (sources.isNotEmpty()) {
            sources.joinToString(" OR ") { "site:$it" }
        } else ""

        val finalQuery = if (siteFilter.isNotEmpty()) {
            "$query $siteFilter"
        } else {
            query
        }

        val url = "https://duckduckgo.com/html/?q=" + finalQuery.replace(" ", "+")
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .timeout(10000)
            .get()

        val links = mutableListOf<String>()
        val results = doc.select("a.result__a").filter { it.hasText() }.take(max)

        for (element in results) {
            val href = element.attr("href")

            val cleanLink = if (href.contains("duckduckgo.com/l/?uddg=")) {
                val encodedUrl = href.substringAfter("uddg=")
                    .substringBefore("&")
                URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
            } else {
                href
            }

            links.add(cleanLink)
        }

        links
    }
}