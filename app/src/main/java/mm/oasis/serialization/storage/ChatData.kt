package mm.oasis.serialization.storage

import mm.oasis.serialization.dto.Message
import kotlinx.serialization.Serializable

@Serializable
data class ChatData(
    var name: String = "",
    var messages: List<Message> = emptyList()
)