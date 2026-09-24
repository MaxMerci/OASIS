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

/**
 * Веб поиск по сниппетам DuckDuckGo (html версия, без JS и без ключей).
 * Страницы целиком не качаем: по соотношению цена/качество сниппетов хватает.
 */
object WebSearch : ToolI {
    private const val ENDPOINT = "https://html.duckduckgo.com/html/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private const val MAX_RESULTS = 8

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable
    private data class Args(
        val query: String,
        val sources: List<String> = emptyList(),
        val max: Int = 5
    )

    data class Result(val title: String, val url: String, val snippet: String)

    override fun getTool(): Tool = Tool(
        function = FunctionDefinition(
            name = "web_search",
            description = "Search the web (DuckDuckGo) and get a list of results with short snippets. " +
                    "Use it once with a good query, then answer using what was found, even if results are incomplete.",
            parameters = JsonSchema(
                type = "object",
                properties = mapOf(
                    "query" to JsonSchemaProperty(
                        type = "string",
                        description = "Search query. Write it in English (translate if needed)."
                    ),
                    "sources" to JsonSchemaProperty(
                        type = "array",
                        items = JsonSchema(type = "string"),
                        description = "Optional list of site domains to limit the search to, e.g. [\"wikipedia.org\"]"
                    ),
                    "max" to JsonSchemaProperty(
                        type = "integer",
                        description = "Maximum number of results (1-$MAX_RESULTS), default 5"
                    )
                ),
                required = listOf("query")
            )
        ),
        execute = { raw ->
            val args = json.decodeFromString<Args>(raw.ifBlank { "{}" })
            val results = search(args.query, args.sources, args.max.coerceIn(1, MAX_RESULTS))

            if (results.isEmpty()) "No results found for \"${args.query}\"."
            else results.withIndex().joinToString("\n\n") { (i, r) ->
                "${i + 1}. ${r.title}\n${r.url}\n${r.snippet}"
            }
        }
    )

    suspend fun search(
        q: String,
        sources: List<String> = emptyList(),
        max: Int = 5
    ): List<Result> = withContext(Dispatchers.IO) {
        val sitesFilter = sources
            .map { it.removePrefix("https://").removePrefix("http://").trimEnd('/') }
            .filter { it.isNotBlank() }
            .joinToString(" OR ") { "site:$it" }
        val query = if (sitesFilter.isNotEmpty()) "$q ($sitesFilter)" else q

        val doc = Jsoup.connect(ENDPOINT)
            .data("q", query)
            .userAgent(USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .timeout(10_000)
            .get()

        parseResults(doc, max)
    }

    // каждый результат - отдельный блок, так заголовок, ссылка и сниппет не разъедутся
    fun parseResults(doc: Document, max: Int): List<Result> =
        doc.select("div.result").asSequence()
            .filterNot { it.hasClass("result--ad") }
            .mapNotNull { block ->
                val link = block.selectFirst("a.result__a") ?: return@mapNotNull null
                val url = cleanUrl(link.attr("href"))
                if (url.isBlank()) return@mapNotNull null
                Result(
                    title = link.text(),
                    url = url,
                    snippet = block.selectFirst(".result__snippet")?.text().orEmpty()
                )
            }
            .distinctBy { it.url }
            .take(max)
            .toList()

    // ссылки DDG выглядят как //duckduckgo.com/l/?uddg=<url>&rut=...
    private fun cleanUrl(href: String): String {
        if (!href.contains("uddg=")) return href
        val encoded = href.substringAfter("uddg=").substringBefore("&")
        return URLDecoder.decode(encoded, "UTF-8")
    }
}
