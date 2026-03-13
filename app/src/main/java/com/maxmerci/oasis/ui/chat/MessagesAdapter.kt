package com.maxmerci.oasis.ui.chat

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.maxmerci.oasis.R
import com.maxmerci.oasis.repository.ChatRepository
import com.maxmerci.oasis.serialization.dto.Message

class MessagesAdapter() : BaseAdapter() {
    override fun getCount() = ChatRepository.currentChat.messages.size
    override fun getItem(position: Int) = ChatRepository.currentChat.messages[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getViewTypeCount() = 3 // USER, ASSISTANT, SYSTEM/TOOL

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).role) {
            Message.MessageRole.USER -> 0
            Message.MessageRole.ASSISTANT -> 1
            else -> 2
        }
    }

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val message = getItem(position)
        val type = getItemViewType(position)

        val view = convertView ?: when (type) {
            0 -> LayoutInflater.from(parent?.context).inflate(R.layout.item_message_user, parent, false)
            1 -> LayoutInflater.from(parent?.context).inflate(R.layout.item_message_assistant, parent, false)
            else -> LayoutInflater.from(parent?.context).inflate(R.layout.item_message_system, parent, false)
        }

        when (type) {
            0 -> { // USER
                view.findViewById<TextView>(R.id.content).text = message.content
                view.findViewById<TextView>(R.id.name).text = "[${message.name}] >"

                val attachmentsContainer = view.findViewById<LinearLayout>(R.id.attachments_container)
                attachmentsContainer.removeAllViews()
                message.attachments?.forEach { attachment ->
                    val tv = TextView(view.context)
                    tv.text = attachment.name
                    tv.textSize = 12f
                    tv.setPadding(8, 4, 8, 4)
                    tv.setTextColor(0xFFEEEEEE.toInt())
                    attachmentsContainer.addView(tv)
                }
            }
            1 -> { // ASSISTANT
                val contentTv = view.findViewById<TextView>(R.id.content)
                val reasoningTv = view.findViewById<TextView>(R.id.reasoning)
                val nameTv = view.findViewById<TextView>(R.id.name)

                contentTv.text = message.content
                nameTv?.text = "[${message.name ?: "ASSISTANT"}] >"

                if (!message.reasoning.isNullOrBlank()) {
                    reasoningTv.visibility = View.VISIBLE
                    reasoningTv.text = message.reasoning
                } else {
                    reasoningTv.visibility = View.GONE
                }
            }
            else -> { // SYSTEM/TOOL
                view.findViewById<TextView>(R.id.name).text = "[SYSTEM]:"
                view.findViewById<TextView>(R.id.content).text = message.content
            }
        }

        return view
    }
}