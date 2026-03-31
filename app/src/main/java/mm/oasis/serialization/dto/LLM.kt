package mm.oasis.serialization.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class LLMResponse(
    val data: List<LLMRaw>
)

@Serializable(with = LLMRawSerializer::class)
data class LLMRaw(
    val id: String,
    var avatarUrl: String? = null,
    val extra: Map<String, Any?> = emptyMap(),
)

object LLMRawSerializer : KSerializer<LLMRaw> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): LLMRaw {
        val input = decoder as? JsonDecoder ?: throw Exception("Only JSON is supported")
        val jsonObject = input.decodeJsonElement().jsonObject

        val id = jsonObject["id"]?.jsonPrimitive?.content ?: ""
        val avatarUrl = jsonObject["avatarUrl"]?.jsonPrimitive?.contentOrNull

        val knownKeys = setOf("id", "avatarUrl")
        val extraMap = jsonObject.filterKeys { it !in knownKeys }
            .mapValues { it.value.toAny() }

        return LLMRaw(id, avatarUrl, extraMap)
    }

    override fun serialize(encoder: Encoder, value: LLMRaw) {
        val output = encoder as? JsonEncoder ?: throw Exception("Only JSON is supported")
        val map = mutableMapOf<String, JsonElement>()
        map["id"] = JsonPrimitive(value.id)
        value.avatarUrl?.let { map["avatarUrl"] = JsonPrimitive(it) }
        
        value.extra.forEach { (k, v) ->
            map[k] = v.toJsonElement()
        }
        
        output.encodeJsonElement(JsonObject(map))
    }
}

fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> {
        val content = this.entries.associate { it.key.toString() to it.value.toJsonElement() }
        JsonObject(content)
    }
    is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

fun JsonElement.toAny(): Any? = when (this) {
    is JsonPrimitive -> {
        if (isString) content
        else if (content == "true" || content == "false") content.toBoolean()
        else content.toDoubleOrNull() ?: content
    }
    is JsonObject -> mapValues { it.value.toAny() }
    is JsonArray -> map { it.toAny() }
    is JsonNull -> null
}
