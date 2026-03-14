package mm.oasis.ui.models

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import mm.oasis.R
import mm.oasis.remote.ApiClient
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.LLMRaw
import kotlinx.coroutines.launch


class ModelsFragment : Fragment() {
    private lateinit var modelsView: ListView
    private lateinit var emptyView: TextView
    private lateinit var searchInput: EditText

    private lateinit var currentModelId: TextView
    private lateinit var currentModelProvider: TextView
    private lateinit var currentModelPricing: TextView

    private val modelsAdapter = ModelsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_models, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelsView = view.findViewById(R.id.modelsView)
        emptyView = view.findViewById(R.id.emptyView)
        searchInput = view.findViewById(R.id.searchInput)
        currentModelId = view.findViewById(R.id.currentModelId)
        currentModelProvider = view.findViewById(R.id.currentModelProvider)
        currentModelPricing = view.findViewById(R.id.currentModelPricing)

        modelsView.adapter = modelsAdapter
        modelsView.emptyView = emptyView

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // ДО
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // СЕЙЧАС
                val query = s.toString()
                modelsAdapter.filter(query)
            }
            override fun afterTextChanged(s: Editable?) {
                // ПОСЛЕ
            }
        })

        modelsView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            setCurrent(modelsAdapter.getItem(position))
        }

        loadModels()
        setCurrent(ProfileRepository.currentProfile.model)
    }

    fun loadModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val models = ApiClient.fetchModels()
                modelsAdapter.setModels(models)
            } catch (e: Exception) {
                emptyView.text = e.toString()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun setCurrent(model: LLMRaw?) {
        if (model != null) {
            ProfileRepository.currentProfile.model = model
            ProfileRepository.save()

            /* ID */
            currentModelId.text = model.id
            /* PROVIDER */
            currentModelProvider.visibility = VISIBLE
            currentModelProvider.text = model.ownedBy ?: "PROVIDER NOT SPECIFIED"
            /* PRICING */
            currentModelPricing.visibility = VISIBLE
            currentModelPricing.text =
                if (model.pricing?.input != null && model.pricing.output != null) {
                    "INPUT: $${model.pricing.input}/M | OUTPUT: $${model.pricing.output}/M"
                } else "PRICE NOT SPECIFIED"
        } else {
            currentModelId.text = "NOT SELECTED"
            currentModelProvider.visibility = GONE
            currentModelPricing.visibility = GONE
        }
    }
}