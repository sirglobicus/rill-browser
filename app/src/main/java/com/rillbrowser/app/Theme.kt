package com.rillbrowser.app

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/**
 * Rill's look: neutral dark-gray surfaces with soft vertical gradients, blue only as the accent
 * (focus rings, highlights). All sizes elsewhere are multiples of a screen "unit" (see [LandingView]).
 */
object Theme {
    val bgTop = 0xFF282A30.toInt()
    val bgBottom = 0xFF1A1B20.toInt()
    val accent = 0xFF2B8BEA.toInt()
    val accentSoft = 0x332B8BEA
    val text = 0xFFF2F4F8.toInt()
    val textDim = 0xFF8D94A5.toInt()
    val hairline = 0x22FFFFFF
    val scrim = 0xA6000000.toInt()

    val wordmark: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val body: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun background() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bgTop, bgBottom))

    fun gradient(top: Int, bottom: Int, radius: Float, stroke: Int = 0, strokePx: Int = 0) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = radius
            if (strokePx > 0) setStroke(strokePx, stroke)
        }

    /** Row/tile background: transparent (or [idle]) normally, accent-tinted with an accent ring when focused. */
    fun focusable(radius: Float, strokePx: Int, idle: android.graphics.drawable.Drawable? = null): StateListDrawable {
        val focused = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x462B8BEA, 0x2A2B8BEA)).apply {
            cornerRadius = radius
            setStroke(strokePx, accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(), idle ?: GradientDrawable().apply { setColor(0); cornerRadius = radius })
        }
    }
}
