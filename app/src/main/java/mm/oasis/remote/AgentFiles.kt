package mm.oasis.remote

import mm.oasis.Oasis
import java.io.File

/**
 * AGENT.md and skills (skills/<name>.md).
 */
object AgentFiles {
    const val AGENT_FILE = "AGENT.md"
    private const val DESCRIPTION_LIMIT = 200

    data class Skill(val name: String, val description: String, val content: String)

    private val dir: File get() = File(Oasis.filesDir, "agent").apply { mkdirs() }
    private val skillsDir: File get() = File(dir, "skills").apply { mkdirs() }
    private val agentFile: File get() = File(dir, AGENT_FILE)

    /* AGENT.md */

    // файла нет = пользователь его не трогал, берем базовый из assets
    fun readAgent(): String =
        if (agentFile.exists()) agentFile.readText() else defaultAgent()

    fun writeAgent(text: String) = agentFile.writeText(text)

    fun resetAgent(): String {
        agentFile.delete()
        return defaultAgent()
    }

    fun defaultAgent(): String =
        Oasis.applicationContext.assets.open(AGENT_FILE).use { it.readBytes().decodeToString() }

    /* SKILLS */

    fun skills(): List<Skill> =
        skillsDir.listFiles { f -> f.isFile && f.extension == "md" }.orEmpty()
            .sortedBy { it.name }
            .map { f -> f.readText().let { Skill(f.nameWithoutExtension, describe(it), it) } }

    fun readSkill(name: String): String? =
        File(skillsDir, "${normalizeName(name)}.md").takeIf { it.isFile }?.readText()

    /** Сохраняет скил, при переименовании старый файл удаляется. Возвращает итоговое имя. */
    fun writeSkill(name: String, content: String, oldName: String? = null): String {
        val normalized = normalizeName(name)
        require(normalized.isNotEmpty()) { "EMPTY SKILL NAME" }
        if (oldName != null && normalizeName(oldName) != normalized) deleteSkill(oldName)
        File(skillsDir, "$normalized.md").writeText(content)
        return normalized
    }

    fun deleteSkill(name: String) {
        File(skillsDir, "${normalizeName(name)}.md").delete()
    }

    fun skillExists(name: String) = File(skillsDir, "${normalizeName(name)}.md").isFile

    // имя = имя файла
    fun normalizeName(name: String): String =
        name.trim().removeSuffix(".md").lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_\\-]"), "")
            .trim('_', '-')

    fun frontmatterName(content: String): String? = frontmatter(content)["name"]

    // description из frontmatter, иначе первая строка текста, которая не заголовок
    private fun describe(content: String): String {
        frontmatter(content)["description"]?.let { return it.take(DESCRIPTION_LIMIT) }
        return body(content).lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?.take(DESCRIPTION_LIMIT)
            .orEmpty()
    }

    private fun frontmatter(content: String): Map<String, String> {
        val lines = content.trimStart().lines()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap()
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end == -1) return emptyMap()
        return lines.subList(1, end + 1)
            .mapNotNull { line ->
                val key = line.substringBefore(':', "").trim()
                val value = line.substringAfter(':', "").trim().trim('"', '\'')
                if (key.isEmpty() || value.isEmpty()) null else key to value
            }
            .toMap()
    }

    private fun body(content: String): String {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("---")) return content
        val lines = trimmed.lines()
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        return if (end == -1) content else lines.drop(end + 2).joinToString("\n")
    }

    /* PROMPT */

    fun systemPrompt(skills: List<Skill>): String? {
        val agent = readAgent().trim()
        val lines = mutableListOf<String>()

        if (agent.isNotEmpty()) {
            lines += listOf(
                "# Project Context",
                "",
                "Loaded project context:",
                "$AGENT_FILE: persona/tone. Follow it unless higher-priority instructions override.",
                "",
                "## $AGENT_FILE",
                "",
                agent,
                ""
            )
        }

        if (skills.isNotEmpty()) {
            lines += listOf(
                "## Skills",
                "Scan <available_skills>. Clear match: read it with `read_skill` by exact <name>; obey.",
                "Several: most specific. None: read none.",
                "Up-front max one. Never invent names.",
                "<available_skills>"
            )
            skills.forEach { skill ->
                lines += "  <skill>"
                lines += "    <name>${skill.name}</name>"
                lines += "    <description>${skill.description}</description>"
                lines += "  </skill>"
            }
            lines += "</available_skills>"
        }

        return lines.joinToString("\n").trim().ifEmpty { null }
    }
}
