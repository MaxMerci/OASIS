package mm.oasis.ui.chat.message

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.serialization.dto.Message
import kotlin.math.abs
import androidx.core.view.isVisible

/* дело такое, AssistantViewHolder постоянно пересоздается и хранить такую переменную в нем просто бесполезно,
так как класс будет пересоздаватся и значение всегда будет -1
самый простой способ как по мне был вот такой:
*/
var lastReasoningIndex =  -1

class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val avatarView: ImageView = view.findViewById(R.id.avatar)
    private val nameView: TextView = view.findViewById(R.id.name)
    private val contentView: TextView = view.findViewById(R.id.content)

    private val reasoningContainer: FrameLayout = view.findViewById(R.id.reasoningContainer)
    private val reasoningCurrent: TextView = view.findViewById(R.id.reasoningCurrent)
    private val reasoningNext: TextView = view.findViewById(R.id.reasoningNext)

    private var heightAnimator: ValueAnimator? = null

    /**
     * иногда ллм могут писать неправильное форматирование
     * это не латекс, а LaTeX, что является языком форматировния
     * оно кстати не нужно, потомучто LaTeX тут просто не работает ;)
     * */
    fun latexFix(text: String): String {
        val regex = Regex("""(?<!\\)\$((?:[^$]|\\\$)+?)(?<!\\)\$""")
        return regex.replace(text) {
            val inner = it.groupValues[1]
            if (inner.contains("\n")) "$$$inner$$" else "$${inner.trim()}$"
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?) {
        val content = latexFix(message.display)
        val reasoning = message.reasoning

        Glide.with(itemView.context)
            .load(message.avatarUrl)
            .centerCrop()
            .into(avatarView)

        nameView.text = "[${message.name ?: "ASSISTANT"}] >"

        if (content.isEmpty() && reasoning.isNullOrEmpty()) {
            contentView.text = "Thinks..."
        } else {
            markwon?.setMarkdown(contentView, content)
        }

        if (!reasoning.isNullOrBlank()) {
            val parts = reasoning.split("\n\n").filter { it.isNotBlank() }

            val targetIndex = when {
                parts.size >= 2 -> parts.size - 2
                parts.size == 1 -> 0
                else -> -1
            }

            val newText = if (targetIndex != -1) parts[targetIndex].trim() else null

            if (newText != null && lastReasoningIndex != targetIndex) {
                lastReasoningIndex = targetIndex
                animateTextChange(newText, markwon)
            } else if (newText != null && targetIndex == 0) {
                markwon?.setMarkdown(reasoningCurrent, newText) // без анимации, обновление первого абзаца
            }

            if (content.isNotBlank()) {
                hideReasoning()
            } else {
                showReasoning()
            }
        } else {
            hideReasoning()
        }
    }

    private fun animateTextChange(newText: String, markwon: Markwon?) {
        reasoningNext.alpha = 0f
        markwon?.setMarkdown(reasoningNext, newText)

        reasoningNext.post {
            val targetHeight = reasoningNext.measuredHeight
            animateHeight(targetHeight)
        }

        reasoningNext.animate().alpha(1f).setDuration(200).start()
        reasoningCurrent.animate().alpha(0f).setDuration(200).withEndAction {
            reasoningCurrent.text = reasoningNext.text
            reasoningCurrent.alpha = 1f
            reasoningNext.alpha = 0f
        }.start()
    }

    private fun animateHeight(target: Int) {
        val start = reasoningContainer.height

        if (abs(start - target) < 10) return

        heightAnimator?.cancel()

        heightAnimator = ValueAnimator.ofInt(start, target).apply {
            duration = 200
            addUpdateListener {
                val value = it.animatedValue as Int
                val params = reasoningContainer.layoutParams
                params.height = value
                reasoningContainer.layoutParams = params
            }
            start()
        }
    }

    private fun showReasoning() {
        if (reasoningContainer.isVisible) return

        reasoningContainer.alpha = 0f
        reasoningContainer.visibility = View.VISIBLE
        reasoningContainer.animate().alpha(1f).setDuration(200).start()
    }

    private fun hideReasoning() {
        if (reasoningContainer.visibility != View.VISIBLE) return

        reasoningContainer.animate().alpha(0f).setDuration(200).withEndAction {
            reasoningContainer.visibility = View.GONE
        }.start()
    }
}