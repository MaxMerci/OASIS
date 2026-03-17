package mm.oasis.ui.models

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import mm.oasis.R
import mm.oasis.remote.ApiClient
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.LLMRaw
import kotlinx.coroutines.launch
import mm.oasis.repository.RepositoryState
import mm.oasis.serialization.dto.LLMResponse
import mm.oasis.serialization.storage.ProfileData


class ModelsFragment : Fragment() {
    private lateinit var modelsList: ListView
    private lateinit var emptyView: TextView
    private lateinit var searchInput: EditText
    private lateinit var reload: SwipeRefreshLayout

    private lateinit var currentModelId: TextView
    private lateinit var currentModelProvider: TextView
    private lateinit var currentModelPricing: TextView

    private val modelsAdapter = ModelsAdapter()

    private var lastProfilesState: RepositoryState<ProfileData>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_models, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyView = view.findViewById(R.id.emptyView)
        searchInput = view.findViewById(R.id.searchInput)
        currentModelId = view.findViewById(R.id.currentModelId)
        currentModelProvider = view.findViewById(R.id.currentModelProvider)
        currentModelPricing = view.findViewById(R.id.currentModelPricing)
        reload = view.findViewById(R.id.reload)

        modelsList = view.findViewById(R.id.modelsView)
        modelsList.adapter = modelsAdapter
        modelsList.emptyView = emptyView

        lifecycleScope.launch {
            ProfileRepository.state.collect { state ->
                requireActivity().runOnUiThread {
                    val currentProfile = ProfileRepository.currentProfile
                    val lastProfile =
                        lastProfilesState?.items?.getOrNull(lastProfilesState?.currentIndex ?: 0)

                    val apiKeyChanged = lastProfile?.apiKey != currentProfile?.apiKey
                    val endPointChanged = lastProfile?.endPoint != currentProfile?.endPoint

                    if (apiKeyChanged || endPointChanged) {
                        setCurrent(null)
                        loadModels()
                        setCurrent(ProfileRepository.currentProfile?.model)
                    }

                    lastProfilesState = state
                }
            }
        }

        modelsList.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            requireActivity().runOnUiThread {
                setCurrent(modelsAdapter.getItem(position))
            }
        }

        reload.setProgressBackgroundColorSchemeResource(R.color.bg)
        reload.setColorSchemeResources(R.color.text)
        reload.setOnRefreshListener {
            loadModels()
            reload.isRefreshing = false
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                modelsAdapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadModels()
        setCurrent(ProfileRepository.currentProfile?.model)
    }

    fun loadModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                modelsAdapter.setModels(LLMResponse(emptyList()))
                val models = ApiClient.fetchModels()
                modelsAdapter.setModels(models)
            } catch (e: Exception) {
                emptyView.text = e.toString()
                emptyView.visibility = VISIBLE
                modelsList.visibility = GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun setCurrent(model: LLMRaw?) {
        if (model != null) {
            ProfileRepository.updateItem(ProfileRepository.currentIndex) { currentProfile ->
                currentProfile.copy(model = model)
            }
            currentModelId.text = model.id
            currentModelProvider.visibility = VISIBLE
            currentModelProvider.text = model.ownedBy ?: "PROVIDER NOT SPECIFIED"
            currentModelPricing.visibility = VISIBLE
            currentModelPricing.text = if (model.pricing?.prompt != null && model.pricing.completion != null) {
                "PROMPT: $${model.pricing.prompt}/M | COMPLETION: $${model.pricing.completion}/M"
            } else "PRICE NOT SPECIFIED"
        } else {
            currentModelId.text = "NOT SELECTED"
            currentModelProvider.visibility = GONE
            currentModelPricing.visibility = GONE
        }
    }
}
