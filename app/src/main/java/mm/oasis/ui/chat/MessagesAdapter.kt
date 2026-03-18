package mm.oasis.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.repository.ChatRepository
import mm.oasis.serialization.dto.Message
import mm.oasis.ui.chat.message.AssistantViewHolder
import mm.oasis.ui.chat.message.SystemViewHolder
import mm.oasis.ui.chat.message.UserViewHolder

class MessagesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var markwon: Markwon? = null

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_SYSTEM = 2
    }

    override fun getItemCount() = ChatRepository.currentChat.messages.size

    override fun getItemViewType(position: Int): Int {
        return when (ChatRepository.currentChat.messages[position].role) {
            Message.MessageRole.USER -> TYPE_USER
            Message.MessageRole.ASSISTANT -> TYPE_ASSISTANT
            else -> TYPE_SYSTEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (markwon == null) {
            markwon = Markwon.create(parent.context)
        }
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            TYPE_ASSISTANT -> AssistantViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
            else -> SystemViewHolder(inflater.inflate(R.layout.item_message_system, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = ChatRepository.currentChat.messages[position]
        when (holder) {
            is UserViewHolder -> holder.bind(message, markwon)
            is AssistantViewHolder -> holder.bind(message, markwon)
            is SystemViewHolder -> holder.bind(message, markwon)
        }
    }
}
