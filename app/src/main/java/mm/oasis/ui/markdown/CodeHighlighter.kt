package mm.oasis.ui.markdown

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import mm.oasis.R

object CodeHighlighter {
    private val HASH_COMMENTS = setOf(
        "python", "py", "bash", "sh", "shell", "zsh", "ruby", "rb", "yaml", "yml", "toml",
        "r", "perl", "pl", "dockerfile", "makefile", "make", "conf", "ini", "nix", "elixir", "ex"
    )
    private val DASH_COMMENTS = setOf("sql", "lua", "haskell", "hs")
    // в обычном тексте апострофы и скобки не код, подсветка только мешает
    private val PLAIN = setOf("text", "txt", "plain", "plaintext", "md", "markdown", "log", "output", "console")

    private val KEYWORDS = listOf(
        "abstract", "and", "as", "assert", "async", "await", "break", "case", "catch", "chan", "class",
        "companion", "const", "continue", "data", "def", "default", "defer", "del", "delete", "do", "elif",
        "else", "enum", "except", "export", "extends", "extern", "false", "fi", "final", "finally", "fn",
        "for", "from", "fun", "func", "function", "go", "goto", "if", "impl", "implements", "import", "in",
        "init", "inline", "interface", "internal", "is", "lambda", "let", "loop", "match", "mod", "mut",
        "namespace", "new", "nil", "none", "not", "null", "object", "open", "operator", "or", "override",
        "package", "pass", "private", "protected", "pub", "public", "raise", "range", "return", "sealed",
        "select", "self", "static", "struct", "super", "suspend", "switch", "then", "this", "throw",
        "throws", "trait", "true", "try", "type", "typeof", "undefined", "use", "val", "var", "void",
        "when", "where", "while", "with", "yield", "None", "True", "False", "int", "long", "float",
        "double", "bool", "boolean", "char", "string", "byte", "short", "unsigned", "echo", "local",
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
        "TABLE", "DROP", "ALTER", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "GROUP", "BY",
        "ORDER", "LIMIT", "AND", "OR", "NOT", "NULL", "AS", "DISTINCT", "PRIMARY", "KEY", "INDEX"
    ).joinToString("|")

    private val patterns = HashMap<String, Regex>()

    private fun pattern(language: String): Regex = patterns.getOrPut(language) {
        val comment = buildList {
            if (language in HASH_COMMENTS) add("#[^\\n]*")
            else add("//[^\\n]*")
            if (language in DASH_COMMENTS) add("--[^\\n]*")
            if (language !in HASH_COMMENTS) add("/\\*[\\s\\S]*?(?:\\*/|$)")
            add("<!--[\\s\\S]*?(?:-->|$)")
        }.joinToString("|")

        Regex(
            "(?<comment>$comment)" +
                    "|(?<string>\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?|`(?:\\\\.|[^`\\\\])*`?)" +
                    "|(?<number>\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)[fFlLuU]?\\b)" +
                    "|(?<keyword>\\b(?:$KEYWORDS)\\b)" +
                    "|(?<call>\\b[A-Za-z_][A-Za-z0-9_]*(?=\\s*\\())"
        )
    }

    fun highlight(context: Context, code: String, language: String): CharSequence {
        if (language.lowercase() in PLAIN) return code
        val spannable = SpannableString(code)
        val colors = mapOf(
            "comment" to context.getColor(R.color.code_comment),
            "string" to context.getColor(R.color.code_string),
            "number" to context.getColor(R.color.code_number),
            "keyword" to context.getColor(R.color.code_keyword),
            "call" to context.getColor(R.color.code_function),
        )

        pattern(language.lowercase()).findAll(code).forEach { match ->
            val group = colors.keys.firstOrNull { match.groups[it] != null } ?: return@forEach
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(ForegroundColorSpan(colors.getValue(group)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (group == "comment") {
                spannable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return spannable
    }
}
