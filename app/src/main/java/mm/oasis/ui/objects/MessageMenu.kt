package mm.oasis.ui.objects

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import mm.oasis.R
import androidx.core.graphics.drawable.toDrawable


object MessageMenu {
    data class Item(val text: String, val onClick: () -> Unit)

    fun show(anchor: View, x: Int, y: Int, items: List<Item>) {
        if (items.isEmpty()) return
        val inflater = LayoutInflater.from(anchor.context)
        val root = inflater.inflate(R.layout.popup_message_menu, null) as LinearLayout

        val popup = PopupWindow(
            root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            elevation = 8 * anchor.resources.displayMetrics.density
        }

        items.forEach { item ->
            val view = inflater.inflate(R.layout.item_menu, root, false) as TextView
            view.text = item.text
            view.setOnClickListener {
                popup.dismiss()
                item.onClick()
            }
            root.addView(view)
        }

        root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val metrics = anchor.resources.displayMetrics
        val margin = (16 * metrics.density).toInt()
        val px = x.coerceAtMost(metrics.widthPixels - root.measuredWidth - margin).coerceAtLeast(margin)
        val py = if (y + root.measuredHeight + margin > metrics.heightPixels) y - root.measuredHeight else y

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, px, py)
    }
}
