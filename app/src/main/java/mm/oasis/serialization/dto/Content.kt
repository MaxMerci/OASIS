package mm.oasis.serialization.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import java.io.File

@Serializable(with = MessageContentSerializer::class)
sealed class MessageContent {
    data class Text(val value: String) : MessageContent()
    data class Parts(val parts: List<ContentPart>) : MessageContent()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ContentPart {
    abstract val fileName: String?

    @Serializable
    @SerialName("text")
    data class TextPart(
        val text: String,
        @Transient override val fileName: String? = null
    ) : ContentPart()

    @Serializable
    @SerialName("image_url")
    data class ImagePart(
        @SerialName("image_url") val imageUrl: ImageUrl,
        @Transient override val fileName: String? = null
    ) : ContentPart()

    @Serializable
    @SerialName("input_audio")
    data class AudioPart(
        @SerialName("input_audio") val inputAudio: InputAudio,
        @Transient override val fileName: String? = null
    ) : ContentPart()
}

@Serializable
data class ImageUrl(
    // "data:image/jpeg;base64,{BASE64}"
    val url: String,
    // OpenAI: "low", "high" или "auto"
    val detail: String? = null
)

@Serializable
data class InputAudio(
    val data: String,
    val format: String
)