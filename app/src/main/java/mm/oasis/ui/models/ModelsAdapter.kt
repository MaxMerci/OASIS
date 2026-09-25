package mm.oasis.ui.models

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.serialization.dto.LLMRaw
import mm.oasis.serialization.dto.LLMResponse

class ModelsAdapter(
    private val onModelClick: (LLMRaw) -> Unit,
    private val onModelOpen: (LLMRaw) -> Unit,
) : RecyclerView.Adapter<ModelsAdapter.ModelViewHolder>() {
    private var allModels: LLMResponse? = null
    private var viewModels: List<LLMRaw> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun setModels(newModels: LLMResponse) {
        allModels = newModels
        viewModels = newModels.data
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        val lowerQuery = query.lowercase().trim()
        viewModels = if (lowerQuery.isEmpty()) {
            allModels?.data ?: emptyList()
        } else {
            allModels?.data?.filter { item ->
                lowerQuery in item.id.lowercase() ||
                        lowerQuery in (item.extra.toString().lowercase())
            } ?: emptyList()
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(viewModels[position])
    }

    override fun getItemCount() = viewModels.size

    inner class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val modelId: TextView = view.findViewById(R.id.modelId)
        private val openButton: Button = view.findViewById(R.id.openButton)

        fun bind(model: LLMRaw) {
            modelId.text = model.id

            modelId.setOnClickListener { onModelClick(model) }
            openButton.setOnClickListener { onModelOpen(model) }

            itemView.apply {
                alpha = 0f
                scaleX = 0f

                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .setDuration(300L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }
    }
}
