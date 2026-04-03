package mm.oasis.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.remote.ToolRegistry
import mm.oasis.serialization.dto.Tool

class ToolsListAdapter : RecyclerView.Adapter<ToolsListAdapter.ToolViewHolder>() {
    val enabledTools = mutableListOf<Tool>()

    override fun getItemCount() = ToolRegistry.tools.size

    override fun getItemViewType(position: Int): Int = if (enabledTools.contains(ToolRegistry.tools[position])) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_tool,
                parent,
                false
            )

        return ToolViewHolder(view)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        val tool = ToolRegistry.tools[position]

        holder.name.text = tool.function.name

        if (getItemViewType(position) == 1) holder.itemView.setBackgroundResource(R.drawable.ic_bg_g_r)
        else holder.itemView.setBackgroundResource(R.drawable.ic_bg_b_r)

        holder.itemView.setOnClickListener {
            if (enabledTools.contains(tool)) enabledTools.remove(tool)
            else enabledTools.add(tool)
            notifyItemChanged(position)
        }
    }

    class ToolViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
    }
}