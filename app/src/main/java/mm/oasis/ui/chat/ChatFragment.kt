package mm.oasis.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import mm.oasis.Oasis
import mm.oasis.R
import mm.oasis.repository.ChatRepository
import mm.oasis.serialization.dto.ContentPart
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent
import mm.oasis.serialization.dto.Request
import mm.oasis.ui.modal.DialogButton
import mm.oasis.ui.modal.DialogField
import mm.oasis.ui.modal.FieldType
import mm.oasis.ui.modal.ModalDialogBuilder

class ChatFragment : Fragment() {

    private lateinit var input: RequestView
    private val messagesAdapter = MessagesAdapter()
    private lateinit var listView: ListView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        input = view.findViewById(R.id.requestView)

        listView = view.findViewById(R.id.messagesList)
        listView.adapter = messagesAdapter
        listView.emptyView = view.findViewById<TextView>(R.id.emptyView)

        input.onSend = ::sendMessage

        lifecycleScope.launch {
            ChatRepository.state.collect {
                messagesAdapter.notifyDataSetChanged()
            }
        }

        return view
    }

    private fun sendMessage(request: Request) {
        ChatRepository.currentChat.messages += request.messages
        messagesAdapter.notifyDataSetChanged()

        listView.smoothScrollToPosition(messagesAdapter.count - 1)

        ChatRepository.currentChat.messages.add(
            Message(
                role = Message.MessageRole.ASSISTANT,
                content = MessageContent.Parts(
                    listOf(ContentPart.TextPart(request.toString()))
                ),
                name = request.model,
            )
        )
        messagesAdapter.notifyDataSetChanged()

        listView.smoothScrollToPosition(messagesAdapter.count - 1)
    }
}