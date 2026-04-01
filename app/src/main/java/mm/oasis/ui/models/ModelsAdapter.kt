package mm.oasis.ui.models

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.serialization.dto.LLMRaw
import mm.oasis.serialization.dto.LLMResponse

class ModelsAdapter(private val onModelClick: (LLMRaw) -> Unit) : RecyclerView.Adapter<ModelsAdapter.ModelViewHolder>() {
    private var allModels: LLMResponse? = null
    private var viewModels: List<LLMRaw> = emptyList()

    private val expandedItems = mutableSetOf<String>()

    @SuppressLint("NotifyDataSetChanged")
    fun setModels(newModels: LLMResponse) {
        allModels = newModels
        viewModels = newModels.data
        expandedItems.clear()
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
        private val modelExtra: TextView = view.findViewById(R.id.modelExtra)
        private val toggleButton: ImageView = view.findViewById(R.id.toggleButton)

        fun bind(model: LLMRaw) {
            modelId.text = model.id
            modelExtra.text = formatExtra(model.extra)

            val isExpanded = expandedItems.contains(model.id)

            modelExtra.visibility = if (isExpanded) VISIBLE else GONE
            toggleButton.rotation = if (isExpanded) 180f else 0f
            modelExtra.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT

            val toggle = {
                if (expandedItems.contains(model.id)) {
                    expandedItems.remove(model.id)
                    collapse(modelExtra)
                    toggleButton.animate().rotation(0f).start()
                    modelExtra.animate().alpha(0f)
                } else {
                    expandedItems.add(model.id)
                    expand(modelExtra)
                    toggleButton.animate().rotation(180f).start()
                    modelExtra.animate().alpha(1f)
                }
            }

            toggleButton.setOnClickListener { toggle() }
            modelExtra.setOnClickListener { toggle() }

            modelId.setOnClickListener { onModelClick(model) }

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

    private fun expand(view: View) {
        view.measure(
            MeasureSpec.makeMeasureSpec((view.parent as View).width, MeasureSpec.EXACTLY),
            MeasureSpec.UNSPECIFIED
        )
        val targetHeight = view.measuredHeight

        view.layoutParams.height = 0
        view.visibility = VISIBLE

        val animator = ValueAnimator.ofInt(0, targetHeight)
        animator.duration = 250
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener {
            val value = it.animatedValue as Int
            view.layoutParams.height = value
            view.requestLayout()
        }

        animator.start()
    }

    private fun collapse(view: View) {
        val initialHeight = view.measuredHeight

        val animator = ValueAnimator.ofInt(initialHeight, 0)
        animator.duration = 250
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener {
            val value = it.animatedValue as Int
            view.layoutParams.height = value
            view.requestLayout()
        }

        animator.doOnEnd {
            view.visibility = GONE
        }

        animator.start()
    }
}
