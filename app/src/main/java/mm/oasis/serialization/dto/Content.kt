package mm.oasis.serialization.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

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

    fun withoutFileName(): ContentPart = when (this) {
        is TextPart -> copy(fileName = null)
        is ImagePart -> copy(fileName = null)
        is AudioPart -> copy(fileName = null)
        is VideoPart -> copy(fileName = null)
        is FilePart -> copy(fileName = null)
    }

    @Serializable
    @SerialName("text")
    data class TextPart(
        val text: String,
        @SerialName("file_name") override val fileName: String? = null
    ) : ContentPart()

    @Serializable
    @SerialName("image_url")
    data class ImagePart(
        @SerialName("image_url") val imageUrl: ImageUrl,
        @SerialName("file_name") override val fileName: String? = null
    ) : ContentPart()

    @Serializable
    @SerialName("input_audio")
    data class AudioPart(
        @SerialName("input_audio") val inputAudio: InputAudio,
        @SerialName("file_name") override val fileName: String? = null
    ) : ContentPart()

    @Serializable
    @SerialName("video_url")
    data class VideoPart(
        @SerialName("video_url") val videoUrl: VideoUrl,
        @SerialName("file_name") override val fileName: String? = null
    ) : ContentPart()

    // документы, которые модель читает сама (PDF)
    @Serializable
    @SerialName("file")
    data class FilePart(
        val file: FileData,
        @SerialName("file_name") override val fileName: String? = null
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

@Serializable
data class VideoUrl(
    // "data:video/mp4;base64,{BASE64}"
    val url: String
)

@Serializable
data class FileData(
    val filename: String,
    // "data:application/pdf;base64,{BASE64}"
    @SerialName("file_data") val fileData: String
)
