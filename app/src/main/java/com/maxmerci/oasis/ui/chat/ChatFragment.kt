package com.maxmerci.oasis.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.maxmerci.oasis.R
import com.maxmerci.oasis.repository.ChatRepository
import com.maxmerci.oasis.serialization.dto.Message
import com.maxmerci.oasis.serialization.dto.Request
import com.maxmerci.oasis.ui.chat.MessagesAdapter
import com.maxmerci.oasis.ui.chat.RequestView

class ChatFragment : Fragment() {

    private lateinit var input: RequestView
    private val messages = MessagesAdapter()
    private lateinit var listView: ListView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        input = view.findViewById(R.id.requestView)

        listView = view.findViewById(R.id.messagesList)
        listView.adapter = messages
        listView.emptyView = view.findViewById<TextView>(R.id.emptyView)

        input.onSend = ::sendMessage

        return view
    }

    private fun sendMessage(request: Request) {
        ChatRepository.currentChat.messages += request.messages
        messages.notifyDataSetChanged()

        listView.smoothScrollToPosition(messages.count - 1)

        ChatRepository.currentChat.messages.add(
            Message(
                role = Message.MessageRole.ASSISTANT,
                content = request.toString(),
                name = request.model,
            )
        )
        messages.notifyDataSetChanged()

        listView.smoothScrollToPosition(messages.count - 1)
    }
}