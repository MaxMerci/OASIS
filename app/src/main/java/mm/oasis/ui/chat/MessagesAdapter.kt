package mm.oasis.ui.chat

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import mm.oasis.R
import mm.oasis.repository.ChatRepository
import mm.oasis.serialization.dto.Message
import mm.oasis.ui.chat.message.AssistantViewHolder
import mm.oasis.ui.chat.message.SystemViewHolder
import mm.oasis.ui.chat.message.UserViewHolder

class MessagesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var markwon: Markwon? = null

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_SYSTEM = 2
    }

    override fun getItemCount() = ChatRepository.currentChat.messages.size

    override fun getItemViewType(position: Int): Int {
        return when (ChatRepository.currentChat.messages[position].role) {
            Message.MessageRole.USER -> TYPE_USER
            Message.MessageRole.ASSISTANT -> TYPE_ASSISTANT
            else -> TYPE_SYSTEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (markwon == null) {
            val context = parent.context
            val jetbrainsMono = ResourcesCompat.getFont(context, R.font.jetbrainsmono)!!
            val latexSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                16f,
                context.resources.displayMetrics
            )

            markwon = Markwon.builder(context)
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(JLatexMathPlugin.create(latexSize) { builder ->
                    builder.inlinesEnabled(true)
                })
                .usePlugin(object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder.codeTypeface(jetbrainsMono)
                        builder.headingBreakHeight(1)
                        builder.thematicBreakHeight(1)
                        builder.bulletWidth(10)
                    }
                })
                .build()
        }

        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            TYPE_ASSISTANT -> AssistantViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
            else -> SystemViewHolder(inflater.inflate(R.layout.item_message_system, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = ChatRepository.currentChat.messages[position]
        when (holder) {
            is UserViewHolder -> holder.bind(message, markwon)
            is AssistantViewHolder -> holder.bind(message, markwon)
            is SystemViewHolder -> holder.bind(message, markwon)
        }
    }
}
