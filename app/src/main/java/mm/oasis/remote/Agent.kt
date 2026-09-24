package mm.oasis.remote

import kotlinx.coroutines.CancellationException
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
        toolCalls = toolCalls.map { it.toToolCall() }.ifEmpty { null }
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

        fun toToolCall(): ToolCall = ToolCall(id, type ?: "function", FunctionCall(functionName, arguments))
    }
}

/**
 * Цикл агента: запрос -> (вызовы инструментов -> результаты -> запрос)* -> ответ.
 * Остановка генерации = отмена корутины, которая собирает этот Flow.
 */
object Agent {
    fun use(request: Request): Flow<Message.Flow> = channelFlow {
        val req = request.copy(tools = request.tools?.ifEmpty { null })
        val messages = req.messages.toMutableList()

        for (iteration in 0 until MAX_ITER) {
            // на последней итерации инструменты не даем, модель обязана ответить текстом
            if (iteration == MAX_ITER - 1) req.tools = null
            req.messages = messages

            val acc = Acc()
            ApiClient.generateTextStream(req).collect { chunk ->
                val delta = chunk.choices.firstOrNull()?.delta ?: return@collect
                val c = delta.content.orEmpty()
                val r = delta.reasoning.orEmpty()

                acc.content += c
                acc.reasoning += r
                delta.toolCalls?.forEach { acc.appendToolCalls(it) }

                if (c.isNotEmpty() || r.isNotEmpty()) send(Message.Flow(c, r))
            }
            if (acc.toolCalls.isEmpty()) break  // дальше ТОЛЬКО обработка инструментов

            messages.add(acc.toMessage())
            send(Message.Flow(
                reasoning = "\n\n" + acc.toolCalls.joinToString { "use ${it.functionName} ${it.arguments}" } + "\n\n",
                toolCalls = acc.toolCalls.map { it.toToolCall() }
            ))

            // использованный инструмент убираем, иначе модели любят зацикливаться
            val used = acc.toolCalls.map { it.functionName }.toSet()
            req.tools = req.tools?.filter { it.function.name !in used }?.ifEmpty { null }

            // на КАЖДЫЙ tool_call обязан быть ответ, иначе API вернет 400
            val results = acc.toolCalls.map { call ->
                async(Dispatchers.IO) {
                    Message(
                        Message.MessageRole.TOOL,
                        MessageContent.Text(execute(call.functionName, call.arguments)),
                        toolCallId = call.id
                    )
                }
            }.awaitAll()
            messages.addAll(results)
        }
    }

    private suspend fun execute(name: String, arguments: String): String {
        val tool = ToolRegistry.getTool(name) ?: return "Error: unknown tool '$name'"
        return try {
            tool.execute(arguments)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message ?: e.toString()}"
        }
    }
}
