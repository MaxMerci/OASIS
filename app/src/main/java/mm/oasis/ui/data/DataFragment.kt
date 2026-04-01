package mm.oasis.ui.data

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import mm.oasis.R
import mm.oasis.repository.ChatRepository
import mm.oasis.repository.ProfileRepository
import mm.oasis.repository.Repository
import mm.oasis.serialization.storage.ProfileData
import mm.oasis.ui.objects.DialogButton
import mm.oasis.ui.objects.DialogField
import mm.oasis.ui.objects.FieldType
import mm.oasis.ui.objects.ModalDialogBuilder
import kotlin.math.abs

class DataFragment : Fragment() {
    /* PROFILES */
    private val profilesAdapter = ProfilesAdapter(
        ::onProfileClick,
        ::onLongProfileClick,
    )
    private lateinit var profilesView: RecyclerView

    /* CHATS */
    private lateinit var chatsView: RecyclerView
    private lateinit var addChatHint: TextView
    private val chatsAdapter = ChatsAdapter(
        ::onChatClick,
        ::onLongChatClick
    )

    /* ADD CHAT MECHANICS */
    private var startY = 0f
    private var isDragging = false
    private var isAtTop = true
    private var currentOffset = 0f
    private val maxPull = 150f
    private val triggerThreshold = 100f

    @SuppressLint("NotifyDataSetChanged", "ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_data, container, false)
        
        profilesView = view.findViewById(R.id.profilesView)
        profilesView.adapter = profilesAdapter

        lifecycleScope.launch {
            ProfileRepository.state.collect {
                profilesAdapter.notifyDataSetChanged()
            }
        }

        addChatHint = view.findViewById(R.id.addChatHint)
        chatsView = view.findViewById(R.id.chatsList)
        chatsView.adapter = chatsAdapter

        lifecycleScope.launch {
            ChatRepository.state.collect {
                chatsAdapter.notifyDataSetChanged()
            }
        }

        chatsView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    isDragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY

                    isAtTop = !chatsView.canScrollVertically(-1)

                    if (dy > 0 && isAtTop) {
                        isDragging = true

                        val offset = (dy / 2).coerceAtMost(maxPull)
                        currentOffset = offset

                        chatsView.translationY = offset

                        val alpha = (offset / triggerThreshold).coerceIn(0f, 1f)
                        addChatHint.alpha = alpha

                        return@setOnTouchListener true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        if (currentOffset > triggerThreshold) {
                            ChatRepository.addAt(0,ChatRepository.emptyChat(), true)
                            chatsAdapter.notifyDataSetChanged()
                        }

                        chatsView.animate()
                            .translationY(0f)
                            .setDuration(200)
                            .start()

                        addChatHint.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .start()

                        currentOffset = 0f

                        isDragging = false
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        return view
    }

    private fun onChatClick(pos: Int) {
        ChatRepository.updateIndex(pos)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onLongChatClick(pos: Int) {
        val chat = ChatRepository.items[pos]
        ModalDialogBuilder(requireContext())
            .addField(DialogField("name", "NAME", FieldType.TEXT, true, chat.name))
            .addButton(DialogButton(
                "DELETE",
                onClick = {
                    ChatsAdapter.animatedItems.remove(ChatRepository.items[pos]) // ООП никого не щадит
                    ChatRepository.removeAt(pos, true)
                    chatsAdapter.notifyDataSetChanged()
                }
            ))
            .onOk { values ->
                values["name"]?.let {
                    chat.name = it
                    ChatRepository.save()
                    chatsAdapter.notifyDataSetChanged()
                }
            }
            .show()
    }

    private fun onProfileClick(pos: Int) {
        if (pos == -1) {
            ModalDialogBuilder(requireContext())
                .addField(DialogField("endPoint", "ENDPOINT", FieldType.URL, true, "https://api.example.ai/v1"))
                .addField(DialogField("apiKey", "API KEY", FieldType.TEXT, true))
                .onOk { values ->
                    val newProfile = ProfileData(
                        endPoint = values["endPoint"] ?: "",
                        apiKey = values["apiKey"] ?: ""
                    )
                    ProfileRepository.addAt(0, newProfile)
                }
                .show()
        } else {
            ProfileRepository.updateIndex(pos)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onLongProfileClick(pos: Int) {
        val profile = ProfileRepository.items[pos]
        ModalDialogBuilder(requireContext())
            .addField(DialogField("endPoint", "ENDPOINT", FieldType.URL, true, profile.endPoint.ifEmpty { "https://api.example.ai/v1" }))
            .addField(DialogField("apiKey", "API KEY", FieldType.TEXT, true, profile.apiKey.ifEmpty { "sk-..." }))
            .addButton(
                DialogButton("DELETE") {
                    ProfilesAdapter.animatedItems.remove(ProfileRepository.items[pos])
                    ProfileRepository.removeAt(pos)
                    profilesAdapter.notifyDataSetChanged()
                }
            )
            .onOk { values ->
                profile.endPoint = values["endPoint"] ?: profile.endPoint
                profile.apiKey = values["apiKey"] ?: profile.apiKey
                ProfileRepository.save()
                profilesAdapter.notifyDataSetChanged()
            }
            .show()
    }
}