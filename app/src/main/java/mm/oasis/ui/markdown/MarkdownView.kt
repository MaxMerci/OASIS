package mm.oasis.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.noties.markwon.Markwon
import mm.oasis.R

class MarkdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    sealed class Segment {
        data class Text(val text: String) : Segment()
        data class Code(val language: String, val code: String) : Segment()
    }

    companion object {
        private val FENCE = Regex("^(\\s*)(`{3,}|~{3,})(.*)$")
        private const val COPIED_DURATION = 1500L

        fun parse(markdown: String): List<Segment> {
            val segments = mutableListOf<Segment>()
            val text = StringBuilder()
            val lines = markdown.split('\n')

            fun flushText() {
                val value = text.toString().trim('\n')
                if (value.isNotBlank()) segments += Segment.Text(value)
                text.clear()
            }

            var i = 0
            while (i < lines.size) {
                val match = FENCE.matchEntire(lines[i])
                val info = match?.groupValues?.get(3).orEmpty()
                if (match == null || (match.groupValues[2][0] == '`' && info.contains('`'))) {
                    text.append(lines[i]).append('\n')
                    i++
                    continue
                }

                flushText()
                val indent = match.groupValues[1].length
                val fence = match.groupValues[2]
                val code = mutableListOf<String>()
                i++
                // незакрытый блок (модель еще пишет) идет до конца текста
                while (i < lines.size) {
                    val line = lines[i]
                    val trimmed = line.trim()
                    i++
                    if (trimmed.length >= fence.length && trimmed.all { it == fence[0] }) break
                    code += line.drop(line.takeWhile { it == ' ' }.length.coerceAtMost(indent))
                }
                segments += Segment.Code(info.trim().substringBefore(' '), code.joinToString("\n"))
            }
            flushText()
            return segments
        }

        // $...$ в одну строку - инлайн формула, многострочная - блочная
        fun latexFix(text: String): String {
            val regex = Regex("""(?<!\\)\$((?:[^$]|\\\$)+?)(?<!\\)\$""")
            return regex.replace(text) {
                val inner = it.groupValues[1]
                if (inner.contains("\n")) "$$$inner$$" else "$${inner.trim()}$"
            }
        }
    }

    var markwon: Markwon? = null
    var onLongClick: OnLongClickListener? = null

    init {
        orientation = VERTICAL
    }

    fun setMarkdown(markdown: String) {
        val segments = parse(markdown)
        segments.forEachIndexed { index, segment ->
            val child = getChildAt(index)
            if (child == null || child.tag?.javaClass != segment.javaClass) {
                while (childCount > index) removeViewAt(childCount - 1)
                addView(create(segment))
            }
            update(getChildAt(index), segment)
        }
        while (childCount > segments.size) removeViewAt(childCount - 1)
    }

    fun clear() = removeAllViews()

    private fun create(segment: Segment): View {
        val inflater = LayoutInflater.from(context)
        val view = when (segment) {
            is Segment.Text -> inflater.inflate(R.layout.view_markdown_text, this, false)
            is Segment.Code -> inflater.inflate(R.layout.view_code_block, this, false).also { block ->
                block.findViewById<TextView>(R.id.codeText).setOnLongClickListener { onLongClick?.onLongClick(this) ?: false }
            }
        }
        view.setOnLongClickListener { onLongClick?.onLongClick(this) ?: false }
        return view
    }

    private fun update(view: View, segment: Segment) {
        if (view.tag == segment) return
        view.tag = segment
        when (segment) {
            is Segment.Text -> {
                val textView = view as TextView
                val text = latexFix(segment.text)
                markwon?.setMarkdown(textView, text) ?: run { textView.text = text }
            }
            is Segment.Code -> {
                view.findViewById<TextView>(R.id.codeLanguage).text = segment.language.ifEmpty { "code" }
                view.findViewById<TextView>(R.id.codeText).text =
                    CodeHighlighter.highlight(context, segment.code, segment.language)
                val copy = view.findViewById<TextView>(R.id.codeCopy)
                copy.setOnClickListener {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", segment.code))
                    copy.text = "COPIED"
                    copy.removeCallbacks(copy.tag as? Runnable)
                    val restore = Runnable { copy.text = "COPY" }
                    copy.tag = restore
                    copy.postDelayed(restore, COPIED_DURATION)
                }
            }
        }
    }
}
