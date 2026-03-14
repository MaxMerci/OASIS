package mm.oasis.serialization.storage

import mm.oasis.serialization.dto.Message
import kotlinx.serialization.Serializable

@Serializable
data class ChatData(
    val name: String = "",
    val messages: MutableList<Message> = mutableListOf()
)