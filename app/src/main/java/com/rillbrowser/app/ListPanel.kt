package com.rillbrowser.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/**
 * The slide-in side panel (menu, bookmarks, history, settings): a dimmed scrim over the page with a rounded
 * card on the right. Layout follows the wireframe: a "< Close" header, then rows. Sizes are multiples of
 * u = min(width / 16, height / 9) so it scales with the screen.
 */
class ListPanel(context: Context) : FrameLayout(context) {
    sealed class Item
    class Row(val label: String, val icon: IconView.Kind? = null, val dim: Boolean = false, val action: () -> Unit) : Item()
    class Duo(val left: Row, val right: Row) : Item()
    class Field(val text: String, val action: () -> Unit) : Item()

    private val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var u = 0f
    private var render: (() -> Unit)? = null

    init {
        setBackgroundColor(Theme.scrim)
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END))
    }

    /** Unit from this view's size if it has one yet, otherwise from the display (the panel starts GONE). */
    private fun computeUnit(w: Int, h: Int): Float {
        val dm = resources.displayMetrics
        val ww = if (w > 0) min(w, dm.widthPixels) else dm.widthPixels
        val hh = if (h > 0) min(h, dm.heightPixels) else dm.heightPixels
        return min(ww / 16f, hh / 9f)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val next = computeUnit(w, h)
        if (next != u) {
            u = next
            android.util.Log.i("Rill", "panel unit=$u view=${w}x$h")
            render?.invoke()
        }
    }

    private fun px(v: Float) = (v * u).toInt()

    /**
     * @param focusIndex which row to focus (counting selectable rows, a Duo counts once); -1 = the header.
     */
    fun show(
        title: String?,
        items: List<Item>,
        backLabel: String = "Close",
        focusIndex: Int = -1,
        onBack: () -> Unit,
    ) {
        render = { build(title, items, backLabel, focusIndex, onBack) }
        if (u == 0f) u = computeUnit(width, height)
        render?.invoke()
    }

    private fun build(title: String?, items: List<Item>, backLabel: String, focusIndex: Int, onBack: () -> Unit) {
        card.removeAllViews()
        val margin = px(0.2f)
        card.layoutParams = (card.layoutParams as LayoutParams).apply {
            width = px(6.2f)
            setMargins(0, margin, margin, margin)
        }
        card.setPadding(px(0.3f), px(0.25f), px(0.3f), px(0.25f))
        card.background = Theme.gradient(0xFF33343C.toInt(), 0xFF25262C.toInt(), px(0.5f).toFloat(), Theme.hairline, 2)

        val header = row(IconView.Kind.CHEVRON_LEFT, backLabel, false, onBack).also {
            card.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(0.7f)))
        }
        if (title != null) {
            card.addView(TextView(context).apply {
                text = title
                typeface = Theme.wordmark
                setTextColor(Theme.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.3f * u)
                letterSpacing = 0.06f
                setPadding(px(0.3f), px(0.2f), 0, px(0.08f))
            })
        }

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        card.addView(
            ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        var target: View? = null
        var selectable = 0
        for (item in items) {
            val v: View = when (item) {
                is Row -> row(item.icon, item.label, item.dim, item.action)
                is Duo -> LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (cell in listOf(item.left, item.right)) {
                        addView(row(cell.icon, cell.label, cell.dim, cell.action), LinearLayout.LayoutParams(0, px(0.6f), 1f))
                    }
                }
                is Field -> field(item)
            }
            list.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (item is Field) ViewGroup.LayoutParams.WRAP_CONTENT else px(0.6f)).apply {
                topMargin = px(if (item is Field) 0.08f else 0.02f)
                bottomMargin = px(if (item is Field) 0.12f else 0.02f)
            })
            if (selectable == focusIndex) target = if (item is Duo) (v as ViewGroup).getChildAt(0) else v
            selectable++
        }

        // keep D-pad movement inside the card: Up from the first row goes to the header and vice versa
        val firstRow = list.getChildAt(0)?.let { if (it.isFocusable) it else (it as? ViewGroup)?.getChildAt(0) }
        header.id = View.generateViewId()
        firstRow?.let {
            if (it.id == View.NO_ID) it.id = View.generateViewId()
            header.nextFocusDownId = it.id
            it.nextFocusUpId = header.id
        }
        (target ?: header).requestFocus()
    }

    /** One focusable row: icon slot (kept even when empty, so labels line up) + label. */
    private fun row(icon: IconView.Kind?, label: String, dim: Boolean, action: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = Theme.focusable(px(0.16f).toFloat(), maxOf(2, px(0.035f)))
            setPadding(px(0.25f), 0, px(0.2f), 0)
            val slot = px(0.42f)
            if (icon != null) addView(IconView(context, icon, if (dim) Theme.textDim else Theme.text), LinearLayout.LayoutParams(slot, slot))
            addView(TextView(context).apply {
                text = label
                typeface = Theme.body
                setTextColor(if (dim) Theme.textDim else Theme.text)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.34f * u)
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (icon != null) marginStart = px(0.26f) })
            setOnClickListener { action() }
        }

    /** The URL box: looks like a text field, opens the keyboard dialog when selected. */
    private fun field(item: Field): TextView = TextView(context).apply {
        text = item.text
        typeface = Theme.body
        setTextColor(Theme.text)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.34f * u)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(0.28f), px(0.16f), px(0.28f), px(0.16f))
        isFocusable = true
        isClickable = true
        val idle = GradientDrawable().apply {
            setColor(0x33000000)
            cornerRadius = px(0.16f).toFloat()
            setStroke(maxOf(1, px(0.02f)), 0x55FFFFFF)
        }
        background = Theme.focusable(px(0.16f).toFloat(), maxOf(2, px(0.035f)), idle)
        setOnClickListener { item.action() }
    }
}
