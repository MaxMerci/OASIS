package mm.oasis.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import mm.oasis.R
import mm.oasis.remote.ToolRegistry
import mm.oasis.serialization.dto.Tool
import mm.oasis.ui.objects.DialogField
import mm.oasis.ui.objects.FieldType
import mm.oasis.ui.objects.ModalDialogBuilder

class ToolsListAdapter : RecyclerView.Adapter<ToolsListAdapter.ToolViewHolder>() {
    private val disabledTools = mutableSetOf<Tool>()

    val enabledTools: List<Tool>
        get() = ToolRegistry.tools.filter { it !in disabledTools }

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    override fun getItemCount() = ToolRegistry.tools.size

    override fun getItemViewType(position: Int): Int = if (ToolRegistry.tools[position] in disabledTools) 0 else 1

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
            .replace("_", " ")

        if (getItemViewType(position) == 1) holder.itemView.setBackgroundResource(R.drawable.ic_bg_g_r)
        else holder.itemView.setBackgroundResource(R.drawable.ic_bg_b_r)

        holder.itemView.setOnClickListener {
            if (!disabledTools.remove(tool)) disabledTools.add(tool)
            notifyItemChanged(holder.bindingAdapterPosition)
        }

        holder.itemView.setOnLongClickListener {
            showToolInfo(it, tool)
            true
        }
    }

    private fun showToolInfo(view: View, tool: Tool) {
        val dialog = ModalDialogBuilder(view.context)
            .setTitle(tool.function.name)
            .setOkText("CLOSE")
            .hideCancel()

        addInfoFields(dialog, json.encodeToJsonElement(tool) as JsonObject, 0)

        dialog.show()
    }

    private fun addInfoFields(dialog: ModalDialogBuilder, obj: Map<String, JsonElement>, depth: Int) {
        obj.forEach { (key, value) ->
            val title = key.replace('_', ' ').uppercase()

            when (value) {
                is JsonObject if value.isNotEmpty() -> {
                    dialog.addField(DialogField("", title, FieldType.INFO, depth = depth))
                    addInfoFields(dialog, value, depth + 1)
                }

                is JsonArray if value.any { it is JsonObject || it is JsonArray } -> {
                    dialog.addField(DialogField("", title, FieldType.INFO, depth = depth))
                    addInfoFields(dialog, value.withIndex().associate { "[${it.index}]" to it.value }, depth + 1)
                }

                else -> {
                    dialog.addField(DialogField("", title, FieldType.INFO, defaultValue = formatValue(value), depth = depth))
                }
            }
        }
    }

    private fun formatValue(value: JsonElement): String = when (value) {
        is JsonNull -> "—"
        is JsonPrimitive -> value.content
        is JsonArray -> if (value.isEmpty()) "—" else value.joinToString(", ") { formatValue(it) }
        is JsonObject -> "—"
    }

    class ToolViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
    }
}
