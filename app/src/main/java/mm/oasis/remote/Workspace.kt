package mm.oasis.remote

import mm.oasis.Oasis
import java.io.File

/**
 * Agent work directory
 */
object Workspace {
    private const val LIST_LIMIT = 50
    private val ALLOWED = listOf("/system/", "/dev/null")
    private val ABSOLUTE = Regex("""(?<![\w.$}:/<])/[^\s'"`;|&<>()]*""")
    private val HEREDOC = Regex("""<<-?[ \t]*(['"]?)(\w+)\1([^\n]*)\n[\s\S]*?\n[ \t]*\2[ \t]*(?=\n|$)""")
    private val PARENT = Regex("""(^|[\s/'"=:])\.\.($|[\s/'"])""")

    val dir: File get() = File(Oasis.filesDir, "workspace").apply { mkdirs() }
    val input: File get() = File(dir, "input")

    fun resolve(path: String): File? {
        val root = dir.canonicalFile
        val file = File(root, path.trim().removePrefix("/")).canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) return null
        return file.takeIf { it.isFile }
    }

    fun relativePath(file: File): String = file.canonicalFile.relativeTo(dir.canonicalFile).path

    fun files(): List<String> =
        dir.walkTopDown().filter { it.isFile }.take(LIST_LIMIT).map { relativePath(it) }.sorted().toList()

    fun isEmpty(): Boolean = dir.walkTopDown().none { it.isFile }

    fun inputFiles(): List<String> = input.listFiles().orEmpty().filter { it.isFile }.map { relativePath(it) }.sorted()

    // защита от случайного выхода за воркспейс, а не песочница: путь, собранный в рантайме, не поймает
    fun violation(command: String): String? {
        val code = HEREDOC.replace(command) { it.groupValues[3] }
        if (PARENT.containsMatchIn(code) || '~' in code) return "'..' and '~' are not allowed outside heredoc bodies"
        val root = dir.canonicalPath
        val path = ABSOLUTE.findAll(code).firstOrNull { m ->
            val p = m.value
            p != root && !p.startsWith("$root/") && ALLOWED.none(p::startsWith) &&
                    if (p == "/") code.getOrNull(m.range.last + 1).let { it == null || it.isWhitespace() || it in ";&|)" }
                    else p.any { it in "*?[" } || File("/" + p.trim('/').substringBefore('/')).exists()
        }?.value
        return path?.let { "command rejected, '$it' points outside the workspace. Use relative paths; heredoc bodies are not checked" }
    }
}
