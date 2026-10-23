package com.rillbrowser.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView

/**
 * D-pad -> pointer. Arrows move the cursor (holding accelerates), center sends a touch down/up at the cursor
 * (a short press is a click; WebView turns a long press into a long-click), hover events are sent as it moves,
 * and pushing against a screen edge scrolls the page.
 */
class CursorInput(
    private val cursor: CursorView,
    private val density: Float,
    private val target: () -> WebView?,
    private val speed: () -> Int,
    private val onClick: () -> Unit,
    private val onMoved: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val held = HashSet<Int>()
    private var holdStart = 0L
    private var downTime = 0L
    private var centerDown = false

    private val tick = object : Runnable {
        override fun run() {
            if (held.isEmpty()) return
            step()
            handler.postDelayed(this, 16)
        }
    }

    fun reset() {
        held.clear()
        handler.removeCallbacks(tick)
        if (centerDown) sendTouch(MotionEvent.ACTION_CANCEL)
        centerDown = false
    }

    fun onKey(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) {
                    if (held.isEmpty()) {
                        holdStart = SystemClock.uptimeMillis()
                        handler.post(tick)
                    }
                    held.add(e.keyCode)
                } else held.remove(e.keyCode)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down && e.repeatCount == 0 && !centerDown) {
                    centerDown = true
                    downTime = SystemClock.uptimeMillis()
                    onClick()
                    sendTouch(MotionEvent.ACTION_DOWN)
                } else if (!down && centerDown) {
                    centerDown = false
                    sendTouch(MotionEvent.ACTION_UP)
                }
                return true
            }
        }
        return false
    }

    private fun step() {
        val heldMs = SystemClock.uptimeMillis() - holdStart
        val base = when (speed()) { 0 -> 3f; 2 -> 8f; else -> 5f } * density
        val px = base * (1f + (heldMs / 350f).coerceAtMost(4f))
        val dx = (if (KeyEvent.KEYCODE_DPAD_RIGHT in held) 1 else 0) - (if (KeyEvent.KEYCODE_DPAD_LEFT in held) 1 else 0)
        val dy = (if (KeyEvent.KEYCODE_DPAD_DOWN in held) 1 else 0) - (if (KeyEvent.KEYCODE_DPAD_UP in held) 1 else 0)
        val oldX = cursor.cx
        val oldY = cursor.cy
        cursor.moveTo(oldX + dx * px, oldY + dy * px)
        onMoved()
        sendHover()

        // Pushing against an edge scrolls the page instead.
        val pushedX = dx != 0 && cursor.cx == oldX
        val pushedY = dy != 0 && cursor.cy == oldY
        if (pushedX || pushedY) {
            target()?.scrollBy(if (pushedX) (dx * px).toInt() else 0, if (pushedY) (dy * px).toInt() else 0)
        }
    }

    private fun sendHover() {
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, cursor.cx, cursor.cy, 0)
        ev.source = InputDevice.SOURCE_MOUSE
        target()?.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    private fun sendTouch(action: Int) {
        val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, cursor.cx, cursor.cy, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        target()?.dispatchTouchEvent(ev)
        ev.recycle()
    }
}
