package mm.oasis.ui.chat

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import mm.oasis.R
import mm.oasis.remote.Agent
import mm.oasis.remote.ApiClient
import mm.oasis.remote.ToolRegistry
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.*
import mm.oasis.ui.chat.message.AttachmentsAdapter
import kotlin.math.*

class RequestView(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    /* MAIN */
    var request = Request()
    var onSend: ((Request) -> Unit)? = null
    var onAddAttachment: (() -> Unit)? = null

    /* UI REFERENCES */
    private val content: EditText
    private val send: ImageButton
    private val addAttachment: ImageButton

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

    /* ANIM & TOUCH */
    private var fullSettingsHeight = 0
    private var initialY = 0f
    private var initialHeight = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /* STATE */
    private var isGenerating = false

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.request_fields, this, true)

        // MAIN
        content = findViewById(R.id.contentInput)
        send = findViewById(R.id.sendButton)
        addAttachment = findViewById(R.id.addAttachment)

        // REASONING
        reasoningYes = findViewById(R.id.reasoningYes)
        reasoningAuto = findViewById(R.id.reasoningAuto)
        reasoningNo = findViewById(R.id.reasoningNo)

        // PARAMETERS
        temperatureField = findViewById(R.id.temperature)
        maxTokensField = findViewById(R.id.maxTokens)

        // ATTACHMENTS
        attachmentsList = findViewById(R.id.attachmentsList)
        attachmentsAdapter = AttachmentsAdapter { updateAttachmentsVisibility() }
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
    }

    fun addAttachment(part: ContentPart) {
        attachmentsAdapter.addItem(part)
        updateAttachmentsVisibility()
    }

    fun setGenerating(generating: Boolean) {
        isGenerating = generating
        send.setImageResource(if (generating) R.drawable.ic_stop else R.drawable.ic_send)
    }

    fun clear() {
        request = Request()
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
        if (Agent.isGenerating) {
            onSend?.invoke(request)
            return
        }

        val contentText = content.text.toString()
        val attachments = attachmentsAdapter.getItems()
        if (contentText.isBlank() && attachments.isEmpty()) return

        request.apply {
            model = ProfileRepository.currentProfile?.model?.id ?: "MODEL NOT SELECTED"
            includeReasoning = when (reasoningMode) {
                ReasoningMode.AUTO -> null
                ReasoningMode.ENABLED -> true
                ReasoningMode.DISABLED -> false
            }
            maxTokens = temperatureField.text.toString().takeIf { it.isNotBlank() }?.toIntOrNull()
            topP = maxTokensField.text.toString().takeIf { it.isNotBlank() }?.toDoubleOrNull()
            tools = toolsAdapter.enabledTools

            val textParts = attachments.filterIsInstance<ContentPart.TextPart>()
            val mediaParts = attachments.filter { it !is ContentPart.TextPart }

            textParts.forEach { part ->
                messages += Message(
                    role = Message.MessageRole.SYSTEM,
                    content = MessageContent.Parts(listOf(part))
                )
            }

            val userContentParts = mutableListOf<ContentPart>()
            if (contentText.isNotBlank()) {
                userContentParts.add(ContentPart.TextPart(contentText))
            }
            userContentParts.addAll(mediaParts)

            messages += Message(
                avatarUrl = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=${ProfileRepository.currentProfile?.endPoint}",
                role = Message.MessageRole.USER,
                content = MessageContent.Parts(userContentParts),
                name = ProfileRepository.currentProfile?.endpointDomain() ?: "YOU"
            )
        }

        onSend?.invoke(request)
        clear()
    }

    private fun updateAttachmentsVisibility() {
        attachmentsList.visibility = if (attachmentsAdapter.itemCount > 0) VISIBLE else GONE
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