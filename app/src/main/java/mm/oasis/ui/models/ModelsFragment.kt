package mm.oasis.ui.models

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import mm.oasis.R
import mm.oasis.remote.ApiClient
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.dto.LLMRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mm.oasis.repository.RepositoryState
import mm.oasis.serialization.dto.LLMResponse
import mm.oasis.serialization.storage.ProfileData
import mm.oasis.ui.agent.AgentFilesActivity
import mm.oasis.ui.objects.DialogField
import mm.oasis.ui.objects.FieldType
import mm.oasis.ui.objects.ModalDialogBuilder
import java.text.DateFormat
import java.util.Date


class ModelsFragment : Fragment() {
    private lateinit var modelsList: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var searchInput: EditText
    private lateinit var reload: SwipeRefreshLayout

    private lateinit var currentModelId: TextView

    private val modelsAdapter = ModelsAdapter(
        onModelClick = { model -> setCurrent(model) },
        onModelOpen = { model -> showModelInfo(model) },
    )

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
        reload = view.findViewById(R.id.reload)

        currentModelId = view.findViewById(R.id.currentModelId)

        view.findViewById<View>(R.id.agentFilesButton).setOnClickListener {
            startActivity(Intent(requireContext(), AgentFilesActivity::class.java))
        }

        modelsList = view.findViewById(R.id.modelsView)
        modelsList.layoutManager = LinearLayoutManager(requireContext())
        modelsList.adapter = modelsAdapter

        modelsList.itemAnimator = null

        viewLifecycleOwner.lifecycleScope.launch {
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
            modelsAdapter.setModels(LLMResponse(emptyList()))
            if (ProfileRepository.currentProfile == null) {
                emptyView.text = "PROFILE NOT SELECTED"
                emptyView.visibility = VISIBLE
                modelsList.visibility = GONE
                return@launch
            }
            try {
                val models = ApiClient.fetchModels()
                modelsAdapter.setModels(models)
                emptyView.text = "NO MODELS"
                emptyView.visibility = if (models.data.isEmpty()) VISIBLE else GONE
                modelsList.visibility = if (models.data.isEmpty()) GONE else VISIBLE
            } catch (e: CancellationException) {
                throw e
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
        } else {
            currentModelId.text = "NOT SELECTED"
        }
    }

    private fun showModelInfo(model: LLMRaw) {
        val dialog = ModalDialogBuilder(requireContext())
            .setTitle(model.id)
            .setOkText("SELECT")
            .setCancelText("CLOSE")
            .onOk { setCurrent(model) }

        model.avatarUrl?.let {
            dialog.addField(DialogField("avatarUrl", "AVATAR", FieldType.INFO, defaultValue = it))
        }

        if (model.extra.isEmpty()) {
            dialog.addField(DialogField("", "NO DATA", FieldType.INFO))
        } else {
            addInfoFields(dialog, model.extra, 0)
        }

        dialog.show()
    }

    private fun addInfoFields(dialog: ModalDialogBuilder, map: Map<*, *>, depth: Int) {
        map.forEach { (key, value) ->
            val title = key.toString().replace('_', ' ').uppercase()

            when {
                value is Map<*, *> && value.isNotEmpty() -> {
                    dialog.addField(DialogField("", title, FieldType.INFO, depth = depth))
                    addInfoFields(dialog, value, depth + 1)
                }
                value is List<*> && value.any { it is Map<*, *> || it is List<*> } -> {
                    dialog.addField(DialogField("", title, FieldType.INFO, depth = depth))
                    addInfoFields(dialog, value.withIndex().associate { "[${it.index}]" to it.value }, depth + 1)
                }
                else -> {
                    dialog.addField(DialogField("", title, FieldType.INFO, defaultValue = formatValue(key.toString(), value), depth = depth))
                }
            }
        }
    }

    private fun formatValue(key: String, value: Any?): String = when (value) {
        null -> "—"
        is Map<*, *> -> "—"
        is List<*> -> if (value.isEmpty()) "—" else value.joinToString(", ") { formatValue("", it) }
        is Double -> {
            val asLong = value.toLong()
            // unix-время в секундах (created, created_at и т.п.)
            if (key.startsWith("created") && asLong in 1_000_000_000L..9_999_999_999L) {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(asLong * 1000))
            } else if (value == asLong.toDouble()) {
                asLong.toString()
            } else {
                value.toString()
            }
        }
        else -> value.toString()
    }
}
