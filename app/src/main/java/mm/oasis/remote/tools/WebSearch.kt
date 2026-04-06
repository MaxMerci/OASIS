package mm.oasis.remote.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mm.oasis.remote.ApiClient
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent
import mm.oasis.serialization.dto.Request
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

    @Serializable
    private data class ArgsFetch(
        val url: String,
        val query: String = "Display brief basic information from the page, with links to additional sources, if any."
    )

    override fun getTools(): List<Tool> {
        return listOf(
            Tool(
                function = FunctionDefinition(
                    name = "search_links",
                    description = "Performs a search for a given query and extracts a list of links (URLs) from the results.",
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
                                description = "maximum number of links to display information (default = 3 | max = 5)"
                            )
                        ),
                        required = listOf("query")
                    )
                ),
                execute = { args ->
                    val args = json.decodeFromString<Args>(args)
                    try {
                        val links = searchLinks(args.query, args.max, args.sources)
                        "FOUND FOR \"${args.query}\" (MAX: ${args.max} SOURCES: ${args.sources}):\n" + links.toString()
                    } catch (e: Exception) {
                        e.toString()
                    }
                }
            ),
            Tool(
                function = FunctionDefinition(
                    name = "fetch_page",
                    description = "Displays basic information from a web page upon request",
                    parameters = JsonSchema(
                        type = "object",
                        properties = mapOf(
                            "url" to JsonSchemaProperty(
                                type = "str",
                                description = "URL"
                            ),
                            "query" to JsonSchemaProperty(
                                type = "str",
                                description = "What the AI model will look for on the page (default: \"Extract the main information from the page, including additional sources if any. If the information is not found, say so.\")"
                            )
                        ),
                        required = listOf("url")
                    )
                ),
                execute = { args ->
                    val args = json.decodeFromString<ArgsFetch>(args)
                    val result = fetchPage(args.url, args.query)
                    result
                }
            )
        )
    }

    /**
     * Я предупреждаю, это максимально не экономный метод.
     * Самому больно такое делать.
    */
    suspend fun fetchPage(url: String, query: String): String {
        try {
            val doc: Document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10_000)
                .get()
            val text = doc.text()

            var response = ""

            ApiClient.generateStream(Request(
                ProfileRepository.currentProfile!!.model!!.id,
                listOf(
                    Message(
                        Message.MessageRole.SYSTEM,
                        MessageContent.Text(text)
                    ),
                    Message(
                        Message.MessageRole.USER,
                        MessageContent.Text(query)
                    )
                ),
                includeReasoning = false
            )).collect { chunk ->
                response += chunk.choices[0].delta.content ?: ""
            }

            return response
        } catch (e: Exception) {
            return e.toString()
        }
    }

    fun searchLinks(query: String, max: Int, sites: List<String>): List<String> {
        val max = min(5, max)
        try {
            val siteFilter = if (sites.isNotEmpty()) {
                sites.joinToString(" OR ") { "site:$it" }
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
            return links
        } catch (e: Exception) {
            throw e
        }
    }
}