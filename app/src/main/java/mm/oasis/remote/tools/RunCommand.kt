package mm.oasis.remote.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mm.oasis.remote.Workspace
import mm.oasis.serialization.dto.FunctionDefinition
import mm.oasis.serialization.dto.JsonSchema
import mm.oasis.serialization.dto.JsonSchemaProperty
import mm.oasis.serialization.dto.Tool
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

object RunCommand : ToolI {
    private const val TIMEOUT_SECONDS = 30L
    private const val OUTPUT_LIMIT = 16_000

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class Args(val command: String = "")

    override fun getTool(): Tool = Tool(
        repeatable = true,
        function = FunctionDefinition(
            name = "run_command",
            description = "Run a command in Android sh (toybox) with the workspace root as the working directory. " +
                    "Use relative paths, leaving the workspace is blocked. Read the workspace skill before the first use.",
            parameters = JsonSchema(
                type = "object",
                properties = mapOf(
                    "command" to JsonSchemaProperty(type = "string", description = "Shell command, e.g. \"ls -la input\"")
                ),
                required = listOf("command")
            )
        ),
        execute = { raw ->
            val command = json.decodeFromString<Args>(raw.ifBlank { "{}" }).command
            Workspace.violation(command)?.let { "Error: $it" } ?: exec(command)
        }
    )

    private suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val root = Workspace.dir.canonicalPath
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(Workspace.dir)
            .redirectErrorStream(true)
            .apply {
                environment().apply {
                    clear()
                    put("PATH", "/system/bin")
                    put("HOME", root)
                    put("TMPDIR", root)
                }
            }
            .start()
        process.outputStream.close()

        val out = StringBuilder()
        var truncated = false
        val reader = async {
            runCatching {
                process.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(4096)
                    while (true) {
                        val k = r.read(buf)
                        if (k < 0) break
                        val take = minOf(k, OUTPUT_LIMIT - out.length)
                        out.appendRange(buf, 0, take)
                        if (take < k) truncated = true
                    }
                }
            }
        }
        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        // фоновые потомки держат pipe открытым
        if (withTimeoutOrNull(1000.milliseconds) { reader.join() } == null) {
            process.inputStream.close()
            reader.join()
        }

        buildString {
            append(out.ifEmpty { "(no output)" })
            if (truncated) append("\n[OUTPUT TRUNCATED TO $OUTPUT_LIMIT CHARS]")
            append(if (finished) "\n[EXIT ${process.exitValue()}]" else "\n[KILLED AFTER ${TIMEOUT_SECONDS}s]")
        }
    }
}
