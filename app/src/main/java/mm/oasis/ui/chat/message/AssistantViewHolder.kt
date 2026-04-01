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
    private val reasoningView: TextView = view.findViewById(R.id.reasoning)

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

        if (!reasoning.isNullOrBlank() && content.isBlank()) {
            if (!reasoningView.isVisible) reasoningView.visibility = VISIBLE
            val parts = reasoning.split("\n\n").filter { it.isNotBlank() }

            val targetIndex = when {
                parts.size >= 2 -> parts.size - 2 // всега показываем готовый, предпоследний абзац
                else -> 0
            }
            reasoningView.text = parts[targetIndex].trim()
        } else {
            if (reasoningView.isVisible) reasoningView.visibility = GONE
        }
    }
}