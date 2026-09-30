package mm.oasis.ui.workspace

import android.annotation.SuppressLint
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mm.oasis.R
import java.io.File
import java.text.DateFormat
import java.util.Date

class FilesAdapter(
    private val onClick: (File) -> Unit,
    private val onDelete: (File) -> Unit,
) : RecyclerView.Adapter<FilesAdapter.ViewHolder>() {
    private var items: List<File> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(files: List<File>) {
        items = files
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        val context = holder.itemView.context
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.lastModified()))

        holder.name.text = if (file.isDirectory) "${file.name}/" else file.name
        holder.info.text = if (file.isDirectory) {
            "${file.list()?.size ?: 0} ITEMS · $date"
        } else {
            "${Formatter.formatShortFileSize(context, file.length())} · $date"
        }
        holder.itemView.setOnClickListener { onClick(file) }
        holder.itemView.setOnLongClickListener { onDelete(file); true }
        holder.delete.setOnClickListener { onDelete(file) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.fileName)
        val info: TextView = view.findViewById(R.id.fileInfo)
        val delete: View = view.findViewById(R.id.fileDelete)
    }
}
