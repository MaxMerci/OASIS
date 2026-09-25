package mm.oasis.ui.agent

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import mm.oasis.remote.AgentFiles

class SkillsAdapter(
    private val onClick: (AgentFiles.Skill) -> Unit
) : RecyclerView.Adapter<SkillsAdapter.ViewHolder>() {
    private var items: List<AgentFiles.Skill> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(skills: List<AgentFiles.Skill>) {
        items = skills
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_skill, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val skill = items[position]
        holder.name.text = skill.name
        holder.description.text = skill.description
        holder.description.visibility = if (skill.description.isBlank()) View.GONE else View.VISIBLE
        holder.itemView.setOnClickListener { onClick(skill) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.skillName)
        val description: TextView = view.findViewById(R.id.skillDescription)
    }
}
