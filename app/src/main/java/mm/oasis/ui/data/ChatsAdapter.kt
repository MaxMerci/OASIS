package mm.oasis.ui.data

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.repository.ChatRepository

class ChatsAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onLongItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {
    override fun getItemCount() = ChatRepository.items.size

    override fun getItemViewType(position: Int): Int {
        return if (ChatRepository.currentIndex == position) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layout = if (viewType == 0) R.layout.item_chat else R.layout.item_chat_c
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = ChatRepository.items[position]
        holder.bind(chat.name)
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.name)

        fun bind(chatName: String) {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                name.text = chatName
                itemView.setOnClickListener {
                    onItemClick(pos)
                }

                itemView.setOnLongClickListener {
                    onLongItemClick(pos)
                    true
                }
            }
        }
    }
}