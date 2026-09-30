package mm.oasis.ui.chat

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import mm.oasis.R
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.*
import mm.oasis.ui.chat.message.AttachmentsAdapter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RequestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    /* MAIN */
    var onSend: ((request: Request, edited: Message?) -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onAddAttachment: (() -> Unit)? = null
    var onEditingChanged: ((Message?) -> Unit)? = null

    /* UI REFERENCES */
    private val content: EditText
    private val send: ImageButton
    private val addAttachment: ImageButton
    private val editBar: View

    private val settingsContainer: View
    private val attachmentsList: RecyclerView
    private val attachmentsAdapter: AttachmentsAdapter

    private val toolsList: RecyclerView
    private val toolsAdapter: ToolsListAdapter

    /* REASONING */
    private val reasoningYes: TextView
    private val reasoningAuto: TextView
    private val reasoningNo: TextView

    private var reasoningMode: ReasoningMode = ReasoningMode.AUTO

    /* PARAMETERS */
    private val temperatureField: EditText
    private val maxTokensField: EditText
    private val maxIterationsField: EditText

    /* ANIM & TOUCH */
    private var fullSettingsHeight = 0
    private var initialY = 0f
    private var initialHeight = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /* STATE */
    private var isGenerating = false

    /* EDITING */
    var editing: Message? = null
        private set
    private var draft: CharSequence? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.request_fields, this, true)

        // MAIN
        content = findViewById(R.id.contentInput)
        send = findViewById(R.id.sendButton)
        addAttachment = findViewById(R.id.addAttachment)
        editBar = findViewById(R.id.editBar)

        // REASONING
        reasoningYes = findViewById(R.id.reasoningYes)
        reasoningAuto = findViewById(R.id.reasoningAuto)
        reasoningNo = findViewById(R.id.reasoningNo)

        // PARAMETERS
        temperatureField = findViewById(R.id.temperature)
        maxTokensField = findViewById(R.id.maxTokens)
        maxIterationsField = findViewById(R.id.maxIterations)

        // ATTACHMENTS
        attachmentsList = findViewById(R.id.attachmentsList)
        attachmentsAdapter = AttachmentsAdapter(
            onOpen = ::openAttachment,
            onRemove = { updateAttachmentsVisibility() }
        )
        attachmentsList.adapter = attachmentsAdapter

        // TOOLS
        toolsList = findViewById(R.id.toolsList)
        toolsAdapter = ToolsListAdapter()
        toolsList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        toolsList.adapter = toolsAdapter

        // SETTINGS
        settingsContainer = findViewById(R.id.settingsContainer)
        settingsContainer.visibility = VISIBLE
        settingsContainer.measure(
            MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        fullSettingsHeight = settingsContainer.measuredHeight
        updateSettingsHeight(0)

        updateReasoningState(ReasoningMode.AUTO)
        setupListeners()
    }

    private fun setupListeners() {
        addAttachment.setOnClickListener { onAddAttachment?.invoke() }

        reasoningYes.setOnClickListener { updateReasoningState(ReasoningMode.ENABLED) }
        reasoningAuto.setOnClickListener { updateReasoningState(ReasoningMode.AUTO) }
        reasoningNo.setOnClickListener { updateReasoningState(ReasoningMode.DISABLED) }

        send.setOnClickListener { onClickSend() }

        findViewById<View>(R.id.editCancel).setOnClickListener { cancelEditing() }
        content.addTextChangedListener { updateSendState() }
    }

    /* EDITING */

    fun startEditing(message: Message) {
        if (editing == null) draft = content.text.toString()
        editing = message
        editBar.visibility = VISIBLE
        content.setText(message.display)
        content.setSelection(content.text.length)
        content.requestFocus()
        context.getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(content, InputMethodManager.SHOW_IMPLICIT)
        updateSendState()
        onEditingChanged?.invoke(message)
    }

    fun cancelEditing() {
        if (editing == null) return
        val previous = draft
        finishEditing()
        content.setText(previous ?: "")
        content.setSelection(content.text.length)
    }

    fun finishEditing() {
        if (editing == null) return
        editing = null
        draft = null
        editBar.visibility = GONE
        updateSendState()
        onEditingChanged?.invoke(null)
    }

    private fun canSendEdit(): Boolean {
        val message = editing ?: return true
        val text = content.text.toString()
        val added = attachmentsAdapter.itemCount > 0
        return (text != message.display || added) && (text.isNotBlank() || added || hasFiles(message))
    }

    private fun hasFiles(message: Message): Boolean =
        (message.content as? MessageContent.Parts)?.parts.orEmpty().any { !it.fileName.isNullOrEmpty() }

    private fun updateSendState() {
        val enabled = isGenerating || canSendEdit()
        send.isEnabled = enabled
        send.alpha = if (enabled) 1f else 0.4f
    }

    fun addAttachment(part: ContentPart, uri: Uri? = null) {
        attachmentsAdapter.addItem(AttachmentsAdapter.Attachment(part, uri))
        updateAttachmentsVisibility()
    }

    private fun openAttachment(attachment: AttachmentsAdapter.Attachment) {
        val uri = attachment.uri ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeOf(uri, attachment.part.fileName))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, attachment.part.fileName ?: "OPEN"))
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(this, "NO APP TO OPEN ${attachment.part.fileName ?: "FILE"}", Snackbar.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Snackbar.make(this, "NO ACCESS TO ${attachment.part.fileName ?: "FILE"}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun mimeOf(uri: Uri, fileName: String?): String {
        val fromResolver = context.contentResolver.getType(uri)
        if (fromResolver != null && fromResolver != "application/octet-stream") return fromResolver
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun appendText(text: String) {
        val current = content.text
        if (current.isNotEmpty() && !current.endsWith("\n")) current.append("\n")
        current.append(text)
        content.setSelection(content.text.length)
    }

    fun setGenerating(generating: Boolean) {
        isGenerating = generating
        send.setImageResource(if (generating) R.drawable.ic_stop else R.drawable.ic_send)
        updateSendState()
    }

    fun clear() {
        content.text.clear()
        attachmentsAdapter.clear()
        updateAttachmentsVisibility()
    }

    private enum class ReasoningMode {
        AUTO, ENABLED, DISABLED
    }

    private fun updateReasoningState(mode: ReasoningMode) {
        reasoningMode = mode
        reasoningYes.setBackgroundResource(if (mode == ReasoningMode.ENABLED) R.drawable.ic_bg_g_r else android.R.color.transparent)
        reasoningAuto.setBackgroundResource(if (mode == ReasoningMode.AUTO) R.drawable.ic_bg_g_r else android.R.color.transparent)
        reasoningNo.setBackgroundResource(if (mode == ReasoningMode.DISABLED) R.drawable.ic_bg_g_r else android.R.color.transparent)
    }

    private fun onClickSend() {
        if (isGenerating) {
            onStop?.invoke()
            return
        }

        val edited = editing
        if (!canSendEdit()) return

        val contentText = content.text.toString()
        val keptFiles = (edited?.content as? MessageContent.Parts)?.parts.orEmpty()
            .filter { !it.fileName.isNullOrEmpty() }
        val attachments = keptFiles + attachmentsAdapter.getItems()
        if (contentText.isBlank() && attachments.isEmpty()) return

        val profile = ProfileRepository.currentProfile
        val userParts = attachments.toMutableList()
        if (contentText.isNotBlank()) userParts += ContentPart.TextPart(contentText)

        val message = Message(
            avatarUrl = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=${profile?.endPoint}",
            role = Message.MessageRole.USER,
            content = MessageContent.Parts(userParts),
            name = profile?.endpointDomain() ?: "YOU"
        )

        onSend?.invoke(buildRequest(listOf(message)), edited)
    }

    fun buildRequest(messages: List<Message>): Request {
        val profile = ProfileRepository.currentProfile
        return Request(
            messages = messages,
            model = profile?.model?.id,
            temperature = temperatureField.text.toString().trim().toDoubleOrNull(),
            maxTokens = maxTokensField.text.toString().trim().toDoubleOrNull()?.toInt(),
            includeReasoning = when (reasoningMode) {
                ReasoningMode.AUTO -> null
                ReasoningMode.ENABLED -> true
                ReasoningMode.DISABLED -> false
            },
            tools = toolsAdapter.enabledTools.toList().ifEmpty { null },
            maxIterations = maxIterationsField.text.toString().trim().toIntOrNull()?.takeIf { it > 0 }
                ?: Request.DEFAULT_MAX_ITERATIONS
        )
    }

    private fun updateAttachmentsVisibility() {
        attachmentsList.visibility = if (attachmentsAdapter.itemCount > 0) VISIBLE else GONE
        updateSendState()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialY = event.rawY
                initialHeight = settingsContainer.layoutParams.height
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawY - initialY) > touchSlop) return true
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val deltaY = initialY - event.rawY
                val newHeight = (initialHeight + deltaY).toInt()
                updateSettingsHeight(min(fullSettingsHeight, max(0, newHeight)))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val currentHeight = settingsContainer.layoutParams.height
                val targetHeight = if (currentHeight > fullSettingsHeight / 3) fullSettingsHeight else 0
                animateHeightChange(currentHeight, targetHeight)
            }
        }
        return true
    }

    private fun updateSettingsHeight(height: Int) {
        settingsContainer.layoutParams.height = height
        settingsContainer.requestLayout()
    }

    private fun animateHeightChange(from: Int, to: Int) {
        ValueAnimator.ofInt(from, to).apply {
            duration = 250
            addUpdateListener { updateSettingsHeight(it.animatedValue as Int) }
        }.start()
    }
}