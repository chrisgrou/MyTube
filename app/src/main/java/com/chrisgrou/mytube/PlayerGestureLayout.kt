package com.chrisgrou.mytube

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Hosts the fullscreen video view and adds the usual player swipe gestures:
 * a vertical drag on the left half changes screen brightness, on the right half
 * it changes volume.
 *
 * Only vertical drags past the touch slop are intercepted, so taps (play/pause,
 * showing the controls) and horizontal drags (seeking) still reach YouTube's own
 * player underneath untouched.
 */
class PlayerGestureLayout(context: Context) : FrameLayout(context) {

    /** (startedOnLeftHalf, deltaPixels since the previous callback; up is negative). */
    var onVerticalDrag: ((onLeftHalf: Boolean, deltaPixels: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var startedOnLeftHalf = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                val dx = ev.x - downX
                // Clearly vertical, and past the slop: this one is ours. Anything
                // else (tap, horizontal seek) is left to the player.
                if (!dragging && abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.5f) {
                    dragging = true
                    startedOnLeftHalf = downX < width / 2f
                    lastY = ev.y
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val delta = ev.y - lastY
                    lastY = ev.y
                    onVerticalDrag?.invoke(startedOnLeftHalf, delta)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    onDragEnd?.invoke()
                    return true
                }
            }
        }
        return dragging
    }
}
