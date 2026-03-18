package mm.oasis.ui.chat.message

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.repository.ChatRepository
import mm.oasis.serialization.dto.Message

class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val nameView: TextView = view.findViewById(R.id.name)
    private val contentView: TextView = view.findViewById(R.id.content)

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?) {
        nameView.text = "[${ChatRepository.currentChat.name}]:"
        markwon?.setMarkdown(contentView, message.display)
    }
}