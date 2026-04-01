package mm.oasis.ui.data

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.repository.ProfileRepository
import mm.oasis.serialization.storage.ChatData
import mm.oasis.serialization.storage.ProfileData

class ProfilesAdapter(
    private val onProfileClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<ProfilesAdapter.ProfileViewHolder>() {

    companion object {
        val animatedItems = mutableListOf<ProfileData>()

        const val TYPE_ADD = 2
        const val TYPE_SELECTED = 1
        const val TYPE_NORMAL = 0
    }

    override fun getItemCount() = ProfileRepository.items.size + 1

    override fun getItemViewType(position: Int): Int {
        if (position == 0) return TYPE_ADD

        val dataIndex = position - 1
        return if (ProfileRepository.currentIndex == dataIndex) TYPE_SELECTED else TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val layout = when (viewType) {
            TYPE_ADD -> R.layout.item_profile_add
            TYPE_SELECTED -> R.layout.item_profile_c
            else -> R.layout.item_profile
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = ProfileRepository.items[position]
        holder.bind(profile)
    }

    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val endPoint: TextView? = itemView.findViewById(R.id.endPoint)
        private val currentModel: TextView? = itemView.findViewById(R.id.currentModel)

        @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
        fun bind(profile: ProfileData) {
            val viewType = itemViewType

            if (viewType == TYPE_ADD) {
                itemView.setOnClickListener {
                    onProfileClick(-1)
                }
                itemView.setOnLongClickListener(null)
            } else {
                itemView.apply {
                    if (!animatedItems.contains(profile)) {
                        animatedItems.add(profile)
                        alpha = 0f
                        scaleY = 0f

                        animate()
                            .alpha(1f)
                            .scaleY(1f)
                            .setDuration(250)
                            .setStartDelay((bindingAdapterPosition * 100L))
                            .start()
                    }
                }

                val dataIndex = position - 1
                val profile = ProfileRepository.items[dataIndex]

                endPoint?.text = profile.endPoint
                currentModel?.text = profile.model?.id ?: "MODEL NOT SPECIFIED"

                itemView.setOnClickListener {
                    onProfileClick(dataIndex)
                    notifyDataSetChanged()
                }
                itemView.setOnLongClickListener {
                    onLongClick(dataIndex)
                    true
                }
            }
        }
    }
}