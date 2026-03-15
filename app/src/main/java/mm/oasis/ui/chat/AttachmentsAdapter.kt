package mm.oasis.ui.chat

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.serialization.dto.ContentPart

class AttachmentsAdapter(
    private val onRemove: (ContentPart) -> Unit
) : RecyclerView.Adapter<AttachmentsAdapter.ViewHolder>() {

    private val items = mutableListOf<ContentPart>()

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(newItems: List<ContentPart>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addItem(item: ContentPart) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun getItems(): List<ContentPart> = items

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

        fun bind(item: ContentPart) {
            name.text = item.fileName ?: "FILE"
            itemView.setOnClickListener {
                onRemove(item)
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    items.removeAt(pos)
                    notifyItemRemoved(pos)
                }
            }
        }
    }
}
