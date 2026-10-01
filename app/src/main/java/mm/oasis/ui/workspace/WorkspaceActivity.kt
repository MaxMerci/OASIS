package mm.oasis.ui.workspace

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import mm.oasis.Oasis
import mm.oasis.R
import mm.oasis.remote.AgentFiles
import mm.oasis.ui.objects.DialogField
import mm.oasis.ui.objects.FieldType
import mm.oasis.ui.objects.ModalDialogBuilder

class WorkspaceActivity : AppCompatActivity() {
    private enum class Tab { AGENT, SKILLS, WORKSPACE }

    private lateinit var tabAgent: TextView
    private lateinit var tabSkills: TextView
    private lateinit var tabWorkspace: TextView
    private lateinit var workspace: WorkspacePanel
    private lateinit var agentPanel: View
    private lateinit var skillsPanel: View
    private lateinit var skillEditor: View

    private lateinit var agentInput: EditText
    private lateinit var skillsEmpty: TextView
    private lateinit var skillName: EditText
    private lateinit var skillContent: EditText
    private lateinit var skillDelete: Button
    private lateinit var skillSave: Button

    private val skillsAdapter = SkillsAdapter { openEditor(it.name, it.content, readOnly = it.builtin) }

    private var tab = Tab.AGENT
    private var savedAgent = ""
    private var editing: String? = null
    private var editorOpen = false

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importSkill(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Oasis.init(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_workspace)
        applyWindowInsets()

        tabAgent = findViewById(R.id.tabAgent)
        tabSkills = findViewById(R.id.tabSkills)
        tabWorkspace = findViewById(R.id.tabWorkspace)
        workspace = WorkspacePanel(findViewById(R.id.workspacePanel))
        agentPanel = findViewById(R.id.agentPanel)
        skillsPanel = findViewById(R.id.skillsPanel)
        skillEditor = findViewById(R.id.skillEditor)
        agentInput = findViewById(R.id.agentInput)
        skillsEmpty = findViewById(R.id.skillsEmpty)
        skillName = findViewById(R.id.skillName)
        skillContent = findViewById(R.id.skillContent)
        skillDelete = findViewById(R.id.skillDelete)
        skillSave = findViewById(R.id.skillSave)

        findViewById<RecyclerView>(R.id.skillsList).adapter = skillsAdapter

        savedAgent = AgentFiles.readAgent()
        agentInput.setText(savedAgent)

        tabAgent.setOnClickListener { selectTab(Tab.AGENT) }
        tabSkills.setOnClickListener { selectTab(Tab.SKILLS) }
        tabWorkspace.setOnClickListener { selectTab(Tab.WORKSPACE) }

        findViewById<Button>(R.id.agentSave).setOnClickListener { saveAgent() }
        findViewById<Button>(R.id.agentReset).setOnClickListener { resetAgent() }

        findViewById<Button>(R.id.skillNew).setOnClickListener { openEditor(null, "") }
        findViewById<Button>(R.id.skillImport).setOnClickListener { importLauncher.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.skillCancel).setOnClickListener { closeEditor() }
        skillSave.setOnClickListener { saveSkill() }
        skillDelete.setOnClickListener { deleteSkill() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })

        refreshSkills()
        selectTab(Tab.AGENT)
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun selectTab(newTab: Tab) {
        tab = newTab
        tabAgent.setBackgroundResource(if (tab == Tab.AGENT) R.drawable.ic_bg_g_r else android.R.color.transparent)
        tabSkills.setBackgroundResource(if (tab == Tab.SKILLS) R.drawable.ic_bg_g_r else android.R.color.transparent)
        tabWorkspace.setBackgroundResource(if (tab == Tab.WORKSPACE) R.drawable.ic_bg_g_r else android.R.color.transparent)
        workspace.view.visibility = if (tab == Tab.WORKSPACE) View.VISIBLE else View.GONE
        if (tab == Tab.WORKSPACE) workspace.refresh()
        agentPanel.visibility = if (tab == Tab.AGENT) View.VISIBLE else View.GONE
        skillsPanel.visibility = if (tab == Tab.SKILLS && !editorOpen) View.VISIBLE else View.GONE
        skillEditor.visibility = if (tab == Tab.SKILLS && editorOpen) View.VISIBLE else View.GONE
    }

    private fun onBack() {
        when {
            tab == Tab.SKILLS && editorOpen -> closeEditor()
            agentInput.text.toString() != savedAgent -> confirm("UNSAVED AGENT.md", "Discard changes?") { finish() }
            else -> finish()
        }
    }

    /* AGENT.md */

    private fun saveAgent() {
        savedAgent = agentInput.text.toString()
        AgentFiles.writeAgent(savedAgent)
        toast("AGENT.md SAVED")
    }

    private fun resetAgent() {
        confirm("RESET AGENT.md", "Restore the default text?") {
            savedAgent = AgentFiles.resetAgent()
            agentInput.setText(savedAgent)
            toast("AGENT.md RESET")
        }
    }

    /* SKILLS */

    private fun refreshSkills() {
        val skills = AgentFiles.skills()
        skillsAdapter.submit(skills)
        skillsEmpty.visibility = if (skills.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditor(name: String?, content: String, suggestedName: String? = null, readOnly: Boolean = false) {
        editing = name
        editorOpen = true
        skillName.setText(name ?: suggestedName.orEmpty())
        skillContent.setText(content)
        listOf(skillName, skillContent).forEach { it.isFocusable = !readOnly; it.isFocusableInTouchMode = !readOnly }
        skillSave.visibility = if (readOnly) View.GONE else View.VISIBLE
        skillDelete.visibility = if (name != null && !readOnly) View.VISIBLE else View.GONE
        selectTab(Tab.SKILLS)
    }

    private fun closeEditor() {
        editorOpen = false
        editing = null
        refreshSkills()
        selectTab(Tab.SKILLS)
    }

    private fun saveSkill() {
        val name = AgentFiles.normalizeName(skillName.text.toString())
        if (name.isEmpty()) {
            toast("ENTER SKILL NAME")
            return
        }
        if (AgentFiles.isBuiltin(name)) {
            toast("$name IS A BUILT-IN SKILL")
            return
        }
        val write = {
            AgentFiles.writeSkill(name, skillContent.text.toString(), editing)
            toast("SKILL $name SAVED")
            closeEditor()
        }
        // перезапись чужого скила только после подтверждения
        if (name != editing?.let(AgentFiles::normalizeName) && AgentFiles.skillExists(name)) {
            confirm("SKILL EXISTS", "Overwrite $name?", write)
        } else {
            write()
        }
    }

    private fun deleteSkill() {
        val name = editing ?: return
        confirm("DELETE SKILL", "Delete $name?") {
            AgentFiles.deleteSkill(name)
            closeEditor()
        }
    }

    private fun importSkill(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw IllegalStateException("CAN'T READ FILE")
            val fileName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            // SKILL.md из чужих наборов называется одинаково, поэтому имя из frontmatter важнее имени файла
            val name = AgentFiles.frontmatterName(content)
                ?: fileName?.substringBeforeLast('.')?.takeUnless { it.equals("skill", true) }
            openEditor(null, content, name?.let(AgentFiles::normalizeName))
        } catch (e: Exception) {
            toast(e.message ?: e.toString())
        }
    }

    private fun confirm(title: String, text: String, onOk: () -> Unit) {
        ModalDialogBuilder(this)
            .setTitle(title)
            .addField(DialogField("", text, FieldType.INFO))
            .onOk { onOk() }
            .show()
    }

    private fun toast(text: String) {
        Snackbar.make(findViewById(R.id.root), text, Snackbar.LENGTH_SHORT).show()
    }
}
