package mm.oasis.remote

import mm.oasis.Oasis
import java.io.File

/**
 * Agent work directory
 */
object Workspace {
    private const val LIST_LIMIT = 50

    val dir: File get() = File(Oasis.filesDir, "workspace").apply { mkdirs() }

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
}
