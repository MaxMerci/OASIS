package mm.oasis.ui.data

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import mm.oasis.R
import mm.oasis.repository.ChatRepository
import mm.oasis.serialization.storage.ChatData



class ChatsAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onLongItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {
    companion object {
        val animatedItems = mutableSetOf<ChatData>()
        const val TYPE_NORMAL = 0
        const val TYPE_SELECTED = 1
    }

    override fun getItemCount() = ChatRepository.items.size

    override fun getItemViewType(position: Int): Int {
        return if (ChatRepository.currentIndex == position) TYPE_SELECTED else TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        if (viewType == TYPE_SELECTED) {
            view.setBackgroundResource(R.drawable.ic_bg_g)
        }
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = ChatRepository.items[position]

        holder.bind(chat)

        when (getItemViewType(position)) {
            TYPE_SELECTED -> holder.itemView.setBackgroundResource(R.drawable.ic_bg_g)
            TYPE_NORMAL -> holder.itemView.setBackgroundResource(R.drawable.ic_bg_b)
        }
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.name)

        @SuppressLint("NotifyDataSetChanged")
        fun bind(chat: ChatData) {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                name.text = chat.name

                itemView.apply {
                    if (!animatedItems.contains(chat)) {
                        animatedItems.add(chat)
                        alpha = 0f
                        scaleX = 0f

                        animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .setDuration(250)
                            .setStartDelay((pos * 100L))
                            .start()
                    }
                }

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