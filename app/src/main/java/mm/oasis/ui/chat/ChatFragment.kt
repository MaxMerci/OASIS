package mm.oasis.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mm.oasis.R
import mm.oasis.remote.Agent
import mm.oasis.remote.Attachments
import mm.oasis.repository.ChatRepository
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.*
import mm.oasis.serialization.storage.ChatData
import mm.oasis.ui.objects.MessageMenu

class ChatFragment : Fragment() {

    private lateinit var input: RequestView
    private val messagesAdapter = MessagesAdapter(onLongClick = ::showMessageMenu)
    private lateinit var messagesList: RecyclerView
    private lateinit var emptyView: TextView

    private var generation: Job? = null

    private var touchX = 0
    private var touchY = 0

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { lifecycleScope.launch { attachFile(it) } }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        input = view.findViewById(R.id.requestView)
        emptyView = view.findViewById(R.id.emptyView)

        messagesList = view.findViewById(R.id.messagesList)
        messagesList.adapter = messagesAdapter
        messagesList.itemAnimator = DefaultItemAnimator().apply {
            supportsChangeAnimations = false  // это был ключ к решению всех моих проблем, просто памятка
            addDuration = 500L
        }
        messagesList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchX = e.rawX.toInt()
                    touchY = e.rawY.toInt()
                }
                return false
            }
        })

        input.onSend = ::sendMessage
        input.onEditingChanged = { messagesAdapter.editing = it }
        input.onStop = { generation?.cancel() }
        input.onAddAttachment = {
            pickFileLauncher.launch("*/*")
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        input.setGenerating(generation?.isActive == true)
        // viewLifecycleOwner: при пересоздании вью старый сборщик не должен дергать старый список
        viewLifecycleOwner.lifecycleScope.launch {
            ChatRepository.state.collect {
                updateMessages()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            SharedInbox.files.collect { uris ->
                uris.forEach { uri ->
                    attachFile(uri)
                    SharedInbox.files.update { it - uri }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            SharedInbox.text.collect { text ->
                if (text == null) return@collect
                SharedInbox.text.value = null
                input.appendText(text)
            }
        }
    }

    private fun updateEmptyViewVisibility() {
        val isEmpty = messagesAdapter.itemCount == 0
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        messagesList.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private suspend fun attachFile(uri: Uri) {
        try {
            input.addAttachment(Attachments.read(requireContext().contentResolver, uri), uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            view?.let { Snackbar.make(it, e.message ?: e.toString(), Snackbar.LENGTH_SHORT).show() }
        }
    }

    fun updateMessages() {
        if (view == null) return
        val update = messagesAdapter.submit(ChatRepository.currentChat)
        updateEmptyViewVisibility()
        val last = messagesAdapter.itemCount - 1
        if (last < 0) return
        if (update == MessagesAdapter.Update.SWITCHED) input.cancelEditing()
        when (update) {
            MessagesAdapter.Update.SWITCHED -> messagesList.scrollToPosition(last)
            MessagesAdapter.Update.INSERTED -> messagesList.smoothScrollToPosition(last)
            MessagesAdapter.Update.NONE -> {}
        }
    }

    /* MENU */

    private fun showMessageMenu(message: Message) {
        val messages = ChatRepository.currentChat.messages
        val idle = generation?.isActive != true
        val items = mutableListOf<MessageMenu.Item>()

        if (message.display.isNotBlank()) items += MessageMenu.Item("COPY") { copy(message.display) }
        when (message.role) {
            // править можно только последний запрос, иначе пришлось бы переписывать всю ветку
            Message.MessageRole.USER ->
                if (idle && message === messages.lastOrNull { it.role == Message.MessageRole.USER }) {
                    items += MessageMenu.Item("EDIT") { input.startEditing(message) }
                }
            Message.MessageRole.ASSISTANT ->
                if (idle && message === messages.lastOrNull()) {
                    items += MessageMenu.Item("REGENERATE") { regenerate(message) }
                }
            else -> {}
        }
        MessageMenu.show(messagesList, touchX, touchY, items)
    }

    private fun copy(text: String) {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
    }

    /* GENERATION */

    private fun checkProfile(): Boolean {
        val errorText = when {
            ProfileRepository.currentProfile == null -> "PROFILE NOT SELECTED"
            ProfileRepository.currentProfile?.model == null -> "MODEL NOT SELECTED"
            else -> null
        }
        errorText?.let { Snackbar.make(requireView(), it, Snackbar.LENGTH_SHORT).show() }
        return errorText == null
    }

    private fun sendMessage(request: Request, edited: Message?) {
        if (generation?.isActive == true || !checkProfile()) return

        val currentChat = ChatRepository.currentChat
        if (edited != null) {
            val index = currentChat.messages.indexOfFirst { it === edited }
            if (index != -1) currentChat.messages = currentChat.messages.take(index)
            input.finishEditing()
        }
        input.clear()

        if (currentChat.messages.isEmpty()) {
            request.messages.lastOrNull()?.display?.lineSequence()?.firstOrNull { it.isNotBlank() }
                ?.let { currentChat.name = it.trim().take(32) }
        }
        currentChat.messages += request.messages
        generate(currentChat, request)
    }

    // ответ удаляется целиком и генерируется заново на ту же историю
    private fun regenerate(message: Message) {
        if (generation?.isActive == true || !checkProfile()) return

        val currentChat = ChatRepository.currentChat
        if (currentChat.messages.lastOrNull() !== message) return
        input.cancelEditing()
        currentChat.messages = currentChat.messages.dropLast(1)
        generate(currentChat, input.buildRequest(emptyList()))
    }

    private fun generate(currentChat: ChatData, request: Request) {
        val history = currentChat.messages
            .filter { it.role != Message.MessageRole.ASSISTANT || it.display.isNotBlank() }
            .map { it.copy(toolCalls = null) } // вызовы инструментов в чате только для показа

        val assistant = Message(
            avatarUrl = ProfileRepository.currentProfile?.model?.avatarUrl,
            role = Message.MessageRole.ASSISTANT,
            content = MessageContent.Parts(listOf(ContentPart.TextPart(""))),
            reasoning = "",
            name = request.model,
        )
        currentChat.messages += assistant
        updateMessages()

        input.setGenerating(true)
        generation = lifecycleScope.launch {
            try {
                Agent.use(request.copy(messages = history)).collect { flow ->
                    assistant.streamDisplay(flow.content)
                    assistant.reasoning = (assistant.reasoning ?: "") + flow.reasoning

                    if (flow.toolCalls.isNotEmpty()) {
                        assistant.toolCalls = assistant.toolCalls.orEmpty() + flow.toolCalls
                    }
                    if (flow.toolResults.isNotEmpty()) {
                        assistant.toolCalls = assistant.toolCalls?.map { call ->
                            flow.toolResults.firstOrNull { it.first === call }
                                ?.let { call.copy(result = it.second) } ?: call
                        }
                    }
                    if (flow.files.isNotEmpty()) {
                        assistant.files = (assistant.files.orEmpty() + flow.files).distinct()
                    }
                    notifyMessageChanged(assistant)
                }
            } catch (e: CancellationException) {

            } catch (e: Exception) {
                e.printStackTrace()
                assistant.streamDisplay("\n\n**[ERROR]:** ${e.message ?: e.toString()}")
                view?.let { Snackbar.make(it, e.message ?: e.toString(), Snackbar.LENGTH_SHORT).show() }
            } finally {
                if (assistant.display.isBlank() && assistant.reasoning.isNullOrBlank() && assistant.toolCalls.isNullOrEmpty()) {
                    currentChat.messages = currentChat.messages.filter { it !== assistant }
                }
                input.setGenerating(false)

                ChatRepository.updateItem(ChatRepository.items.indexOfFirst { it === currentChat }) { it }
                updateMessages()
            }
        }
    }

    private fun notifyMessageChanged(message: Message) {
        if (view == null) return
        val follow = !messagesList.canScrollVertically(1)
        messagesAdapter.notifyMessageChanged(message)
        if (follow) messagesList.post {
            if (view != null) messagesList.scrollBy(0, messagesList.height)
        }
    }
}
