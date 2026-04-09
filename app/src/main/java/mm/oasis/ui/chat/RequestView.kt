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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mm.oasis.R
import mm.oasis.remote.Agent
import mm.oasis.remote.tools.RelevanceFit
import mm.oasis.remote.tools.WebSearch.searchLinks
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.ContentPart
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent
import mm.oasis.serialization.dto.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ClickableViewAccessibility")
class RequestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    /* MAIN */
    var request = Request()
    var onSend: ((Request) -> Unit)? = null
    var onAddAttachment: (() -> Unit)? = null
    /* VALUES */
    private val content: EditText
    private val settingsContainer: View
    private val send: ImageButton
    private val addAttachment: ImageButton
    /* REASONING */
    private val reasoningYes: TextView
    private val reasoningAuto: TextView
    private val reasoningNo: TextView
    private var reasoningState: Boolean? = null // null = AUTO, true = YES, false = NO
    /* OTHER */
    private val temperature: EditText
    private val maxTokens: EditText
    private val attachmentsList: RecyclerView
    private val attachmentsAdapter: AttachmentsAdapter
    /* TOOLS */
    private val toolsAdapter: ToolsListAdapter
    private val toolsList: RecyclerView
    /* ANIM */
    private var fullSettingsHeight = 0
    private var initialY = 0f
    private var initialHeight = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var isGenerating = false

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.request_fields, this, true)

        content = findViewById(R.id.contentInput)
        send = findViewById(R.id.sendButton)
        addAttachment = findViewById(R.id.addAttachment)
        
        reasoningYes = findViewById(R.id.reasoningYes)
        reasoningAuto = findViewById(R.id.reasoningAuto)
        reasoningNo = findViewById(R.id.reasoningNo)
        
        temperature = findViewById(R.id.temperature)
        maxTokens = findViewById(R.id.maxTokens)
        settingsContainer = findViewById(R.id.settingsContainer)
        attachmentsList = findViewById(R.id.attachmentsList)

        attachmentsAdapter = AttachmentsAdapter { _ ->
            updateAttachmentsVisibility()
        }
        attachmentsList.adapter = attachmentsAdapter

        settingsContainer.visibility = VISIBLE
        settingsContainer.measure(
            MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        fullSettingsHeight = settingsContainer.measuredHeight

        toolsList = findViewById(R.id.toolsList)
        toolsAdapter = ToolsListAdapter()
        toolsList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        toolsList.adapter = toolsAdapter

        updateSettingsHeight(0)

        addAttachment.setOnClickListener {
            onAddAttachment?.invoke()
        }

        updateReasoningState(reasoningState)
        reasoningYes.setOnClickListener { updateReasoningState(true) }
        reasoningAuto.setOnClickListener { updateReasoningState(null) }
        reasoningNo.setOnClickListener { updateReasoningState(false) }

        send.setOnClickListener { onClickSend() }
    }

    private fun onClickSend() {
        if (Agent.isGenerating) {
            onSend?.invoke(request)
            return
        }

        val contentText = content.text.toString()
        val temperature = this@RequestView.temperature.text.toString()
        val topPText = maxTokens.text.toString()
        val attachments = attachmentsAdapter.getItems().toMutableList()

        if (contentText.isNotBlank() || attachments.isNotEmpty()) {
            request.model = ProfileRepository.currentProfile?.model?.id ?: "MODEL NOT SELECTED"
            request.includeReasoning = reasoningState
            request.maxTokens = if (temperature.isNotBlank()) temperature.toInt() else null
            request.topP = if (topPText.isNotBlank()) topPText.toDouble() else null
            request.tools = toolsAdapter.enabledTools

            val parts = mutableListOf<ContentPart>()
            if (contentText.isNotBlank()) {
                parts.add(ContentPart.TextPart(contentText))
            }
            for (part in attachments) {
                if (part is ContentPart.TextPart) {
                    request.messages += Message(
                        role = Message.MessageRole.SYSTEM,
                        content = MessageContent.Parts(listOf(part))
                    )
                    attachments.remove(part)
                }
            }
            parts.addAll(attachments)

            request.messages += Message(
                avatarUrl = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=${ProfileRepository.currentProfile?.endPoint}",
                role = Message.MessageRole.USER,
                content = MessageContent.Parts(parts),
                name = ProfileRepository.currentProfile?.endpointDomain() ?: "YOU",
            )
            onSend?.invoke(request)
            clear()
        }
    }

    private fun updateReasoningState(state: Boolean?) {
        reasoningState = state
        reasoningYes.setBackgroundResource(if (state == true) R.drawable.ic_bg_g_r else android.R.color.transparent)
        reasoningAuto.setBackgroundResource(if (state == null) R.drawable.ic_bg_g_r else android.R.color.transparent)
        reasoningNo.setBackgroundResource(if (state == false) R.drawable.ic_bg_g_r else android.R.color.transparent)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialY = event.rawY
                initialHeight = settingsContainer.layoutParams.height
            }
            MotionEvent.ACTION_MOVE -> {
                val diff = abs(event.rawY - initialY)
                if (diff > touchSlop) {
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

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

    fun addAttachment(part: ContentPart) {
        attachmentsAdapter.addItem(part)
        updateAttachmentsVisibility()
    }

    private fun updateAttachmentsVisibility() {
        attachmentsList.visibility = if (attachmentsAdapter.itemCount > 0) VISIBLE else GONE
    }

    fun clear() {
        request = Request()
        content.text.clear()
        attachmentsAdapter.clear()
        updateAttachmentsVisibility()
    }

    fun setGenerating(generating: Boolean) {
        this.isGenerating = generating
        if (generating) {
            send.setImageResource(R.drawable.ic_stop)
        } else {
            send.setImageResource(R.drawable.ic_send)
        }
    }

    private fun updateSettingsHeight(height: Int) {
        val params = settingsContainer.layoutParams
        params.height = height
        settingsContainer.layoutParams = params
    }

    private fun animateHeightChange(from: Int, to: Int) {
        val animator = ValueAnimator.ofInt(from, to)
        animator.addUpdateListener { valueAnimator ->
            updateSettingsHeight(valueAnimator.animatedValue as Int)
        }
        animator.duration = 250
        animator.start()
    }
}
