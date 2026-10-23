package com.rillbrowser.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * The home screen: logo + wordmark and a 2x2 grid, centred as one group.
 *
 * Every size is a multiple of one "unit" = min(width / 16, height / 9), so the layout keeps the same
 * proportions on 16:9, ultra-wide or odd resolutions and never overflows the short side. The unit is
 * derived in onMeasure and every child is measured and placed here directly (no LayoutParams juggling).
 */
class LandingView(
    context: Context,
    onUrl: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
) : ViewGroup(context) {

    private inner class Tile(val kind: IconView.Kind, label: String, action: () -> Unit) : LinearLayout(context) {
        val icon = IconView(context, kind)
        val text = TextView(context).apply {
            this.text = label
            typeface = Theme.wordmark
            setTextColor(Theme.text)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            addView(icon, LinearLayout.LayoutParams(1, 1))
            addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            setOnClickListener { action() }
            onFocusChangeListener = OnFocusChangeListener { _, focused ->
                animate().scaleX(if (focused) 1.05f else 1f).scaleY(if (focused) 1.05f else 1f).setDuration(120).start()
            }
        }
    }

    private val logo = ImageView(context).apply {
        setImageResource(R.drawable.ic_logo)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val wordmark = TextView(context).apply {
        text = "Rill"
        typeface = Theme.wordmark
        setTextColor(Theme.text)
        includeFontPadding = false
        maxLines = 1
    }
    private val urlTile = Tile(IconView.Kind.URL, "Enter URL", onUrl)
    private val searchTile = Tile(IconView.Kind.SEARCH, "Search", onSearch)
    private val bookmarksTile = Tile(IconView.Kind.STAR, "Bookmarks", onBookmarks)
    private val settingsTile = Tile(IconView.Kind.GEAR, "Settings", onSettings)
    private val tiles = listOf(urlTile, searchTile, bookmarksTile, settingsTile)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    private var unit = 0f
    private var logoSize = 0
    private var brandGap = 0
    private var tileW = 0
    private var tileH = 0
    private var gapX = 0
    private var gapY = 0
    private var brandToTiles = 0

    /** The tile that should hold focus when the screen appears. */
    val defaultFocus: View get() = searchTile

    init {
        background = Theme.background()
        setWillNotDraw(false)
        clipChildren = false
        addView(logo)
        addView(wordmark)
        tiles.forEach { addView(it) }
    }

    private fun px(v: Float) = (v * unit).toInt()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(w, h)
        if (w == 0 || h == 0) return

        // Never larger than the real display allows, even if this view were handed a bogus size.
        val dm = resources.displayMetrics
        val u = min(min(w, dm.widthPixels) / 16f, min(h, dm.heightPixels) / 9f)
        if (u != unit) {
            unit = u
            Log.i("Rill", "landing unit=$u view=${w}x$h display=${dm.widthPixels}x${dm.heightPixels} density=${dm.density}")
            applyUnit()
        }

        fun exact(v: Int) = MeasureSpec.makeMeasureSpec(v, MeasureSpec.EXACTLY)
        logo.measure(exact(logoSize), exact(logoSize))
        wordmark.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(logoSize, MeasureSpec.AT_MOST))
        tiles.forEach { it.measure(exact(tileW), exact(tileH)) }
    }

    private fun applyUnit() {
        logoSize = px(1.4f)
        brandGap = px(0.5f)
        tileW = px(3.4f)
        tileH = px(1.55f)
        gapX = px(0.44f)
        gapY = px(0.32f)
        brandToTiles = px(0.75f)

        wordmark.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.05f * unit)
        val radius = px(0.18f).toFloat()
        for (t in tiles) {
            t.icon.layoutParams = LinearLayout.LayoutParams(if (t.kind == IconView.Kind.URL) px(1.1f) else px(0.6f), px(0.6f))
                .apply { bottomMargin = px(0.1f) }
            t.text.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.4f * unit)
            t.background = Theme.focusable(
                radius, maxOf(2, px(0.04f)),
                Theme.gradient(0xFF212C52.toInt(), 0xFF18213F.toInt(), radius, Theme.hairline, 1),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        if (unit == 0f) return
        val brandW = logoSize + brandGap + wordmark.measuredWidth
        val totalH = logoSize + brandToTiles + tileH * 2 + gapY
        val top = (h - totalH) / 2

        val brandX = (w - brandW) / 2
        logo.layout(brandX, top, brandX + logoSize, top + logoSize)
        val wx = brandX + logoSize + brandGap
        val wy = top + (logoSize - wordmark.measuredHeight) / 2
        wordmark.layout(wx, wy, wx + wordmark.measuredWidth, wy + wordmark.measuredHeight)

        val left = (w - (tileW * 2 + gapX)) / 2
        val y1 = top + logoSize + brandToTiles
        val y2 = y1 + tileH + gapY
        fun place(v: View, x: Int, y: Int) = v.layout(x, y, x + tileW, y + tileH)
        place(urlTile, left, y1)
        place(searchTile, left + tileW + gapX, y1)
        place(bookmarksTile, left, y2)
        place(settingsTile, left + tileW + gapX, y2)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w == 0 || h == 0) return
        glow.shader = RadialGradient(
            w / 2f, h * 0.32f, maxOf(w, h) * 0.55f,
            intArrayOf(0x2A2B8BEA, 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glow)
    }
}
