package mm.oasis.ui.models

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.serialization.dto.LLMRaw
import mm.oasis.serialization.dto.LLMResponse

class ModelsAdapter(private val onClick: (LLMRaw) -> Unit) : RecyclerView.Adapter<ModelsAdapter.ViewHolder>() {
    private var allModels: List<LLMRaw> = emptyList()
    private var viewModels: List<LLMRaw> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun setModels(newModels: LLMResponse) {
        allModels = newModels.data
        viewModels = newModels.data
        notifyDataSetChanged()
    }

    fun getItem(position: Int) = viewModels[position]

    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        val lowerQuery = query.lowercase().trim()
        viewModels = if (lowerQuery.isEmpty()) {
            allModels
        } else {
            allModels.filter { item ->
                lowerQuery in item.id.lowercase() ||
                        lowerQuery in (item.ownedBy?.lowercase() ?: "")
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = viewModels[position]
        holder.bind(model)
        holder.itemView.setOnClickListener { onClick(model) }
    }

    override fun getItemCount() = viewModels.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val modelId: TextView = view.findViewById(R.id.modelId)
        private val modelProvider: TextView = view.findViewById(R.id.modelProvider)
        private val modelPricing: TextView = view.findViewById(R.id.modelPricing)

        fun bind(model: LLMRaw) {
            modelId.text = model.id
            modelProvider.text = if (model.ownedBy != null) "by ${model.ownedBy}" else "PROVIDER NOT SPECIFIED"
            modelPricing.text = if (model.pricing?.prompt != null && model.pricing.completion != null) {
                "PROMPT: $${model.pricing.prompt}/M | COMPLETION: $${model.pricing.completion}/M"
            } else "PRICE NOT SPECIFIED"
        }
    }
}