package mm.oasis.ui.chat

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import mm.oasis.R
import mm.oasis.repository.ChatRepository
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent

class MessagesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var messages: List<Message> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<Message>) {
        messages = newList
        notifyDataSetChanged()
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        return when (messages[position].role) {
            Message.MessageRole.USER -> 0
            Message.MessageRole.ASSISTANT -> 1
            else -> 2
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> UserViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            1 -> AssistantViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
            else -> SystemViewHolder(inflater.inflate(R.layout.item_message_system, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserViewHolder -> holder.bind(message)
            is AssistantViewHolder -> holder.bind(message)
            is SystemViewHolder -> holder.bind(message)
        }
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val content: TextView = view.findViewById(R.id.content)
        private val name: TextView = view.findViewById(R.id.name)
        private val attachmentsContainer: LinearLayout = view.findViewById(R.id.attachments_container)

        fun bind(message: Message) {
            content.text = message.display
            name.text = "[${message.name}] >"

            attachmentsContainer.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            val content = message.content
            if (content is MessageContent.Parts) {
                content.parts.forEach { part ->
                    if (!part.fileName.isNullOrEmpty()) {
                        val itemView = inflater.inflate(R.layout.item_attachment, attachmentsContainer, false)
                        val tvName = itemView.findViewById<TextView>(R.id.attachment_name)
                        tvName.text = part.fileName
                        attachmentsContainer.addView(itemView)
                    }
                }
            }
        }
    }

    class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val content: TextView = view.findViewById(R.id.content)
        private val reasoning: TextView = view.findViewById(R.id.reasoning)
        private val name: TextView = view.findViewById(R.id.name)
        private val avatar: ImageView = view.findViewById(R.id.avatar)

        fun bind(message: Message) {
            Glide.with(itemView.context)
                .load(message.avatarUrl)
                .centerCrop()
                .into(avatar)

            content.text = message.display
            name.text = "[${message.name ?: "ASSISTANT"}] >"

            if (!message.reasoning.isNullOrBlank()) {
                reasoning.visibility = View.VISIBLE
                reasoning.text = message.reasoning
            } else {
                reasoning.visibility = View.GONE
            }
        }
    }

    class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.name)
        private val content: TextView = view.findViewById(R.id.content)

        fun bind(message: Message) {
            name.text = "[${ChatRepository.currentChat.name}]:"
            content.text = message.display
        }
    }
}