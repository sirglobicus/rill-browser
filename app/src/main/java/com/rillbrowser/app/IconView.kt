package com.rillbrowser.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Outline icons drawn with strokes, so they stay crisp at any resolution and need no image assets. */
class IconView(context: Context, private val kind: Kind, color: Int = Theme.text) : View(context) {
    enum class Kind { URL, SEARCH, STAR, GEAR, ARROW_LEFT, ARROW_RIGHT, RELOAD, CHEVRON_LEFT }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    init {
        isFocusable = false
        isClickable = false
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        paint.strokeWidth = s * 0.085f
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s

        when (kind) {
            Kind.URL -> { // wide pill with a text cursor; uses the whole (wide) view
                val pad = paint.strokeWidth
                val r = RectF(pad, height * 0.08f + pad / 2, width - pad, height * 0.92f - pad / 2)
                c.drawRoundRect(r, r.height() / 2, r.height() / 2, paint)
                val cx = r.right - r.height() * 0.62f
                val h = r.height() * 0.26f
                c.drawLine(cx, r.centerY() - h, cx, r.centerY() + h, paint)
                c.drawLine(cx - h * 0.35f, r.centerY() - h, cx + h * 0.35f, r.centerY() - h, paint)
                c.drawLine(cx - h * 0.35f, r.centerY() + h, cx + h * 0.35f, r.centerY() + h, paint)
            }
            Kind.SEARCH -> {
                c.drawCircle(x(0.42f), y(0.42f), s * 0.3f, paint)
                c.drawLine(x(0.64f), y(0.64f), x(0.92f), y(0.92f), paint)
            }
            Kind.STAR -> {
                val p = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.47f else 0.2f
                    val a = -PI / 2 + i * PI / 5
                    val px = x(0.5f + r * cos(a).toFloat())
                    val py = y(0.54f + r * sin(a).toFloat())
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
                c.drawPath(p, paint)
            }
            Kind.GEAR -> {
                val p = Path()
                val teeth = 8
                for (k in 0 until teeth) {
                    val base = k * 2 * PI / teeth
                    val d = PI / 180
                    val pts = listOf(base - 15 * d to 0.35f, base - 10 * d to 0.47f, base + 10 * d to 0.47f, base + 15 * d to 0.35f)
                    for ((i, pt) in pts.withIndex()) {
                        val px = x(0.5f + pt.second * cos(pt.first).toFloat())
                        val py = y(0.5f + pt.second * sin(pt.first).toFloat())
                        if (k == 0 && i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                    }
                }
                p.close()
                c.drawPath(p, paint)
                c.drawCircle(x(0.5f), y(0.5f), s * 0.15f, paint)
            }
            Kind.ARROW_LEFT, Kind.ARROW_RIGHT -> {
                val dir = if (kind == Kind.ARROW_LEFT) -1f else 1f
                val tail = 0.5f - dir * 0.42f
                val tip = 0.5f + dir * 0.42f
                val head = tip - dir * 0.28f
                c.drawLine(x(tail), y(0.5f), x(tip), y(0.5f), paint)
                c.drawLine(x(head), y(0.2f), x(tip), y(0.5f), paint)
                c.drawLine(x(head), y(0.8f), x(tip), y(0.5f), paint)
            }
            Kind.RELOAD -> {
                // ring open at the upper right, clockwise, with a filled arrowhead on the end of the arc
                val rad = 0.36f
                val r = RectF(x(0.5f - rad), y(0.5f - rad), x(0.5f + rad), y(0.5f + rad))
                c.drawArc(r, 25f, 275f, false, paint)
                val a = 300.0 * PI / 180
                val ex = x(0.5f + rad * cos(a).toFloat())
                val ey = y(0.5f + rad * sin(a).toFloat())
                val tx = -sin(a).toFloat() // clockwise tangent at the end of the arc
                val ty = cos(a).toFloat()
                val nx = -ty
                val ny = tx
                val head = Path().apply {
                    moveTo(ex + tx * s * 0.24f, ey + ty * s * 0.24f)
                    lineTo(ex + nx * s * 0.17f, ey + ny * s * 0.17f)
                    lineTo(ex - nx * s * 0.17f, ey - ny * s * 0.17f)
                    close()
                }
                val old = paint.style
                paint.style = Paint.Style.FILL_AND_STROKE
                c.drawPath(head, paint)
                paint.style = old
            }
            Kind.CHEVRON_LEFT -> {
                val p = Path()
                p.moveTo(x(0.64f), y(0.18f))
                p.lineTo(x(0.3f), y(0.5f))
                p.lineTo(x(0.64f), y(0.82f))
                c.drawPath(p, paint)
            }
        }
    }
}
