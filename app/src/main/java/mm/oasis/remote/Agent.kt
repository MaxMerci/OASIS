package mm.oasis.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import mm.oasis.remote.tools.ReadSkill
import mm.oasis.serialization.dto.*

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

object Agent {
    fun use(request: Request): Flow<Message.Flow> = channelFlow {
        // файлы агентной системы подмешиваются только в запрос, в чате их нет
        val (system, skills) = withContext(Dispatchers.IO) {
            val skills = AgentFiles.skills()
            AgentFiles.systemPrompt(skills) to skills
        }
        val available = request.tools.orEmpty() +
                (if (skills.isNotEmpty()) listOf(ReadSkill.getTool()) else emptyList())

        val req = request.copy(tools = available.ifEmpty { null })
        val messages = req.messages.toMutableList()
        system?.let { messages.add(0, Message(Message.MessageRole.SYSTEM, MessageContent.Text(it))) }

        val maxIterations = request.maxIterations.coerceAtLeast(1)
        for (iteration in 0 until maxIterations) {
            // на последней итерации инструменты не даем, модель обязана ответить текстом
            if (iteration == maxIterations - 1) {
                req.tools = null
                if (iteration > 0) notifyLimit(messages, maxIterations)
            }
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
            if (acc.toolCalls.isEmpty()) break

            messages.add(acc.toMessage())
            send(Message.Flow(
                reasoning = "\n\n" + acc.toolCalls.joinToString { "use ${it.functionName} ${it.arguments}" } + "\n\n",
                toolCalls = acc.toolCalls.map { it.toToolCall() }
            ))

            // использованный инструмент убираем, иначе модели любят зацикливаться; безопасные остаются
            val used = acc.toolCalls.map { it.functionName }
                .filter { name -> available.none { it.function.name == name && it.repeatable } }
                .toSet()
            req.tools = req.tools?.filter { it.function.name !in used }?.ifEmpty { null }

            val results = acc.toolCalls.map { call ->
                async(Dispatchers.IO) {
                    Message(
                        Message.MessageRole.TOOL,
                        MessageContent.Text(execute(available, call.functionName, call.arguments)),
                        toolCallId = call.id
                    )
                }
            }.awaitAll()
            messages.addAll(results)
        }
    }

    // лимит сообщаем в результате последнего инструмента: system посреди диалога многие API не принимают
    private fun notifyLimit(messages: MutableList<Message>, maxIterations: Int) {
        val index = messages.indexOfLast { it.role == Message.MessageRole.TOOL }
        if (index == -1) return
        val tool = messages[index]
        messages[index] = tool.copy(content = MessageContent.Text(
            tool.display + "\n\n[TOOL CALL LIMIT REACHED: $maxIterations iterations. " +
                    "Tools are no longer available. Answer now using the information you already have.]"
        ))
    }

    private suspend fun execute(tools: List<Tool>, name: String, arguments: String): String {
        val tool = tools.find { it.function.name == name } ?: return "Error: unknown tool '$name'"
        return try {
            tool.execute(arguments)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: ${e.message ?: e.toString()}"
        }
    }
}
