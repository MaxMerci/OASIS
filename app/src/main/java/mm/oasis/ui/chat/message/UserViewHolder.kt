package mm.oasis.ui.chat.message

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent

class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val nameView: TextView = view.findViewById(R.id.name)
    private val contentView: TextView = view.findViewById(R.id.content)
    private val attContainer: LinearLayout = view.findViewById(R.id.attachments_container)

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?) {
        nameView.text = "[${message.name}] >"
        markwon?.setMarkdown(contentView, message.display)

        attContainer.removeAllViews()
        val inflater = LayoutInflater.from(itemView.context)
        when (val content = message.content) {
            is MessageContent.Parts -> {
                content.parts.forEach { part ->
                    if (!part.fileName.isNullOrEmpty()) {
                        val itemView = inflater.inflate(R.layout.item_attachment, attContainer, false)
                        itemView.findViewById<TextView>(R.id.attachment_name).text = part.fileName
                        attContainer.addView(itemView)
                    }
                }
            }
            else -> {}
        }
    }
}