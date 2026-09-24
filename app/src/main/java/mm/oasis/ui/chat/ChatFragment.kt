package mm.oasis.ui.chat

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.LayoutInflater
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mm.oasis.R
import mm.oasis.remote.Agent
import mm.oasis.repository.ChatRepository
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.*

class ChatFragment : Fragment() {

    private lateinit var input: RequestView
    private val messagesAdapter = MessagesAdapter()
    private lateinit var messagesList: RecyclerView
    private lateinit var emptyView: TextView

    private var generation: Job? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleFileUri(it) }
    }

    @SuppressLint("NotifyDataSetChanged")
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
        (messagesList.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false  // это был ключ к решению всех моих проблем, просто памятка

        input.onSend = ::sendMessage
        input.onStop = { generation?.cancel() }
        input.onAddAttachment = {
            pickFileLauncher.launch("*/*")
        }

        lifecycleScope.launch {
            ChatRepository.state.collectLatest {
                updateMessages()
            }
        }

        return view
    }

    private fun updateEmptyViewVisibility() {
        val isEmpty = messagesAdapter.itemCount == 0
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        messagesList.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun handleFileUri(uri: Uri) {
        val contentResolver = requireContext().contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = getFileName(uri) ?: "file"

        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch

            val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val part = when {
                mimeType.startsWith("image/") -> {
                    ContentPart.ImagePart(
                        imageUrl = ImageUrl(url = "data:$mimeType;base64,$base64Content"),
                        fileName = fileName
                    )
                }
                mimeType.startsWith("audio/") -> {
                    val format = mimeType.substringAfter("/", "mp3")
                    ContentPart.AudioPart(
                        inputAudio = InputAudio(data = base64Content, format = format),
                        fileName = fileName
                    )
                }
                mimeType.startsWith("text/") || mimeType == "application/json" -> {
                    val textContent = String(bytes)
                    ContentPart.TextPart(text = textContent, fileName = fileName)
                }
                else -> {
                    ContentPart.TextPart(text = "[File: $fileName ($mimeType)]", fileName = fileName)
                }
            }

            input.addAttachment(part)
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) name = it.getString(nameIndex)
            }
        }
        return name
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateMessages() {
        if (!::messagesList.isInitialized) return
        messagesAdapter.notifyDataSetChanged()
        updateEmptyViewVisibility()
        if (messagesAdapter.itemCount > 0) {
            messagesList.smoothScrollToPosition(messagesAdapter.itemCount - 1)
        }
    }

    private fun sendMessage(request: Request) {
        if (generation?.isActive == true) return

        val errorText = when {
            ProfileRepository.currentProfile == null -> "PROFILE NOT SELECTED"
            ProfileRepository.currentProfile?.model == null -> "MODEL NOT SELECTED"
            else -> null
        }
        errorText?.let {
            Snackbar.make(requireView(), it, Snackbar.LENGTH_SHORT).show()
            return
        }

        input.clear()
        val currentChat = ChatRepository.currentChat

        // модели уходит вся история чата, а не только последнее сообщение
        val history = currentChat.messages
            .filter { it.role != Message.MessageRole.ASSISTANT || it.display.isNotBlank() }
            .map { it.copy(toolCalls = null) } // результаты инструментов в чате не хранятся
        if (currentChat.messages.isEmpty()) {
            // новый чат называем по первому сообщению, а не "Chat N"
            request.messages.lastOrNull()?.display?.lineSequence()?.firstOrNull { it.isNotBlank() }
                ?.let { currentChat.name = it.trim().take(32) }
        }
        currentChat.messages += request.messages

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
                Agent.use(request.copy(messages = history + request.messages)).collect { flow ->
                    assistant.streamDisplay(flow.content)
                    assistant.reasoning = (assistant.reasoning ?: "") + flow.reasoning
                    notifyLastMessageChanged()
                }
            } catch (e: CancellationException) {
                // нажали стоп - оставляем то, что успело прийти
            } catch (e: Exception) {
                e.printStackTrace()
                assistant.streamDisplay("\n\n**[ERROR]:** ${e.message ?: e.toString()}")
                view?.let { Snackbar.make(it, e.message ?: e.toString(), Snackbar.LENGTH_SHORT).show() }
            } finally {
                if (assistant.display.isBlank() && assistant.reasoning.isNullOrBlank()) {
                    currentChat.messages -= assistant
                }
                input.setGenerating(false)
                // пустое обновление: сохраняет чат и дергает state, чтобы список чатов увидел новое имя
                ChatRepository.updateItem(ChatRepository.currentIndex) { it }
                updateMessages()
            }
        }
    }

    private fun notifyLastMessageChanged() {
        if (!::messagesList.isInitialized) return
        messagesAdapter.notifyItemChanged(messagesAdapter.itemCount - 1)
    }
}
