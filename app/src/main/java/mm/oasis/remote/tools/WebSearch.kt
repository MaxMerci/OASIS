package mm.oasis.remote.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Tool
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object WebSearch : ToolI {
    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class Args(
        val query: String,
        val max: Int = 3
    )

    @Serializable
    private data class ArgsFetch(
        val url: String,
    )

    override fun getTools(): List<Tool> {
        return listOf(
            Tool(
                function = FunctionDefinition(
                    name = "web_search",
                    description = "Full internet search",
                    parameters = JsonSchema(
                        type = "object",
                        properties = mapOf(
                            "query" to JsonSchemaProperty(
                                type = "str",
                                description = "text query"
                            ),
                            "max" to JsonSchemaProperty(
                                type = "int",
                                description = "maximum number of links to display information (default = 3)"
                            )
                        ),
                        required = listOf("query")
                    )
                ),
                execute = { args ->
                    val args = json.decodeFromString<Args>(args)

                    try {
                        val links = searchLinks(args.query, args.max)
                        val results = links.associateBy {
                            extractContent(it)
                        }
                        results.toString()
                    } catch (e: Exception) {
                        e.toString()
                    }
                }
            ),
            Tool(
                function = FunctionDefinition(
                    name = "get_urls",
                    description = "Get links to websites on query",
                    parameters = JsonSchema(
                        type = "object",
                        properties = mapOf(
                            "query" to JsonSchemaProperty(
                                type = "str",
                                description = "text query"
                            ),
                            "max" to JsonSchemaProperty(
                                type = "int",
                                description = "maximum number of links to display information (default = 3)"
                            )
                        ),
                        required = listOf("query")
                    )
                ),
                execute = { args ->
                    val args = json.decodeFromString<Args>(args)
                    try {
                        val links = searchLinks(args.query, args.max)
                        links.toString()
                    } catch (e: Exception) {
                        e.toString()
                    }
                }
            ),
            Tool(
                function = FunctionDefinition(
                    name = "fetch_url",
                    description = "Get text content from a website",
                    parameters = JsonSchema(
                        type = "object",
                        properties = mapOf(
                            "url" to JsonSchemaProperty(
                                type = "str",
                                description = "URL"
                            )
                        ),
                        required = listOf("url")
                    )
                ),
                execute = { args ->
                    val args = json.decodeFromString<ArgsFetch>(args)
                    extractContent(args.url)
                }
            )
        )
    }

    fun extractContent(url: String): String {
        try {
            val doc: Document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10_000)
                .get()
            doc.select("script, style, nav, footer, header, ads, aside").remove()
            val candidates = listOf(
                doc.select("article"),
                doc.select("[role=main]"),
                doc.select(".content"),
                doc.select("#content"),
                doc.select(".post"),
                doc.select(".article")
            )

            val mainElement = candidates
                .flatten()
                .maxByOrNull { it.text().length }

            return mainElement?.text() ?: doc.body().text()
        } catch (e: Exception) {
            return e.toString()
        }
    }

    fun searchLinks(query: String, max: Int): List<String> {
        try {
            val url = "https://duckduckgo.com/html/?q=" + query.replace(" ", "+")
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

            return links
        } catch (e: Exception) {
            throw e
        }
    }
}