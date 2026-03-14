package mm.oasis.ui.data

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import mm.oasis.R
import mm.oasis.repository.ProfileRepository
import mm.oasis.ui.modal.DialogButton
import mm.oasis.ui.modal.DialogField
import mm.oasis.ui.modal.FieldType
import mm.oasis.ui.modal.ModalDialogBuilder

class DataFragment : Fragment() {
    private val profilesAdapter = ProfilesAdapter(ProfileRepository::updateIndex, ::onLongProfileClick)
    private lateinit var profilesView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_data, container, false)

        profilesView = view.findViewById(R.id.profilesView)
        profilesView.adapter = profilesAdapter

        @SuppressLint("NotifyDataSetChanged")
        lifecycleScope.launch {
            ProfileRepository.state.collect {
                profilesAdapter.notifyDataSetChanged()
            }
        }

        return view
    }

    fun onLongProfileClick(pos: Int) {
        val profile = ProfileRepository.items[pos]

        ModalDialogBuilder(requireContext())
            .addField(
                DialogField(
                    key = "endPoint",
                    title = "ENDPOINT",
                    type = FieldType.URL,
                    required = true,
                    defaultValue = profile.endPoint.ifEmpty { "https://api.example.ai/v1" }
                )
            )
            .addField(
                DialogField(
                    key = "apiKey",
                    title = "API KEY",
                    type = FieldType.TEXT,
                    defaultValue = profile.apiKey.ifEmpty { "sk-..." }
                )
            )
            .addButton(
                DialogButton("CANCEL")
            )
            .onOk { values ->
                profile.endPoint = values["endPoint"] ?: profile.endPoint
                profile.apiKey = values["apiKey"] ?: profile.apiKey
            }
            .show()
    }
}