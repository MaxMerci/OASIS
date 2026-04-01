package mm.oasis.ui.chat.message

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.View.*
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.serialization.dto.Message


class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val avatarView: ImageView = view.findViewById(R.id.avatar)
    private val nameView: TextView = view.findViewById(R.id.name)
    private val contentView: TextView = view.findViewById(R.id.content)
    /* REASONING */
    private val reasoningCurrent: TextView = view.findViewById(R.id.reasoningCurrent)
    private val reasoningNext: TextView = view.findViewById(R.id.reasoningNext)
    private val reasoningContainer: View = view.findViewById(R.id.reasoning)

    companion object {
        const val COLLAPSE_DURATION = 500L
        const val CHANGE_DURATION = 500L
    }

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
        animator.duration = COLLAPSE_DURATION
        animator.interpolator = DecelerateInterpolator()

        animator.addUpdateListener {
            val value = it.animatedValue as Int
            view.layoutParams.height = value
            view.requestLayout()
        }

        view.animate()
            .alpha(0f)
            .setDuration(COLLAPSE_DURATION)
            .withEndAction {
                view.visibility = GONE
            }
            .start()

        animator.start()
    }

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?) {val newUrl = message.avatarUrl
        /* SET BASE FIELDS */
        val currentUrl = avatarView.tag as? String
        if (currentUrl != newUrl) {
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

        markwon?.setMarkdown(contentView, content)
        if (contentView.text.isNotBlank() && contentView.alpha == 0f) {
            contentView.animate().alpha(1f).setDuration(COLLAPSE_DURATION).start()
        }

        if (!reasoning.isNullOrBlank() && content.isBlank()) {
            if (!reasoningContainer.isVisible) expand(reasoningContainer)

            val parts = reasoning.split("\n\n").filter { it.isNotBlank() }

            val targetIndex = when {
                parts.size >= 2 -> parts.size - 2 // всега показываем готовый, предпоследний абзац
                else -> 0
            }
            println(targetIndex)
            val newText = parts[targetIndex].trim()
            reasoningCurrent.text = newText
//            if (targetIndex == 0) {
//                message.uiData.lastReasoningIndex = 0
//                reasoningCurrent.text = newText
//            } else if (message.uiData.lastReasoningIndex != targetIndex) {
//                message.uiData.lastReasoningIndex = targetIndex
//                changeReasoningParagraph(message, newText)
//            }
        } else {
            if (reasoningContainer.isVisible) collapse(reasoningContainer)
        }
    }

    fun changeReasoningParagraph(message: Message, newReasoningText: String) {
        /* ТУПЕЙШИЙ СЫНИШЕ ШЛЮХИ ->

        if (message.uiData.isAnimating) return
        message.uiData.isAnimating = true

        reasoningNext.apply {
            visibility = VISIBLE
            alpha = 0f
            text = newReasoningText
        }

        reasoningNext.animate()
            .alpha(1f)
            .setDuration(CHANGE_DURATION)
            .withEndAction {
                reasoningCurrent.apply {
                    text = newReasoningText
                    alpha = 1f
                    visibility = VISIBLE
                }
                reasoningNext.apply {
                    alpha = 0f
                    text = null
                    visibility = GONE
                }
                message.uiData.isAnimating = false
            }
            .start()

        reasoningCurrent.animate()
            .alpha(0f)
            .setDuration(CHANGE_DURATION)
            .start()
        */
    }
}