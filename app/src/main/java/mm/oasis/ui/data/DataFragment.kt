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
import mm.oasis.serialization.storage.ProfileData
import mm.oasis.ui.modal.DialogButton
import mm.oasis.ui.modal.DialogField
import mm.oasis.ui.modal.FieldType
import mm.oasis.ui.modal.ModalDialogBuilder
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
    private val chatsAdapter = ChatsAdapter(::onChatClick)

    private var startY = 0f
    private var startX = 0f
    private var isPulling = false
    private val pullThreshold = 350f

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

        setupPullToAdd()

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    ChatRepository.remove(position)
                }
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.5f
            
            override fun isItemViewSwipeEnabled(): Boolean {
                return !isPulling
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(chatsView)

        lifecycleScope.launch {
            ChatRepository.state.collect {
                chatsAdapter.notifyDataSetChanged()
            }
        }

        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPullToAdd() {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop

        chatsView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    startX = event.x
                    isPulling = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - startY
                    val deltaX = event.x - startX

                    if (!isPulling && deltaY > touchSlop && deltaY > abs(deltaX) && !chatsView.canScrollVertically(-1)) {
                        isPulling = true
                    }

                    if (isPulling) {
                        val translation = (deltaY * 0.4f).coerceAtLeast(0f)
                        chatsView.translationY = translation
                        addChatHint.alpha = (translation / (pullThreshold * 0.4f)).coerceAtMost(1f)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isPulling) {
                        if (chatsView.translationY > pullThreshold * 0.4f) {
                            ChatRepository.addAt(0, ChatRepository.emptyChat())
                        }
                        chatsView.animate().translationY(0f).setDuration(250).start()
                        addChatHint.animate().alpha(0f).setDuration(250).start()
                        isPulling = false
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun onChatClick(pos: Int) {
        ChatRepository.updateIndex(pos)
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

    private fun onLongProfileClick(pos: Int) {
        val profile = ProfileRepository.items[pos]
        ModalDialogBuilder(requireContext())
            .addField(DialogField("endPoint", "ENDPOINT", FieldType.URL, true, profile.endPoint.ifEmpty { "https://api.example.ai/v1" }))
            .addField(DialogField("apiKey", "API KEY", FieldType.TEXT, true, profile.apiKey.ifEmpty { "sk-..." }))
            .addButton(DialogButton("DELETE") { ProfileRepository.remove(pos) })
            .onOk { values ->
                profile.endPoint = values["endPoint"] ?: profile.endPoint
                profile.apiKey = values["apiKey"] ?: profile.apiKey
            }
            .show()
    }
}