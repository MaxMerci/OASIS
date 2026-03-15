package mm.oasis.remote

import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.ChatCompletionChunk
import mm.oasis.serialization.dto.LLMResponse
import mm.oasis.serialization.dto.Request
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

private val json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object ApiClient{
    private var generationJob: Job? = null
    var generating = false
        private set

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }

        engine {
            config {
                followRedirects(true)
                protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))

                callTimeout(0, TimeUnit.MILLISECONDS)
                readTimeout(0, TimeUnit.MILLISECONDS)
                writeTimeout(0, TimeUnit.MILLISECONDS)
            }
        }

        defaultRequest {
            header(HttpHeaders.Authorization, "Bearer ${ProfileRepository.currentProfile?.apiKey}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("Cache-Control", "no-cache")
            header("Expect", "")
        }
    }

    fun generateTextStream(request: Request): Flow<ChatCompletionChunk> = channelFlow {
        generating = true
        generationJob = coroutineContext[Job]

        try {
            client.preparePost("${ProfileRepository.currentProfile!!.endPoint.trimEnd('/')}/chat/completions") {
                setBody(request.copy(stream = true))
            }.execute { response ->
                if (!response.status.isSuccess()) throw Exception(response.bodyAsText())

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") return@execute

                        val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                        send(chunk)
                    }
                }
            }
        } finally {
            generating = false
            generationJob = null
        }
    }

    fun stop() {
        generationJob?.cancel()
        generationJob = null
        generating = false
    }

    suspend fun fetchModels(): LLMResponse {
        val avatars = mapOf(
            "gpt" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://chatgpt.com",
            "claude" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://claude.ai",
            "gemini" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://google.com",
            "gemma" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://google.com",
            "meta" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://chatgpt.com",
            "mistralai" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://mistral.ai",
            "mistral" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://mistral.ai",
            "cohere" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://cohere.com/",
            "grok" to "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://grok.com/"
        )
        val defaultAvatar = "https://alduris.github.io/watcher-map/embed.png" // да я люблю рв

        val profile = ProfileRepository.currentProfile
        val baseUrl = profile?.endPoint?.trimEnd('/')
        val url = "$baseUrl/models"

        val response = client.get(url)

        if (response.status.value != 200) {
            val errorText = response.bodyAsText()
            throw IllegalStateException("API Error ${response.status}: $errorText")
        }

        val r = response.body<LLMResponse>()

        for (l in r.data) {
            if (l.ownedBy == null && l.id.contains("/")) {
                if (l.tokenizer != null) l.ownedBy = l.tokenizer
                else l.ownedBy = l.id.split("/")[0]
            }
            if (l.name == null) {
                l.name = l.id
            }
            if (l.avatarUrl == null) {
                for ((c, a) in avatars.entries) {
                    if (l.id.contains(c)) l.avatarUrl = a
                    break
                }
                if (l.avatarUrl == null) l.avatarUrl = defaultAvatar
            }
        }

        return r
    }
}