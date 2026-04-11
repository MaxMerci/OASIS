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
import mm.oasis.serialization.dto.EmbedRequest
import mm.oasis.serialization.dto.EmbedResp
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

private val json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

object ApiClient {
    private var generationJob: Job? = null

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
        } catch (e: Exception) {
            val chunk = ChatCompletionChunk(
                "", listOf(
                    ChatCompletionChunk.ChunkChoice(
                        0, ChatCompletionChunk.Delta(content = e.message)
                    )
                )
            )
            send(chunk)
        } finally {
            generationJob = null
        }
    }

    fun stop() {
        generationJob?.cancel()
    }

    suspend fun embedding(req: EmbedRequest): EmbedResp {
        val response: HttpResponse = client.post(
            "https://lamhieu-lightweight-embeddings.hf.space/v1/embeddings"
        ) {
            headers.remove(HttpHeaders.Authorization)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(req)
        }
        return response.body<EmbedResp>()
    }

    suspend fun fetchModels(): LLMResponse {
        val profile = ProfileRepository.currentProfile
        val baseUrl = profile?.endPoint?.trimEnd('/')
        val url = "$baseUrl/models"

        val response = client.get(url) {
            parameter("order", "most-popular")
        }
        val models = response.body<LLMResponse>()
        models.data.forEach { it.avatarUrl = findAvatar(it.id) }
        return models
    }

    fun findAvatar(q: String): String {
        // оооууу да, Маквин готов!
        val favicon = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url="

        val avatars = mapOf(
            "gpt" to "${favicon}https://chatgpt.com",
            "claude" to "${favicon}https://claude.ai",
            "google" to "${favicon}https://google.com",
            "meta" to "${favicon}https://chatgpt.com",
            "mistral" to "${favicon}https://mistral.ai",
            "mistral" to "${favicon}https://mistral.ai",
            "cohere" to "${favicon}https://cohere.com/",
            "grok" to "${favicon}https://grok.com/",
            "nvidia" to "${favicon}https://www.nvidia.com",
            "qwen" to "${favicon}https://chat.qwen.ai/",
            "anthropic" to "${favicon}https://www.anthropic.com/",
        )
        val defaultAvatar = listOf(
            "https://static.wikia.nocookie.net/rainworld/images/8/82/Main_Slugcat_Yellow.png/revision/latest/smart/width/250/height/250?cb=20190609053103&path-prefix=ru",
            "https://biographe.ru/wp-content/uploads/2025/10/3212.jpg",
            "https://e7.pngegg.com/pngimages/369/83/png-clipart-emoticon-emoji-heart-smiley-love-emoji-sticker-symbol.png",
            "https://rberega.info/wp-content/uploads/2022/09/%D1%81%D0%BC%D0%B0%D0%B9%D0%BB%D0%B8%D0%BA-1024x1024.jpg",
            "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQbo1MkPosK42nOCM3geaJST5IeknlgCJQT6g&s",
            "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQKYdj337ura2bM14B-zc7R7o08kLYBgtq8RA&s",
            "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTXPpXln3KTK1BT885r3JLycGD06fgHQM0r6w&s",
            "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQHagsmFfRv-mUPbDrpavQwL88GeK9-jp0QgA&s"
        ).random()

        val lowerQ = q.lowercase()

        for ((key, value) in avatars) {
            if (lowerQ.contains(key)) {
                return value
            }
        }

        return defaultAvatar
    }
}
