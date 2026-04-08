package mm.oasis.remote.tools

import mm.oasis.serialization.dto.Tool

sealed interface ToolI {
    fun getTool(): Tool
}