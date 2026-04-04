package mm.oasis.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mm.oasis.serialization.dto.FunctionCall
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent
import mm.oasis.serialization.dto.Request
import mm.oasis.serialization.dto.ToolCall
import mm.oasis.serialization.dto.ToolCallChunk


const val MAX_ITER = 4

class ToolCallAccumulator(val index: Int) {
    var type: String? = null
    var functionName = ""
    var arguments = ""

    var id = ""

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
        var acc = toolCalls.find { it.index == delta.index }
        if (acc == null) {
            acc = ToolCallAccumulator(delta.index)
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
    val content: String = "",
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = listOf()
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

        do {
            if (count > MAX_ITER) {
                req.tools = listOf()
            }

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
                if (tool != null) launch {
                    val result = withContext(Dispatchers.IO) { tool.execute(it.arguments) }
                    messages.add(
                        Message(
                            Message.MessageRole.TOOL,
                            MessageContent.Text(result),
                            toolCallId = it.id
                        )
                    )
                }
            }

            send(ResponseFlow(
                reasoning = "\n\n",
                toolCalls = acc.toolCalls.map { it.toToolCall() }
            ))

//            send(
//                ResponseFlow(
//                    acc.toolCalls.joinToString("\n") { "`use " + it.functionName + "`" } + "\n",
//                    ""
//                )
//            )

            count++
        } while (true)

        isGenerating = false
        // isGenerating дополнительно выключается в ChatFragment, ибо там находится обработчик ошибкок
    }
}