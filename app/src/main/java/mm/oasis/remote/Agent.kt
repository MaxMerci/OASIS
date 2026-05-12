package mm.oasis.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import mm.oasis.serialization.dto.*

private const val MAX_ITER = 4

private class Acc {
    var content = ""
    var reasoning = ""
    val toolCalls = mutableListOf<ToolCallAcc>()

    fun appendToolCalls(delta: ToolCallChunk) {
        var acc = toolCalls.find { it.index == delta.index }
        if (acc == null) {
            acc = ToolCallAcc(delta.index)
            toolCalls.add(acc)
        }
        acc.applyDelta(delta)
    }

    fun toMessage(): Message = Message(
        Message.MessageRole.ASSISTANT,
        MessageContent.Text(content),
        reasoning.ifEmpty { null },
        toolCalls.map { it.toToolCall() }.ifEmpty { null }
    )

    class ToolCallAcc(val index: Int) {
        var type: String? = null
        var functionName = ""
        var arguments = ""
        var id = ""

        fun applyDelta(delta: ToolCallChunk) {
            delta.id?.let { id += it }
            delta.type?.let { type = it }
            delta.function?.name?.let { functionName += it }
            delta.function?.arguments?.let { arguments += it }
        }

        fun toToolCall(): ToolCall = ToolCall(id, type, FunctionCall(functionName, arguments))
    }
}

object Agent {
    var isGenerating = false

    fun stop() {
        ApiClient.stop()
    }

    fun use(req: Request): Flow<Message.Flow> = channelFlow {
        isGenerating = true

        var count = 0

        val messages = req.messages.map { it.copy() }.toMutableList()

        do {
            req.messages = messages
            val acc = Acc()
            println(req.messages)
            ApiClient.generateTextStream(req).collect { chunk ->
                val c = chunk.choices[0].delta.content ?: ""
                val r = chunk.choices[0].delta.reasoning ?: ""

                acc.content += c
                acc.reasoning += r
                chunk.choices[0].delta.toolCalls?.forEach { acc.appendToolCalls(it) }

                if (c.isNotEmpty() || r.isNotEmpty())
                    send(Message.Flow(c, r))
            }
            if (acc.toolCalls.isEmpty() || !isGenerating) break  // дальше ТОЛЬКО обработка инструментов

            messages.add(acc.toMessage())
            send(Message.Flow(
                reasoning = "\n\n" + acc.toolCalls.joinToString { "use ${it.functionName}" },
                toolCalls = acc.toolCalls.map { it.toToolCall() }
            ))

            val jobs = acc.toolCalls.mapNotNull { call ->
                val tool = ToolRegistry.getTool(call.functionName) ?: return@mapNotNull null
                req.tools = req.tools!! - tool
                async(Dispatchers.IO) {
                    val result = tool.execute(call.arguments)
                    Message(
                        Message.MessageRole.TOOL,
                        MessageContent.Text(result),
                        toolCallId = call.id
                    )
                }
            }
            val results = jobs.awaitAll()
            messages.addAll(results)

            count++
        } while (MAX_ITER > count)

        isGenerating = false
        // isGenerating дополнительно выключается в ChatFragment, ибо там находится обработчик ошибкок
    }
}