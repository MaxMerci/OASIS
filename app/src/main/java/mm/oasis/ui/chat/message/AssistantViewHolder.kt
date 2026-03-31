package mm.oasis.ui.chat.message

import android.annotation.SuppressLint
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
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
    private val reasoningCurrent: TextView = view.findViewById(R.id.reasoningCurrent)

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
        markwon?.setMarkdown(contentView, content)

        if (!reasoning.isNullOrBlank() && content.isBlank()) {
            if (!reasoningCurrent.isVisible) reasoningCurrent.visibility = VISIBLE

            val parts = reasoning.split("\n\n").filter { it.isNotBlank() }

            val targetIndex = when {
                parts.size >= 2 -> parts.size - 2
                parts.size == 1 -> 0
                else -> 0
            }

            val newText = parts[targetIndex].trim()
            reasoningCurrent.text = newText
        } else if (reasoningCurrent.isVisible) {
            reasoningCurrent.visibility = GONE
        }
    }
}