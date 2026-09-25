package mm.oasis.ui.chat.message

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mm.oasis.R
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.ToolCall


class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    companion object {
        const val CHANGE_DURATION = 500L
    }

    private val avatarView: ImageView = view.findViewById(R.id.avatar)
    private val nameView: TextView = view.findViewById(R.id.name)
    private val toolsView: TextView = view.findViewById(R.id.tools)
    private val contentView: TextView = view.findViewById(R.id.content)
    /* REASONING */
    private val reasoningCurrent: TextView = view.findViewById(R.id.reasoningCurrent)
    private val reasoningNext: TextView = view.findViewById(R.id.reasoningNext)
    private val reasoningContainer: FrameLayout = view.findViewById(R.id.reasoning)

    private var boundMessage: Message? = null
    private var markwon: Markwon? = null
    private var reasoningShown = false
    private var contentShown = false
    private var currentParagraph: String? = null
    private var targetParagraph: String? = null
    private var paragraphAnimating = false
    private var heightAnimator: ValueAnimator? = null

    fun latexFix(text: String): String {
        val regex = Regex("""(?<!\\)\$((?:[^$]|\\\$)+?)(?<!\\)\$""")
        return regex.replace(text) {
            val inner = it.groupValues[1]
            if (inner.contains("\n")) "$$$inner$$" else "$${inner.trim()}$"
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?) {
        this.markwon = markwon
        val animate = message === boundMessage
        if (!animate) reset()
        boundMessage = message

        /* SET BASE FIELDS */
        val newUrl = message.avatarUrl
        if ((avatarView.tag as? String) != newUrl) {
            avatarView.tag = newUrl
            Glide.with(itemView.context)
                .load(newUrl)
                .centerCrop()
                .into(avatarView)
        }
        nameView.text = "[${message.name ?: "ASSISTANT"}] >"

        val tools = message.toolCalls.orEmpty().joinToString("\n") { formatToolCall(it) }
        toolsView.text = tools
        toolsView.visibility = if (tools.isEmpty()) GONE else VISIBLE

        val content = latexFix(message.display)
        val reasoning = message.reasoning

        if (content.isBlank() && !reasoning.isNullOrBlank()) {
            val parts = reasoning.split("\n\n").filter { it.isNotBlank() }
            val paragraph = parts[if (parts.size >= 2) parts.size - 2 else 0].trim()
            if (!reasoningShown) showReasoning(animate)
            if (animate) {
                targetParagraph = paragraph
                reasoningContainer.post { if (boundMessage === message) changeReasoningParagraph() }
            } else {
                setParagraph(paragraph)
            }
        } else if (reasoningShown) {
            hideReasoning(animate)
        }

        if (content.isNotBlank()) {
            if (!contentShown) showContent(animate)
            markwon?.setMarkdown(contentView, content) ?: run {
                contentView.text = content
            }
        } else {
            contentView.text = ""
        }
    }

    private fun formatToolCall(call: ToolCall): String {
        val name = call.function?.name ?: "tool"
        val args = call.function?.arguments.orEmpty()
        val readable = try {
            (Json.parseToJsonElement(args) as JsonObject).values.joinToString(", ") {
                if (it is JsonPrimitive) it.content else it.toString()
            }
        } catch (e: Exception) {
            args
        }
        return "> $name: ${readable.take(120)}"
    }

    private fun reset() {
        heightAnimator?.cancel()
        heightAnimator = null
        reasoningContainer.animate().cancel()
        reasoningCurrent.animate().cancel()
        reasoningNext.animate().cancel()
        contentView.animate().cancel()

        reasoningShown = false
        contentShown = false
        currentParagraph = null
        targetParagraph = null
        paragraphAnimating = false

        reasoningContainer.apply {
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            alpha = 0f
            visibility = GONE
        }
        reasoningCurrent.apply {
            alpha = 1f
            text = null
            visibility = VISIBLE
        }
        reasoningNext.apply {
            alpha = 0f
            text = null
            visibility = GONE
        }
        contentView.apply {
            alpha = 0f
            visibility = GONE
        }
    }

    private fun showContent(animate: Boolean) {
        contentShown = true
        contentView.animate().cancel()
        contentView.visibility = VISIBLE
        if (animate) {
            contentView.alpha = 0f
            contentView.animate().alpha(1f).setDuration(CHANGE_DURATION).start()
        } else {
            contentView.alpha = 1f
        }
    }

    private fun showReasoning(animate: Boolean) {
        reasoningShown = true
        heightAnimator?.cancel()
        reasoningContainer.animate().cancel()
        reasoningContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        reasoningContainer.visibility = VISIBLE
        if (animate) {
            reasoningContainer.alpha = 0f
            reasoningContainer.animate().alpha(1f).setDuration(CHANGE_DURATION).start()
        } else {
            reasoningContainer.alpha = 1f
        }
    }

    private fun hideReasoning(animate: Boolean) {
        reasoningShown = false
        targetParagraph = null
        heightAnimator?.cancel()
        reasoningContainer.animate().cancel()
        reasoningCurrent.animate().cancel()
        reasoningNext.animate().cancel()
        paragraphAnimating = false

        if (!animate) {
            reasoningContainer.visibility = GONE
            reasoningContainer.alpha = 0f
            return
        }

        val animator = ValueAnimator.ofInt(reasoningContainer.height, 0)
        animator.duration = CHANGE_DURATION
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            reasoningContainer.layoutParams.height = it.animatedValue as Int
            reasoningContainer.requestLayout()
        }
        heightAnimator = animator

        reasoningContainer.animate()
            .alpha(0f)
            .setDuration(CHANGE_DURATION)
            .withEndAction {
                reasoningContainer.visibility = GONE
            }
            .start()

        animator.start()
    }

    private fun setMarkdown(view: TextView, text: String) {
        markwon?.setMarkdown(view, text) ?: run {
            view.text = text
        }
    }

    private fun setParagraph(text: String) {
        currentParagraph = text
        targetParagraph = text
        setMarkdown(reasoningCurrent, text)
    }

    private fun changeReasoningParagraph() {
        if (!reasoningShown || paragraphAnimating) return
        val newText = targetParagraph ?: return
        if (currentParagraph == newText) return

        // контейнер еще не разложен (только что показали) - меряться не с чем, ставим без анимации
        val width = reasoningContainer.width
        if (width <= 0 || currentParagraph == null) {
            setParagraph(newText)
            return
        }

        paragraphAnimating = true
        setMarkdown(reasoningNext, newText)

        val wSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        reasoningNext.measure(wSpec, hSpec)

        val nHeight = reasoningNext.measuredHeight
        val oHeight = reasoningContainer.height

        reasoningNext.apply {
            alpha = 0f
            visibility = VISIBLE
        }

        heightAnimator?.cancel()
        val hAnimator = ValueAnimator.ofInt(oHeight, nHeight)
        hAnimator.duration = CHANGE_DURATION
        hAnimator.interpolator = DecelerateInterpolator()
        hAnimator.addUpdateListener {
            reasoningContainer.layoutParams.height = it.animatedValue as Int
            reasoningContainer.requestLayout()
        }
        heightAnimator = hAnimator

        reasoningCurrent.animate()
            .alpha(0f)
            .setDuration(CHANGE_DURATION)
            .start()

        reasoningNext.animate()
            .alpha(1f)
            .setDuration(CHANGE_DURATION)
            .withEndAction {
                paragraphAnimating = false
                currentParagraph = newText
                setMarkdown(reasoningCurrent, newText)
                reasoningCurrent.apply {
                    alpha = 1f
                    visibility = VISIBLE
                }
                reasoningNext.apply {
                    visibility = GONE
                    alpha = 0f
                    text = null
                }

                reasoningContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                reasoningContainer.requestLayout()

                // пока шла анимация, мог прийти следующий абзац
                changeReasoningParagraph()
            }
            .start()

        hAnimator.start()
    }
}
