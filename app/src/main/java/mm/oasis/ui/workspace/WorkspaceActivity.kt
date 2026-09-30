package mm.oasis.ui.workspace

import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import mm.oasis.Oasis
import mm.oasis.R
import mm.oasis.remote.Workspace
import mm.oasis.ui.objects.DialogField
import mm.oasis.ui.objects.FieldType
import mm.oasis.ui.objects.ModalDialogBuilder
import mm.oasis.ui.objects.WorkspaceFiles
import java.io.File
import java.nio.file.Files

/**
 * Мини-проводник: открывается в workspace, но позволяет подняться до корня хранилища приложения (не выше)
 */
class WorkspaceActivity : AppCompatActivity() {
    private lateinit var root: File
    private lateinit var current: File

    private lateinit var upButton: TextView
    private lateinit var pathBar: LinearLayout
    private lateinit var pathScroll: HorizontalScrollView
    private lateinit var filesList: RecyclerView
    private lateinit var filesEmpty: TextView

    private val filesAdapter = FilesAdapter(
        onClick = { if (it.isDirectory) navigate(it) else WorkspaceFiles.open(filesList, it) },
        onDelete = ::confirmDelete,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        Oasis.init(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_workspace)
        applyWindowInsets()

        upButton = findViewById(R.id.upButton)
        pathBar = findViewById(R.id.pathBar)
        pathScroll = findViewById(R.id.pathScroll)
        filesList = findViewById(R.id.filesList)
        filesEmpty = findViewById(R.id.filesEmpty)
        filesList.adapter = filesAdapter

        root = File(applicationInfo.dataDir).canonicalFile
        current = savedInstanceState?.getString(KEY_DIR)?.let(::File)?.takeIf { inside(it) && it.isDirectory }
            ?: Workspace.dir.canonicalFile

        upButton.setOnClickListener { goUp() }

        navigate(current)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DIR, current.path)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun inside(file: File): Boolean {
        val path = file.canonicalFile.path
        return path == root.path || path.startsWith(root.path + File.separator)
    }

    private fun goUp() {
        if (current == root) return
        current.parentFile?.takeIf { inside(it) }?.let(::navigate)
    }

    private fun navigate(dir: File) {
        val target = dir.canonicalFile
        if (!inside(target) || !target.isDirectory) {
            toast("CAN'T OPEN ${dir.name}")
            return
        }
        current = target
        upButton.alpha = if (current == root) 0.3f else 1f
        buildPath()
        refresh()
        filesList.scrollToPosition(0)
    }

    private fun refresh() {
        if (!current.isDirectory) {
            var dir: File? = current
            while (dir != null && dir != root && !dir.isDirectory) dir = dir.parentFile
            navigate(dir?.takeIf { inside(it) } ?: root)
            return
        }
        val files = current.listFiles().orEmpty()
            .filter { inside(it) }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        filesAdapter.submit(files)
        filesEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildPath() {
        pathBar.removeAllViews()
        val chain = generateSequence(current) { it.parentFile?.takeIf { p -> inside(p) } }
            .takeWhile { inside(it) }
            .toList()
            .asReversed()

        chain.forEachIndexed { i, dir ->
            if (i > 0) pathBar.addView(segment("/", null))
            val name = if (dir == root) "OASIS" else dir.name
            pathBar.addView(segment(name, dir.takeIf { it != current }))
        }
        pathScroll.post { pathScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun segment(text: String, target: File?) = TextView(this).apply {
        this.text = text
        textSize = 13f
        val pad = (6 * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
        )
        setTextColor(ContextCompat.getColor(context, if (target == null && text != "/") R.color.text else R.color.hint))
        if (target != null) {
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { navigate(target) }
        }
    }

    private fun confirmDelete(file: File) {
        val kind = if (file.isDirectory) "FOLDER" else "FILE"
        ModalDialogBuilder(this)
            .setTitle("DELETE $kind")
            .addField(DialogField("", "Delete ${file.name}? This can't be undone.", FieldType.INFO))
            .onOk {
                if (!delete(file)) toast("CAN'T DELETE ${file.name}")
                refresh()
            }
            .show()
    }

    private fun delete(file: File): Boolean {
        if (file.isDirectory && !Files.isSymbolicLink(file.toPath())) {
            file.listFiles()?.forEach { delete(it) }
        }
        return file.delete()
    }

    private fun toast(text: String) {
        Snackbar.make(findViewById(R.id.root), text, Snackbar.LENGTH_SHORT).show()
    }

    private companion object {
        const val KEY_DIR = "dir"
    }
}
