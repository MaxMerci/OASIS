package com.maxmerci.oasis.serialization.dto

import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
)