package mm.oasis.ui.data

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.repository.ProfileRepository
import mm.oasis.ui.modal.DialogButton
import mm.oasis.ui.modal.DialogField
import mm.oasis.ui.modal.FieldType
import mm.oasis.ui.modal.ModalDialogBuilder

class ProfilesAdapter(
    private val onProfileClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<ProfilesAdapter.ProfileViewHolder>() {
    override fun getItemCount() = ProfileRepository.items.size

    override fun getItemViewType(position: Int): Int {
        return if (ProfileRepository.currentIndex == position) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val layout = if (viewType == 1) {
            R.layout.item_profile_c
        } else {
            R.layout.item_profile
        }

        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val endPoint: TextView = itemView.findViewById(R.id.endPoint)
        private val currentModel: TextView = itemView.findViewById(R.id.currentModel)

        @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
        fun bind(position: Int) {
            val profile = ProfileRepository.items[position]

            endPoint.text = profile.endPoint
            currentModel.text = profile.model?.id ?: "MODEL NOT SPECIFIED"

            itemView.setOnClickListener {
                onProfileClick(position)
                notifyDataSetChanged()
            }

            itemView.setOnLongClickListener {
                onLongClick(position)
                notifyDataSetChanged()
                true
            }
        }
    }
}