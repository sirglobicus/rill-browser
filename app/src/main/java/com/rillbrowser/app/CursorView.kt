package com.rillbrowser.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** The on-screen pointer. Pure drawing; movement and event injection live in [CursorInput]. */
class CursorView(context: Context) : View(context) {
    var cx = 0f
        private set
    var cy = 0f
        private set
    private val density = resources.displayMetrics.density
    private val radius = 11f * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x552B8BEA }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f * density; color = 0xFFFFFFFF.toInt()
    }
    private val edge = Paint(ring).apply { strokeWidth = 6f * density; color = 0xFF000000.toInt() }

    init {
        isFocusable = false
        isClickable = false
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (ow == 0 && oh == 0) { cx = w / 2f; cy = h / 2f }
    }

    fun moveTo(x: Float, y: Float) {
        cx = x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        cy = y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        c.drawCircle(cx, cy, radius, fill)
        c.drawCircle(cx, cy, radius, edge)
        c.drawCircle(cx, cy, radius, ring)
    }
}
