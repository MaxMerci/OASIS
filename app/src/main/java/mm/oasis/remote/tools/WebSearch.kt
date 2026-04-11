package mm.oasis.remote.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

                val results = coroutineScope {
                    searchLinks(args.query, args.sources, args.max).map { link ->
                        async(Dispatchers.IO) {
                            try {
                                val doc = Jsoup.connect(link)
                                    .userAgent("Mozilla/5.0")
                                    .timeout(5_000)
                                    .get()

                                val raw = DocumentRetriever.clearDoc(doc)
                                val relevancePrep = DocumentRetriever.create(raw)
                                val relevant = relevancePrep.retrieve(args.query)

                                if (relevant.isNotEmpty()) {
                                    "$link\n" + relevant.joinToString("\n") { it.trimIndent() } + "\n"
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.awaitAll()
                }
                results.filterNotNull().joinToString("\n")
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