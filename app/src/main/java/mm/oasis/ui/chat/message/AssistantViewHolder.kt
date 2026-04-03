package mm.oasis.ui.chat.message

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.View.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.serialization.dto.Message


class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    companion object {
        const val CHANGE_DURATION = 500L
    }

    private val avatarView: ImageView = view.findViewById(R.id.avatar)
    private val nameView: TextView = view.findViewById(R.id.name)
    private val contentView: TextView = view.findViewById(R.id.content)
    /* REASONING */
    private val reasoningCurrent: TextView = view.findViewById(R.id.reasoningCurrent)
    private val reasoningNext: TextView = view.findViewById(R.id.reasoningNext)
    private val reasoningContainer: FrameLayout = view.findViewById(R.id.reasoning)

    fun latexFix(text: String): String {
        val regex = Regex("""(?<!\\)\$((?:[^$]|\\\$)+?)(?<!\\)\$""")
        return regex.replace(text) {
            val inner = it.groupValues[1]
            if (inner.contains("\n")) "$$$inner$$" else "$${inner.trim()}$"
        }
    }

    private fun expand(view: View) {
        view.alpha = 0f
        view.visibility = VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(250)
            .start()
    }

    private fun collapse(view: View) {
        val initialHeight = view.measuredHeight

        val animator = ValueAnimator.ofInt(initialHeight, 0)
        animator.duration = CHANGE_DURATION
        animator.interpolator = DecelerateInterpolator()

        animator.addUpdateListener {
            val value = it.animatedValue as Int
            view.layoutParams.height = value
            view.requestLayout()
        }

        view.animate()
            .alpha(0f)
            .setDuration(CHANGE_DURATION)
            .withEndAction {
                view.visibility = GONE
            }
            .start()

        animator.start()
    }

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?) {
        /* SET BASE FIELDS */
        val newUrl = message.avatarUrl
        if ((avatarView.tag as? String) != newUrl) {
            avatarView.tag = newUrl
            Glide.with(itemView.context)
                .load(newUrl)
                .centerCrop()
                .into(avatarView)
        }
        if (nameView.text != null) {
            nameView.text = "[${message.name ?: "ASSISTANT"}] >"
        }

        val content = latexFix(message.display)
        val reasoning = message.reasoning

        if (!reasoning.isNullOrBlank() && content.isBlank()) {
            if (!reasoningContainer.isVisible) expand(reasoningContainer)

            val parts = message.reasoning?.split("\n\n")?.filter { it.isNotBlank() } ?: listOf()
            val targetIndex = when {
                parts.size >= 2 -> parts.size - 2
                else -> 0
            }
            val newText = parts[targetIndex].trim()
            if (reasoningCurrent.text != newText) {
                changeReasoningParagraph(newText)
            }
        } else if (content.isNotBlank()) {
            if (reasoningContainer.alpha == 1f) collapse(reasoningContainer)
            if (!contentView.isVisible) contentView.apply {
                visibility = VISIBLE
                animate().alpha(1f).setDuration(CHANGE_DURATION).start()
            }
            markwon?.setMarkdown(contentView, content)
        }
    }

    private fun changeReasoningParagraph(newText: String) {
        if (reasoningNext.isVisible) return

        reasoningNext.apply {
            alpha = 0f
            visibility = VISIBLE
            text = newText
        }

        reasoningNext.animate()
            .alpha(1f)
            .setDuration(CHANGE_DURATION)
            .withEndAction {
                reasoningCurrent.animate().cancel()
                reasoningNext.apply {
                    visibility = GONE
                    alpha = 0f
                    text = null
                }
                reasoningCurrent.apply {
                    alpha = 1f
                    text = newText
                    visibility = VISIBLE
                }
            }
            .start()

        reasoningCurrent.animate()
            .alpha(0f)
            .setDuration(CHANGE_DURATION)
            .start()
    }
}