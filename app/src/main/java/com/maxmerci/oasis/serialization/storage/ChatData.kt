package com.maxmerci.oasis.serialization.storage

import com.maxmerci.oasis.serialization.dto.Message
import kotlinx.serialization.Serializable

@Serializable
data class ChatData(
    val name: String = "",
    val messages: MutableList<Message> = mutableListOf()
)