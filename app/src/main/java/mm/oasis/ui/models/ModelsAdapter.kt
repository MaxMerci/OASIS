package mm.oasis.ui.models

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import mm.oasis.R
import mm.oasis.serialization.dto.LLMResponse

class ModelsAdapter : BaseAdapter() {
    var allModels: LLMResponse? = null
    var viewModels: LLMResponse? = null

    override fun getCount() = viewModels?.data?.size ?: 0
    override fun getItem(position: Int) = viewModels?.data[position]
    override fun getItemId(position: Int) = position.toLong()

    @SuppressLint("NotifyDataSetChanged")
    fun setModels(newModels: LLMResponse) {
        println(newModels)
        allModels = newModels
        viewModels = newModels
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        val lowerQuery = query.lowercase().trim()
        if (lowerQuery.isEmpty()) {
            viewModels = allModels
        } else {
            val filteredList = allModels?.data?.filter { item ->
                lowerQuery in item.id.lowercase() ||
                        lowerQuery in (item.id.lowercase()) ||
                        lowerQuery in (item.ownedBy?.lowercase() ?: "")
            } ?: emptyList()

            viewModels = allModels?.copy(data = filteredList)
        }
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.item_model, parent, false)

        val model = viewModels!!.data[position]

        val modelId: TextView = view.findViewById(R.id.modelId)
        val modelProvider: TextView = view.findViewById(R.id.modelProvider)
        val modelPricing: TextView = view.findViewById(R.id.modelPricing)

        modelId.text = model.name
        modelProvider.text = if (model.ownedBy != null) "by ${model.ownedBy}" else "PROVIDER NOT SPECIFIED"
        modelPricing.text = if (model.pricing?.prompt != null && model.pricing.completion != null) {
            "PROMPT: $${model.pricing.prompt}/M | COMPLETION: $${model.pricing.completion}/M"
        } else "PRICE NOT SPECIFIED"

        return view
    }
}