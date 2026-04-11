package mm.oasis.remote.tools

import mm.oasis.remote.ApiClient
import mm.oasis.serialization.dto.EmbedRequest
import org.jsoup.nodes.Document
import kotlin.math.sqrt

class DocumentRetriever private constructor(
    private val chunks: List<String>,
    private val chunkEmbeddings: List<List<Double>>,
    private val defaultMinSimilarity: Double
) {
    suspend fun retrieve(
        query: String,
        maxChunks: Int = 5,
        minSimilarity: Double = defaultMinSimilarity
    ): List<String> {
        if (query.isBlank() || chunks.isEmpty()) return emptyList()

        val response = ApiClient.embedding(
            EmbedRequest(input = listOf(query))
        )

        val queryEmbedding = response.data[0].embedding

        val scored = chunks.zip(chunkEmbeddings)
            .map { (chunkText, chunkEmb) ->
                val sim = cosineSimilarity(queryEmbedding, chunkEmb)
                chunkText to sim
            }
            .sortedByDescending { it.second }
            .take(maxChunks)

        var retrieved = scored.filter { it.second > minSimilarity }

        // Если результатов не найдено, вычисляем индекс с самой большой разницей sim и берем все блоки до него.
        retrieved.ifEmpty {
            if (scored.size < 2) return@ifEmpty
            var maxDiff = Double.MIN_VALUE
            var maxIndex = -1
            for (i in 0 until scored.size - 1) {
                val diff = scored[i].second - (scored[i].second + 1)
                if (diff > maxDiff) {
                    maxDiff = diff
                    maxIndex = i
                }
            }
            retrieved = scored.take(maxIndex - 1)
        }

        return retrieved.map { it.first }
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        normA = sqrt(normA)
        normB = sqrt(normB)

        return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
    }

    companion object {
        fun clearDoc(doc: Document): String {
            doc.select("script, style, noscript, svg, canvas, iframe, header, footer, nav, aside").remove()
            var text = doc.body()?.text() ?: doc.text()
            text = text.replace(Regex("""\s+"""), " ")
            val garbageRegexes = listOf(
                Regex("""©\s*\d{4}.*?All Rights Reserved""", RegexOption.IGNORE_CASE),
                Regex("""Privacy Policy|Cookies|Terms of Use|Cookie Settings""", RegexOption.IGNORE_CASE),
                Regex("""Subscribe|Newsletter|Follow us|Share""", RegexOption.IGNORE_CASE),
                Regex("""javascript:|function\s*\(|window\.|document\.get""", RegexOption.IGNORE_CASE)
            )
            for (regex in garbageRegexes) {
                text = text.replace(regex, " ")
            }
            val words = text.split(Regex("\\s+"))
            val deduped = mutableListOf<String>()
            var prev = ""
            var count = 0

            for (word in words) {
                if (word.equals(prev, ignoreCase = true)) {
                    count++
                    if (count <= 2) {
                        deduped.add(word)
                    }
                } else {
                    count = 1
                    deduped.add(word)
                    prev = word.lowercase()
                }
            }
            text = deduped.joinToString(" ")
            val fragments = text.split(Regex("[.!?]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.split("\\s+".toRegex()).size >= 4 }
            text = fragments.joinToString(". ")
            text = text.replace(Regex("""\s*\.\s*\.\s*"""), ". ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            return text
        }

        suspend fun create(
            document: String,
            sentencesPerChunk: Int = 12,
            overlapSentences: Int = 4,
            defaultMinSimilarity: Double = 0.65
        ): DocumentRetriever {
            val sentences = splitIntoSentences(document)
            if (sentences.isEmpty()) {
                return DocumentRetriever(emptyList(), emptyList(), defaultMinSimilarity)
            }

            val chunks = overlappingChunks(
                sentences,
                sentencesPerChunk,
                overlapSentences
            )

            val response = ApiClient.embedding(
                EmbedRequest(input = chunks)
            )

            val chunkEmbeddings = response.data.map { it.embedding }

            return DocumentRetriever(
                chunks,
                chunkEmbeddings,
                defaultMinSimilarity
            )
        }

        private fun splitIntoSentences(text: String): List<String> =
            text.split(Regex("(?<=[.!?])\\s+"))
                .filter { it.isNotBlank() }
                .map { it.trim() }

        private fun overlappingChunks(
            sentences: List<String>,
            chunkSize: Int,
            overlap: Int
        ): List<String> {
            if (sentences.isEmpty()) return emptyList()

            require(chunkSize > overlap) { "chunkSize must be > overlap" }

            val result = mutableListOf<String>()
            var i = 0

            while (i < sentences.size) {
                val end = minOf(i + chunkSize, sentences.size)
                val chunk = sentences.subList(i, end).joinToString(" ")
                result.add(chunk)

                i += chunkSize - overlap
                if (chunkSize - overlap <= 0) i += 1
            }
            return result
        }
    }
}