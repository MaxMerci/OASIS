package mm.oasis.ui.objects

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import mm.oasis.R

enum class FieldType {
    TEXT,
    NUMBER,
    URL,
    HEADER,
    INFO, // только чтение: title - ключ, defaultValue - значение (null = заголовок секции)
}

data class DialogField(
    val key: String,
    val title: String,
    val type: FieldType,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val depth: Int = 0
)

data class DialogButton(
    val text: String,
    val layoutRes: Int = R.layout.dialog_button,
    val onClick: (() -> Unit)? = null
)

class ModalDialogBuilder(private val context: Context) {

    private var title: String? = null
    private val fields = mutableListOf<DialogField>()
    private val buttons = mutableListOf<DialogButton>()

    private var onOk: ((Map<String, String?>) -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    private var okText = "OK"
    private var cancelText = "CANCEL"

    fun setTitle(title: String): ModalDialogBuilder {
        this.title = title
        return this
    }

    fun addField(field: DialogField): ModalDialogBuilder {
        fields.add(field)
        return this
    }

    fun addButton(button: DialogButton): ModalDialogBuilder {
        buttons.add(button)
        return this
    }

    fun setOkText(text: String): ModalDialogBuilder {
        okText = text
        return this
    }

    fun setCancelText(text: String): ModalDialogBuilder {
        cancelText = text
        return this
    }

    fun onOk(listener: (Map<String, String?>) -> Unit): ModalDialogBuilder {
        onOk = listener
        return this
    }

    fun onCancel(listener: () -> Unit): ModalDialogBuilder {
        onCancel = listener
        return this
    }

    @SuppressLint("SetTextI18n", "MissingInflatedId")
    fun show() {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_constructor, null)

        title?.let {
            val titleView = view.findViewById<TextView>(R.id.dialogTitle)
            titleView.text = it
            titleView.visibility = View.VISIBLE
        } ?: run {
            view.findViewById<TextView>(R.id.dialogTitle).visibility = View.GONE
        }

        val fieldsContainer = view.findViewById<LinearLayout>(R.id.fieldsContainer)
        val customButtonsContainer = view.findViewById<LinearLayout>(R.id.customButtonsContainer)
        val defaultButtonsContainer = view.findViewById<LinearLayout>(R.id.defaultButtonsContainer)

        val fieldViews = mutableMapOf<String, EditText>()

        fields.forEach { field ->
            if (field.type == FieldType.INFO) {
                fieldsContainer.addView(inflateInfo(fieldsContainer, field))
                return@forEach
            }

            val fieldView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_field, fieldsContainer, false)

            val title = fieldView.findViewById<TextView>(R.id.fieldTitle)
            val input = fieldView.findViewById<EditText>(R.id.fieldInput)

            title.text = field.title

            when(field.type){
                FieldType.TEXT -> input.inputType = InputType.TYPE_CLASS_TEXT
                FieldType.NUMBER -> input.inputType = InputType.TYPE_CLASS_NUMBER
                FieldType.URL -> input.inputType = InputType.TYPE_TEXT_VARIATION_URI
                FieldType.INFO -> {}
                FieldType.HEADER -> {
                    input.inputType = InputType.TYPE_CLASS_TEXT
                    input.visibility = View.GONE
                }
            }

            field.defaultValue?.let {
                input.setText(it)
            }

            fieldsContainer.addView(fieldView)
            fieldViews[field.key] = input
        }

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        buttons.forEach { btn ->
            val b = LayoutInflater.from(context)
                .inflate(btn.layoutRes, customButtonsContainer, false) as Button

            b.text = btn.text
            b.setOnClickListener {
                btn.onClick?.invoke()
                dialog.dismiss()
            }

            customButtonsContainer.addView(b)
        }

        val cancelButton = LayoutInflater.from(context)
            .inflate(R.layout.dialog_button, defaultButtonsContainer, false) as Button
        cancelButton.text = cancelText
        cancelButton.setOnClickListener {
            onCancel?.invoke()
            dialog.dismiss()
        }
        defaultButtonsContainer.addView(cancelButton)

        val okButton = LayoutInflater.from(context)
            .inflate(R.layout.dialog_button_l, defaultButtonsContainer, false) as Button
        okButton.text = okText

        fun validate(fieldViews: Map<String, EditText>): Boolean {
            for (field in fields) {
                val value = fieldViews[field.key]?.text?.toString()?.trim() ?: ""

                if (field.required && value.isEmpty()) {
                    return false
                }

                when (field.type) {
                    FieldType.URL -> {
                        if (value.isNotEmpty() && !Patterns.WEB_URL.matcher(value).matches()) {
                            return false
                        }
                    }
                    else -> {}
                }
            }
            return true
        }

        okButton.isEnabled = validate(fieldViews)

        fieldViews.values.forEach { editText ->
            editText.addTextChangedListener {
                okButton.isEnabled = validate(fieldViews)
            }
        }

        okButton.setOnClickListener {
            val result = mutableMapOf<String, String?>()

            fields.filter { it.type != FieldType.INFO }.forEach { field ->
                result[field.key] = fieldViews[field.key]?.text?.toString()
            }

            onOk?.invoke(result)
            dialog.dismiss()
        }

        defaultButtonsContainer.addView(okButton)

        dialog.show()
    }

    private fun inflateInfo(parent: LinearLayout, field: DialogField): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_info, parent, false)

        val title = view.findViewById<TextView>(R.id.infoTitle)
        val value = view.findViewById<TextView>(R.id.infoValue)

        title.text = field.title

        if (field.defaultValue == null) {
            value.visibility = View.GONE
            title.setTextColor(context.getColor(R.color.text))
        } else {
            value.text = field.defaultValue
        }

        val indent = (12 * field.depth * context.resources.displayMetrics.density).toInt()
        view.setPadding(view.paddingLeft + indent, view.paddingTop, view.paddingRight, view.paddingBottom)

        return view
    }
}
