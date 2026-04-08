package mm.oasis.remote

import mm.oasis.remote.tools.ToolI
import mm.oasis.serialization.dto.Tool

object ToolRegistry {
    val tools = mutableListOf<Tool>()

    fun register(tool: Tool) {
        tools.add(tool)
    }

    fun getTool(name: String): Tool? = tools.find { it.function.name == name }
}


fun registerTools(tools: List<ToolI>) {
    tools.forEach { c -> ToolRegistry.register(c.getTool()) }
}

