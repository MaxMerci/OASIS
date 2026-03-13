package com.maxmerci.oasis.serialization.dto

import kotlinx.serialization.Serializable

@Serializable
data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null
)