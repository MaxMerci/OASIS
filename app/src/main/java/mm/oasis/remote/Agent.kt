package mm.oasis.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import mm.oasis.remote.tools.LinkFile
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

    fun toMessage(calls: List<ToolCall>): Message = Message(
        Message.MessageRole.ASSISTANT,
        MessageContent.Text(content),
        toolCalls = calls.ifEmpty { null }
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
        // инструменты, которым сейчас нечего делать (нет скиллов, пустой workspace), модели не даем
        val (system, available) = withContext(Dispatchers.IO) {
            AgentFiles.systemPrompt(AgentFiles.skills(), Workspace.files()) to
                    request.tools.orEmpty().filter { it.available() }.distinctBy { it.function.name }
        }

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

            val calls = acc.toolCalls.map { it.toToolCall() }
            messages.add(acc.toMessage(calls))
            send(Message.Flow(toolCalls = calls))

            val used = acc.toolCalls.map { it.functionName }
                .filter { name -> available.none { it.function.name == name && it.repeatable } }
                .toSet()
            req.tools = req.tools?.filter { it.function.name !in used }?.ifEmpty { null }

            val results = calls.map { call ->
                async(Dispatchers.IO) {
                    call to execute(available, call.function?.name.orEmpty(), call.function?.arguments.orEmpty())
                }
            }.awaitAll()
            messages.addAll(results.map { (call, result) ->
                Message(Message.MessageRole.TOOL, MessageContent.Text(result), toolCallId = call.id)
            })

            val files = calls
                .filter { it.function?.name == LinkFile.NAME }
                .mapNotNull { LinkFile.linkedPath(it.function?.arguments.orEmpty()) }
            send(Message.Flow(toolResults = results, files = files))
        }
    }

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
