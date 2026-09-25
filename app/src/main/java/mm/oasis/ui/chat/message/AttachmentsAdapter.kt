package mm.oasis.ui.chat.message

import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.serialization.dto.ContentPart

class AttachmentsAdapter(
    private val onOpen: (Attachment) -> Unit,
    private val onRemove: (Attachment) -> Unit
) : RecyclerView.Adapter<AttachmentsAdapter.ViewHolder>() {

    /** часть для запроса + исходный файл, чтобы его можно было открыть */
    data class Attachment(val part: ContentPart, val uri: Uri?)

    private val items = mutableListOf<Attachment>()

    fun addItem(item: Attachment) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun getItems(): List<ContentPart> = items.map { it.part }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attachment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.attachment_name)
        private val remove: ImageView = view.findViewById(R.id.attachment_remove)

        fun bind(item: Attachment) {
            name.text = item.part.fileName ?: "FILE"
            remove.visibility = View.VISIBLE
            itemView.setOnClickListener { onOpen(item) }
            remove.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                items.removeAt(pos)
                notifyItemRemoved(pos)
                onRemove(item)
            }
        }
    }
}
