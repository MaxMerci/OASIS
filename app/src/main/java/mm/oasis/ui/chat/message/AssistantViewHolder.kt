package mm.oasis.ui.chat.message

import android.annotation.SuppressLint
import android.view.View
import android.view.View.*
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

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?) {val newUrl = message.avatarUrl
        /* SET BASE FIELDS */
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
            if (!reasoningContainer.isVisible) reasoningContainer.visibility = VISIBLE
            handleReasoningParagraph(message)
        } else if (content.isNotBlank()) {
            if (reasoningContainer.isVisible) reasoningContainer.visibility = GONE
            markwon?.setMarkdown(contentView, content)
        }
    }

    private fun handleReasoningParagraph(message: Message) {
        if (message.uiData.isAnimating) return
        message.uiData.isAnimating = true
        val newText = message.uiData.reasoningParagraph

        reasoningNext.apply {
            alpha = 0f
            visibility = VISIBLE
            text = newText
        }

        reasoningNext.animate()
            .alpha(1f)
            .setDuration(CHANGE_DURATION + (CHANGE_DURATION/100))
            .withEndAction {
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
                Thread.sleep(100L)
                message.uiData.isAnimating = false
            }
            .start()

        reasoningCurrent.animate()
            .alpha(0f)
            .setDuration(CHANGE_DURATION)
            .start()
    }
}