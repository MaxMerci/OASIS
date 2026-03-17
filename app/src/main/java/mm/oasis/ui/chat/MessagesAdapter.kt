package mm.oasis.ui.chat

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.repository.ChatRepository
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent

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

    class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatarView: ImageView = view.findViewById(R.id.avatar)
        private val nameView: TextView = view.findViewById(R.id.name)
        private val contentView: TextView = view.findViewById(R.id.content)
        private val reasoningList: RecyclerView = view.findViewById(R.id.reasoning_list)

        init {
            reasoningList.isNestedScrollingEnabled = true
            reasoningList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            rv.parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    return false
                }
                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }

        @SuppressLint("SetTextI18n")
        fun bind(message: Message, markwon: Markwon?) {
            Glide.with(itemView.context)
                .load(message.avatarUrl)
                .centerCrop()
                .into(avatarView)

            nameView.text = "[${message.name ?: "ASSISTANT"}] >"
            markwon?.setMarkdown(contentView, message.display)

            if (!message.reasoning.isNullOrBlank()) {
                val reasoningParts = message.reasoning!!.split("\n\n").filter { it.isNotBlank() }
                reasoningList.visibility = View.VISIBLE
                
                var adapter = reasoningList.adapter as? ReasoningAdapter
                if (adapter == null) {
                    adapter = ReasoningAdapter(reasoningParts, markwon) { height ->
                        val params = reasoningList.layoutParams
                        if (params.height != height) {
                            params.height = height
                            reasoningList.layoutParams = params
                        }
                    }
                    reasoningList.adapter = adapter
                } else {
                    adapter.updateData(reasoningParts)
                }
                
                reasoningList.smoothScrollToPosition(reasoningParts.size - 1)
            } else {
                reasoningList.visibility = View.GONE
            }
        }

        private class ReasoningAdapter(
            private var items: List<String>,
            private val markwon: Markwon?,
            private val onLastItemHeightResolved: (Int) -> Unit
        ) : RecyclerView.Adapter<ReasoningViewHolder>() {
            @SuppressLint("NotifyDataSetChanged")
            fun updateData(newItems: List<String>) {
                if (items != newItems) {
                    items = newItems
                    notifyDataSetChanged()
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReasoningViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reasoning, parent, false)
                return ReasoningViewHolder(v)
            }

            override fun onBindViewHolder(holder: ReasoningViewHolder, position: Int) {
                markwon?.setMarkdown(holder.textView, items[position])
                if (position == items.size - 1) {
                    holder.itemView.post {
                        onLastItemHeightResolved(holder.itemView.height)
                    }
                }
            }

            override fun getItemCount() = items.size
        }

        class ReasoningViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.text)
        }
    }

    class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView = view.findViewById(R.id.name)
        private val contentView: TextView = view.findViewById(R.id.content)

        @SuppressLint("SetTextI18n")
        fun bind(message: Message, markwon: Markwon?) {
            nameView.text = "[${ChatRepository.currentChat.name}]:"
            markwon?.setMarkdown(contentView, message.display)
        }
    }
}
