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
import org.jsoup.nodes.Element
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
                        links.toString()
                    } catch (e: Exception) {
                        e.toString()
                    }
                }
            ),
            Tool(
                function = FunctionDefinition(
                    name = "fetch_page",
                    description = "Extract text from website page.",
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
                    try {
                        val result = fetchPage(args.url)
                        result
                    } catch (e: Exception) {
                        e.toString()
                    }
                }
            )
        )
    }

    suspend fun fetchPage(url: String): String = withContext(Dispatchers.IO) {
        val doc: Document = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .timeout(15_000)
            .followRedirects(true)
            .get()

        doc.select("""
            script, style, noscript, 
            header, footer, nav, 
            iframe, embed, object, 
            aside, .sidebar, .ad, .advertisement, .ads, 
            .social, .share, .comment, .comments, 
            .cookie, .banner, .popup, .modal,
            link[rel='stylesheet'], meta
        """.trimIndent()).remove()

        doc.select("*").forEach { el ->
            if (el.text().trim().isEmpty() && el.children().isEmpty()) {
                el.remove()
            }
        }

        val paragraphs = mutableListOf<String>()

        val articleSelectors = listOf(
            "article",
            "[role='main']",
            ".post-content", ".article-content", ".entry-content",
            ".content", ".main-content",
            "#content", "#main",
            "main"
        )

        var mainContent: Element? = null

        for (selector in articleSelectors) {
            val el = doc.select(selector).first()
            if (el != null && el.text().length > 200) {
                mainContent = el
                break
            }
        }

        val root = mainContent ?: doc.body()

        root.select("p, h1, h2, h3, h4, h5, h6, div, li, blockquote, pre").forEach { element ->
            val text = element.text().trim()
            if (text.isNotEmpty() && text.length > 10) {
                paragraphs.add(text)
            }
        }

        if (paragraphs.isEmpty()) {
            val fallback = doc.body().text().trim()
            return@withContext fallback.ifEmpty { "Failed to extract content" }
        }

        val cleanText = paragraphs
            .joinToString("\n\n") { it }
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        cleanText.ifEmpty { "No readable content found" }
    }

    suspend fun searchLinks(
        query: String,
        max: Int,
        sources: List<String>
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