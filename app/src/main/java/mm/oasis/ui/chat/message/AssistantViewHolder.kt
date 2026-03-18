package mm.oasis.ui.chat.message

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.serialization.dto.Message
import kotlin.math.max

class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val avatarView: ImageView = view.findViewById(R.id.avatar)
    private val nameView: TextView = view.findViewById(R.id.name)
    private val contentView: TextView = view.findViewById(R.id.content)
    private val reasoningList: RecyclerView = view.findViewById(R.id.reasoning_list)
    private var lastTagetPos: Int? = null
    private var currentBoundMessage: Message? = null
    
    private var lastContent: String? = null
    private var lastReasoning: String? = null
    private var lastTargetHeight: Int = -1

    private var heightAnimator: ValueAnimator? = null

    init {
        reasoningList.isNestedScrollingEnabled = true
        reasoningList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?) {
        val content = message.display
        val reasoning = message.reasoning
        val isDifferentMessage = currentBoundMessage !== message

        if (!isDifferentMessage && lastContent == content && lastReasoning == reasoning) {
            return
        }

        if (isDifferentMessage) {
            currentBoundMessage = message
            lastTagetPos = null
            lastContent = null
            lastReasoning = null
            lastTargetHeight = -1
            
            Glide.with(itemView.context)
                .load(message.avatarUrl)
                .centerCrop()
                .into(avatarView)

            nameView.text = "[${message.name ?: "ASSISTANT"}] >"
        }

        if (content != lastContent) {
            markwon?.setMarkdown(contentView, content)
            lastContent = content
        }

        val hasContent = content.isNotBlank()

        if (!reasoning.isNullOrBlank()) {
            val reasoningParts = reasoning.split("\n\n").filter { it.isNotBlank() }
            reasoningList.visibility = View.VISIBLE

            val targetPos = if (hasContent) {
                reasoningParts.size - 1
            } else {
                max(0, reasoningParts.size - 2)
            }

            var adapter = reasoningList.adapter as? ReasoningAdapter
            if (adapter == null) {
                adapter = ReasoningAdapter(reasoningParts, markwon, targetPos) { height ->
                    animateReasoningHeight(height)
                }
                reasoningList.adapter = adapter
            } else if (reasoning != lastReasoning || lastTagetPos != targetPos) {
                adapter.updateData(reasoningParts, targetPos)
            }
            
            if (lastTagetPos == null) {
                reasoningList.scrollToPosition(targetPos)
                lastTagetPos = targetPos
            } else if (lastTagetPos != targetPos) {
                reasoningList.smoothScrollToPosition(targetPos)
                lastTagetPos = targetPos
            }
            
            lastReasoning = reasoning
        } else {
            reasoningList.visibility = View.GONE
            heightAnimator?.cancel()
            lastTargetHeight = -1
            lastReasoning = null
        }
    }

    private fun animateReasoningHeight(targetHeight: Int) {
        if (this.lastTargetHeight == targetHeight) return
        this.lastTargetHeight = targetHeight

        val currentParamsHeight = reasoningList.layoutParams.height
        
        // During streaming (small changes), skip animation to avoid "shaking"
        // Starting a new animation on every few characters is what causes the jitter
        if (Math.abs(currentParamsHeight - targetHeight) < 50 && currentParamsHeight > 0) {
            heightAnimator?.cancel()
            val params = reasoningList.layoutParams
            params.height = targetHeight
            reasoningList.layoutParams = params
            return
        }

        heightAnimator?.cancel()

        val startHeight = if (reasoningList.height > 0) reasoningList.height else currentParamsHeight
        if (startHeight <= 0 && targetHeight >= 0) {
            // Initial bind or setting height for the first time
            val params = reasoningList.layoutParams
            params.height = targetHeight
            reasoningList.layoutParams = params
            return
        }
        
        heightAnimator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = 200
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                val params = reasoningList.layoutParams
                params.height = value
                reasoningList.layoutParams = params
            }
            start()
        }
    }
}
