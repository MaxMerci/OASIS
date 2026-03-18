package mm.oasis.ui.chat.message

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import mm.oasis.R

class ReasoningViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val textView: TextView = view.findViewById(R.id.text)
}

class ReasoningAdapter(
    private var items: List<String>,
    private val markwon: Markwon?,
    private var targetPosition: Int,
    private val heightResolve: (Int) -> Unit
) : RecyclerView.Adapter<ReasoningViewHolder>() {

    fun updateData(newItems: List<String>, newTargetPosition: Int) {
        val oldItems = items
        val oldTarget = targetPosition
        
        if (oldItems == newItems && oldTarget == newTargetPosition) return

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = 
                oldItemPosition == newItemPosition
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = 
                oldItems[oldItemPosition] == newItems[newItemPosition]
        })

        items = newItems
        targetPosition = newTargetPosition
        diffResult.dispatchUpdatesTo(this)
        
        if (oldTarget != newTargetPosition) {
            notifyItemChanged(newTargetPosition)
            if (oldTarget < items.size) {
                notifyItemChanged(oldTarget)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReasoningViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reasoning, parent, false)
        return ReasoningViewHolder(v)
    }

    override fun onBindViewHolder(holder: ReasoningViewHolder, position: Int) {
        val text = items[position]
        // Avoid re-setting markdown if it's identical to prevent unnecessary layout passes
        if (holder.textView.text.toString() != text) {
            markwon?.setMarkdown(holder.textView, text)
        }
        
        if (position == targetPosition) {
            holder.itemView.post {
                if (holder.bindingAdapterPosition == targetPosition) {
                    val h = holder.itemView.height
                    if (h > 0) {
                        heightResolve(h)
                    }
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
