package mm.oasis.remote.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
//                        "max" to JsonSchemaProperty(
//                            type = "int",
//                            description = "maximum number of links to display information (default = 3 | max = 5)"
//                        )
                    ),
                    required = listOf("query")
                )
            ),
            execute = { args ->
                val args = json.decodeFromString<Args>(args)

                try {
                    val links = searchLinks(args.query, args.sources, args.max)
                    val raw = StringBuilder()
                    links.forEach {
                        val rawContent = extractContent(it)
                        raw.append("\n\n$it :\n$rawContent")
                    }
                    val result = sanitizeContent(raw.toString(), args.query)
                    result
                } catch (e: Exception) {
                    e.message ?: e.toString()
                }
            }
        )
    }

    suspend fun sanitizeContent(raw: String, query: String): String {
        val sanitized = StringBuilder()

        val content = """
        Find all the information relevant to the query:
        "$query" 
        and formulate key data
        """.trimIndent()

        try {
            ApiClient.generateStream(
                Request(
                    ProfileRepository.currentProfile!!.model!!.id,
                    listOf(
                        Message(
                            Message.MessageRole.SYSTEM,
                            MessageContent.Text(raw)
                        ),
                        Message(
                            Message.MessageRole.USER,
                            MessageContent.Text(content)
                        ),
                    ),
                    includeReasoning = false
                )
            ).collect { chunk ->
                sanitized.append(
                    chunk.choices[0].delta.content ?: ""
                )
            }
            return sanitized.toString()
        } catch (e: Exception) {
            return e.message ?: e.toString()
        }
    }

    suspend fun extractContent(url: String): String = withContext(Dispatchers.IO) {
        try {
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
        } catch (e: Exception) {
           e.message ?: e.toString()
        }
    }

    suspend fun searchLinks(
        query: String,
        sources: List<String>,
        max: Int
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