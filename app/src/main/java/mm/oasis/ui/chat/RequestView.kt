package mm.oasis.ui.chat

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.*
import mm.oasis.ui.chat.message.AttachmentsAdapter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RequestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    /* MAIN */
    var onSend: ((Request) -> Unit)? = null
    var onStop: (() -> Unit)? = null
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

        val contentText = content.text.toString()
        val attachments = attachmentsAdapter.getItems()
        if (contentText.isBlank() && attachments.isEmpty()) return

        val profile = ProfileRepository.currentProfile
        val messages = mutableListOf<Message>()

        // текстовые файлы уходят отдельными system сообщениями
        attachments.filterIsInstance<ContentPart.TextPart>().forEach { part ->
            messages += Message(
                role = Message.MessageRole.SYSTEM,
                content = MessageContent.Parts(listOf(part))
            )
        }

        val userParts = mutableListOf<ContentPart>()
        if (contentText.isNotBlank()) userParts += ContentPart.TextPart(contentText)
        userParts += attachments.filter { it !is ContentPart.TextPart }

        messages += Message(
            avatarUrl = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=${profile?.endPoint}",
            role = Message.MessageRole.USER,
            content = MessageContent.Parts(userParts),
            name = profile?.endpointDomain() ?: "YOU"
        )

        val request = Request(
            messages = messages,
            model = profile?.model?.id,
            temperature = temperatureField.text.toString().trim().toDoubleOrNull(),
            maxTokens = maxTokensField.text.toString().trim().toDoubleOrNull()?.toInt(),
            includeReasoning = when (reasoningMode) {
                ReasoningMode.AUTO -> null
                ReasoningMode.ENABLED -> true
                ReasoningMode.DISABLED -> false
            },
            tools = toolsAdapter.enabledTools.toList().ifEmpty { null }
        )

        onSend?.invoke(request)
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