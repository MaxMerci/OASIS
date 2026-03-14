package mm.oasis.serialization.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

object MessageContentSerializer : KSerializer<MessageContent> {
    override val descriptor = buildClassSerialDescriptor("MessageContent")

    override fun deserialize(decoder: Decoder): MessageContent {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Only json supports")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (element.isString) MessageContent.Text(element.content)
                else throw SerializationException("incorrect primitive")
            }
            is JsonArray -> {
                val parts = jsonDecoder.json.decodeFromJsonElement(
                    ListSerializer(ContentPart.serializer()), element
                )
                MessageContent.Parts(parts)
            }
            else -> throw SerializationException("Unexpected JSON format for content")
        }
    }

    override fun serialize(encoder: Encoder, value: MessageContent) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("Only json supports")

        when (value) {
            is MessageContent.Text -> jsonEncoder.encodeString(value.value)
            is MessageContent.Parts -> {
                val jsonElement = jsonEncoder.json.encodeToJsonElement(
                    ListSerializer(ContentPart.serializer()), value.parts
                )
                jsonEncoder.encodeJsonElement(jsonElement)
            }
        }
    }
}