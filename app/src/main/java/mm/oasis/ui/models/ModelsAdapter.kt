package mm.oasis.ui.models

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.serialization.dto.LLMRaw
import mm.oasis.serialization.dto.LLMResponse

class ModelsAdapter(private val onModelClick: (LLMRaw) -> Unit) : RecyclerView.Adapter<ModelsAdapter.ModelViewHolder>() {
    private var allModels: LLMResponse? = null
    private var viewModels: List<LLMRaw> = emptyList()
    private val expandedPositions = mutableSetOf<String>()

    @SuppressLint("NotifyDataSetChanged")
    fun setModels(newModels: LLMResponse) {
        allModels = newModels
        viewModels = newModels.data
        expandedPositions.clear()
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
        private val container: ViewGroup = view.findViewById(R.id.textContainer)
        private val modelId: TextView = view.findViewById(R.id.modelId)
        private val modelExtra: TextView = view.findViewById(R.id.modelExtra)
        private val toggleButton: ImageView = view.findViewById(R.id.toggleButton)
        private var currentAnimator: ValueAnimator? = null

        fun bind(model: LLMRaw) {
            modelId.text = model.id
            modelExtra.text = formatExtra(model.extra)

            val isExpanded = expandedPositions.contains(model.id)
            toggleButton.rotation = if (isExpanded) 180f else 0f
            
            currentAnimator?.cancel()

            val params = container.layoutParams
            if (isExpanded) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                val width = if (container.width > 0) {
                    container.width
                } else {
                    val metrics = itemView.resources.displayMetrics
                    metrics.widthPixels - ((16 + 8 + toggleButton.layoutParams.width) * metrics.density).toInt()
                }
                
                val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                modelId.measure(widthSpec, heightSpec)
                params.height = modelId.measuredHeight
            }
            container.layoutParams = params

            itemView.setOnClickListener {
                onModelClick(model)
            }

            toggleButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                val isExpanding = !expandedPositions.contains(model.id)
                val startHeight = container.height

                val widthSpec = View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                
                val targetHeight = if (isExpanding) {
                    container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    container.measure(widthSpec, heightSpec)
                    container.measuredHeight
                } else {
                    modelId.measure(widthSpec, heightSpec)
                    modelId.measuredHeight
                }

                container.layoutParams.height = startHeight

                currentAnimator?.cancel()
                currentAnimator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
                    duration = 300
                    addUpdateListener { animator ->
                        val value = animator.animatedValue as Int
                        container.layoutParams.height = value
                        container.requestLayout()
                        
                        val fraction = animator.animatedFraction
                        toggleButton.rotation = if (isExpanding) fraction * 180f else 180f - (fraction * 180f)
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (isExpanding) {
                                expandedPositions.add(model.id)
                                container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                            } else {
                                expandedPositions.remove(model.id)
                                container.layoutParams.height = targetHeight
                            }
                        }
                    })
                    start()
                }
            }
        }
    }

    private fun formatExtra(map: Map<String, Any?>, indent: Int = 0): String {
        val sb = StringBuilder()
        val spaces = "  ".repeat(indent)
        map.forEach { (key, value) ->
            sb.append(spaces).append(key).append(": ")
            if (value is Map<*, *>) {
                sb.append("\n")
                @Suppress("UNCHECKED_CAST")
                sb.append(formatExtra(value as Map<String, Any?>, indent + 1))
            } else {
                sb.append(value.toString()).append("\n")
            }
        }
        return sb.toString().trimEnd()
    }
}
