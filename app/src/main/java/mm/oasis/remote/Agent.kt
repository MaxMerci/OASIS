package mm.oasis.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import mm.oasis.serialization.dto.FunctionCall
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent
import mm.oasis.serialization.dto.Request
import mm.oasis.serialization.dto.ToolCall
import mm.oasis.serialization.dto.ToolCallChunk

const val MAX_ITER = 5

class ToolCallAccumulator(val id: String) {
    var type: String? = null
    var functionName = ""
    var arguments = ""

    fun applyDelta(delta: ToolCallChunk) {
        delta.type?.let { type = it }
        delta.function?.name?.let { functionName += it }
        delta.function?.arguments?.let { arguments += it }
    }

    fun toToolCall(): ToolCall = ToolCall(id, type, FunctionCall(functionName, arguments))
}

class Accumulator {
    var content = ""
    var reasoning = ""
    val toolCalls = mutableListOf<ToolCallAccumulator>()

    fun appendToolCalls(delta: ToolCallChunk) {
        val id = delta.id ?: return
        var acc = toolCalls.find { it.id  == id }
        if (acc == null) {
            acc = ToolCallAccumulator(id)
            toolCalls.add(acc)
        }
        acc.applyDelta(delta)
    }

    fun toMessage(): Message {
        return Message(
            role = Message.MessageRole.ASSISTANT,
            content = MessageContent.Text(content),
            reasoning = reasoning.ifEmpty { null },
            toolCalls = toolCalls.map { it.toToolCall() }.ifEmpty { null }
        )
    }
}

data class ResponseFlow(
    val content: String,
    val reasoning: String,
)

object Agent {
    var isGenerating = false

    fun stop() {
        ApiClient.stop()
    }

    fun use(req: Request): Flow<ResponseFlow> = channelFlow {
        isGenerating = true

        var count = 0

        val messages = req.messages.map { it.copy() }.toMutableList()

        //ToolRegistry.register(testTool)
        //req.tools = ToolRegistry.tools

        do {
            val acc = Accumulator()
            req.messages = messages
            ApiClient.generateStream(req).collect { chunk ->
                val c = chunk.choices[0].delta.content ?: ""
                val r = chunk.choices[0].delta.reasoning ?: ""

                acc.content += c
                acc.reasoning += r
                chunk.choices[0].delta.toolCalls?.forEach { acc.appendToolCalls(it) }

                send(ResponseFlow(c, r))
            }
            if (acc.toolCalls.isEmpty() || !isGenerating) break

            messages.add(acc.toMessage())

            acc.toolCalls.forEach {
                val tool = ToolRegistry.getTool(it.functionName)
                if (tool != null) {
                    messages.add(
                        Message(
                            Message.MessageRole.TOOL,
                            MessageContent.Text(tool.execute(it.arguments).toString()),
                            toolCallId = it.id
                        )
                    )
                }
            }

//            send(
//                ResponseFlow(
//                    acc.toolCalls.joinToString("\n") { "`use " + it.functionName + "`" } + "\n",
//                    ""
//                )
//            )

            count++
        } while (count < MAX_ITER)

        isGenerating = false
        // isGenerating дополнительно выключается в ChatFragment, ибо там находится обработчик ошибкок
    }
}