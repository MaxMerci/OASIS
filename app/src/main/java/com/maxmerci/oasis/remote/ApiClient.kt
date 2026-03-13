package com.maxmerci.oasis.remote

import com.maxmerci.oasis.repository.ProfileRepository
import com.maxmerci.oasis.serialization.dto.ChatCompletionChunk
import com.maxmerci.oasis.serialization.dto.Request
import io.ktor.client.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.*
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

private val json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object ApiClient{
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
            header(HttpHeaders.Authorization, "Bearer ${ProfileRepository.currentProfile.apiKey}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("Cache-Control", "no-cache")
            header("Expect", "")
        }
    }

    fun generateTextStream(request: Request): Flow<ChatCompletionChunk> = channelFlow {
        client.preparePost("${ProfileRepository.currentProfile.endPoint.trimEnd('/')}/chat/completions") {
            setBody(request.copy(stream = true))
        }.execute { response ->
            if (!response.status.isSuccess()) throw Exception(response.bodyAsText())

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break

                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue

                    try {
                        val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                        send(chunk)
                    } catch (e: Exception) {}
                } else {
                    if (line.contains("error")) throw Exception(line)
                }
            }
        }
    }
}