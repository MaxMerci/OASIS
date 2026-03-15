package mm.oasis.ui.chat

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.ContentPart
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent
import mm.oasis.serialization.dto.Request
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
    private val includeReasoning: CheckBox
    private val temperature: EditText
    private val topP: EditText
    private val maxTokens: EditText
    private val attachmentsList: RecyclerView
    private val attachmentsAdapter: AttachmentsAdapter

    /* ANIM */
    private var fullSettingsHeight = 0
    private var initialY = 0f
    private var initialHeight = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var isGenerating = false

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.request_field, this, true)

        content = findViewById(R.id.contentInput)
        send = findViewById(R.id.sendButton)
        addAttachment = findViewById(R.id.addAttachment)
        includeReasoning = findViewById(R.id.includeReasoning)
        temperature = findViewById(R.id.temperature)
        topP = findViewById(R.id.topP)
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

        updateSettingsHeight(0)

        addAttachment.setOnClickListener {
            onAddAttachment?.invoke()
        }

        send.setOnClickListener {
            if (isGenerating) {
                onSend?.invoke(Request())
                return@setOnClickListener
            }

            val contentText = content.text.toString()
            val maxTokensText = maxTokens.text.toString()
            val temperatureText = temperature.text.toString()
            val topPText = topP.text.toString()
            val attachments = attachmentsAdapter.getItems()

            if (contentText.isNotBlank() || attachments.isNotEmpty()) {
                request.model = ProfileRepository.currentProfile?.model?.id ?: "MODEL NOT SELECTED"
                request.includeReasoning = includeReasoning.isChecked
                request.maxTokens = if (maxTokensText.isNotBlank()) maxTokensText.toInt() else null
                request.temperature = if (temperatureText.isNotBlank()) temperatureText.toDouble() else 1.0
                request.topP = if (topPText.isNotBlank()) topPText.toDouble() else null

                val parts = mutableListOf<ContentPart>()
                if (contentText.isNotBlank()) {
                    parts.add(ContentPart.TextPart(contentText))
                }
                parts.addAll(attachments)

                request.messages = listOf(
                    Message(
                        avatarUrl = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=${ProfileRepository.currentProfile?.endPoint}",
                        role = Message.MessageRole.USER,
                        content = MessageContent.Parts(parts),
                        name = ProfileRepository.currentProfile?.endpointDomain() ?: "YOU",
                    )
                )
                onSend?.invoke(request)
                clear()
            }
        }
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
