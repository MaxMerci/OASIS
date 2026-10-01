package mm.oasis.ui.chat.message

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import mm.oasis.R
import mm.oasis.remote.Workspace
import mm.oasis.serialization.dto.Message
import mm.oasis.serialization.dto.MessageContent
import mm.oasis.ui.objects.WorkspaceFiles
import java.io.File

class UserViewHolder(
    view: View,
    private val onLongClick: (Message) -> Unit
) : RecyclerView.ViewHolder(view) {
    companion object {
        const val PULSE_DURATION = 800L
    }

    private val nameView: TextView = view.findViewById(R.id.name)
    private val contentView: TextView = view.findViewById(R.id.content)
    private val attContainer: LinearLayout = view.findViewById(R.id.attachments_container)

    private var boundMessage: Message? = null
    private var pulse: ValueAnimator? = null

    init {
        val longClick = View.OnLongClickListener {
            boundMessage?.let(onLongClick)
            boundMessage != null
        }
        itemView.setOnLongClickListener(longClick)
        contentView.setOnLongClickListener(longClick)
    }

    @SuppressLint("SetTextI18n")
    fun bind(message: Message, markwon: Markwon?, editing: Boolean) {
        boundMessage = message
        setEditing(editing)

        nameView.text = if (editing) "[${message.name}] > EDITING" else "[${message.name}] >"
        markwon?.setMarkdown(contentView, message.display) ?: run {
            contentView.text = message.display
        }
        contentView.visibility = if (message.display.isBlank()) View.GONE else View.VISIBLE

        attContainer.removeAllViews()
        val inflater = LayoutInflater.from(itemView.context)
        when (val content = message.content) {
            is MessageContent.Parts -> {
                content.parts.forEach { part ->
                    if (!part.fileName.isNullOrEmpty()) {
                        val chip = inflater.inflate(R.layout.item_attachment, attContainer, false)
                        val name = chip.findViewById<TextView>(R.id.attachment_name)
                        val path = "input/" + File(part.fileName!!).name
                        name.text = part.fileName
                        if (Workspace.resolve(path) == null) {
                            chip.alpha = 0.5f
                            name.paintFlags = name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        }
                        chip.setOnClickListener { WorkspaceFiles.open(itemView, path) }
                        attContainer.addView(chip)
                    }
                }
            }
            else -> {}
        }
    }

    private fun setEditing(editing: Boolean) {
        if (editing == (pulse != null)) return
        pulse?.cancel()
        pulse = null
        if (!editing) {
            setContentAlpha(1f)
            return
        }
        pulse = ValueAnimator.ofFloat(1f, 0.35f).apply {
            duration = PULSE_DURATION
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { setContentAlpha(it.animatedValue as Float) }
            start()
        }
    }

    private fun setContentAlpha(alpha: Float) {
        contentView.alpha = alpha
        attContainer.alpha = alpha
    }
}
