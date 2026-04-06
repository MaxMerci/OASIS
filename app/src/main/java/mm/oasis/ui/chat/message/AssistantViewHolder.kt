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

    private val toolsUse: TextView = view.findViewById(R.id.toolsUse)

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
            .setDuration(CHANGE_DURATION)
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
        println(message)
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
                reasoningContainer.post {
                    changeReasoningParagraph(newText)
                }
            }
        } else if (content.isNotBlank()) {
            if (reasoningContainer.alpha == 1f) collapse(reasoningContainer)
            if (!contentView.isVisible) expand(contentView)
            markwon?.setMarkdown(contentView, content)
        }

        if (!message.toolCalls.isNullOrEmpty()) {
            if (!toolsUse.isVisible) expand(toolsUse)
            toolsUse.text = message.toolCalls!!.joinToString("\n") {
                "use " + (it.function?.name ?: "U") + " " + it.function?.arguments
            } + "\n"
        }
    }

    private fun changeReasoningParagraph(newText: String) {
        if (reasoningNext.isVisible) return
        if (reasoningCurrent.text?.toString() == newText) return

        reasoningNext.text = newText

        val wSpec = MeasureSpec.makeMeasureSpec(
            reasoningContainer.measuredWidth,
            MeasureSpec.EXACTLY
        )
        val hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        reasoningNext.measure(wSpec, hSpec)

        val nHeight = reasoningNext.measuredHeight
        val oHeight = reasoningContainer.measuredHeight

        reasoningNext.apply {
            alpha = 0f
            visibility = VISIBLE
        }

        val hAnimator = ValueAnimator.ofInt(oHeight, nHeight)
        hAnimator.duration = CHANGE_DURATION
        hAnimator.interpolator = DecelerateInterpolator()
        hAnimator.addUpdateListener {
            val value = it.animatedValue as Int
            reasoningContainer.layoutParams.height = value
            reasoningContainer.requestLayout()
        }

        reasoningCurrent.animate()
            .alpha(0f)
            .setDuration(CHANGE_DURATION)
            .start()

        reasoningNext.animate()
            .alpha(1f)
            .setDuration(CHANGE_DURATION)
            .withEndAction {
                reasoningCurrent.apply {
                    alpha = 1f
                    text = newText
                    visibility = VISIBLE
                }
                reasoningNext.apply {
                    visibility = GONE
                    alpha = 0f
                    text = null
                }

                reasoningContainer.layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                reasoningContainer.requestLayout()
            }
            .start()

        hAnimator.start()
    }
}