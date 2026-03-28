package mm.oasis.remote

import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Tool

object ToolRegistry {
    val tools = mutableListOf<Tool>()

    fun register(tool: Tool) {
        tools.add(tool)
    }

    fun getTool(name: String): Tool? = tools.find { it.function.name == name }
}

val testTool = Tool(
    function = FunctionDefinition(
        name = "process_status",
        description = "Get process status",
        parameters = JsonSchema(
            type = "object",
            properties = mapOf(
                "id" to JsonSchemaProperty(
                    type = "int",
                    description = "Process ID"
                )
            ),
            required = listOf("id")
        )
    ),
    execute = { args ->
        // о да мой блютуз, он самый
        "FIND PROCESS WITH ID 1337 \"bluetooth\":\n" +
        "Loaded: loaded (/usr/lib/systemd/system/bluetooth.service; enabled; preset: disabled)\n" +
        "Active: active (running) since Sat 2026-03-28 06:41:02 UTC; 4h 0min ago\n" +
        "Docs: man:bluetoothd(8)\n" +
        "Status: \"Running\"\n" +
        "Tasks: 1 (limit: 38211)\n" +
        "Memory: 2.5M (peak: 4.4M)\n" +
        "CPU: 2.819s\n" +
        "CGroup: /system.slice/bluetooth.service"
    }
)