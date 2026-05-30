package com.touchoffset.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Receives offset-adjusted touch events from OverlayService and dispatches
 * them as continuous gestures using GestureDescription stroke continuations.
 * This correctly handles drag/draw strokes, not just individual taps.
 */
class TouchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchA11y"
        private var instance: TouchAccessibilityService? = null

        // Stroke state for continuous drawing
        private var lastStroke: GestureDescription.StrokeDescription? = null
        private var lastOx = 0f
        private var lastOy = 0f

        /**
         * Call this for every ACTION_DOWN / ACTION_MOVE / ACTION_UP event.
         * Builds a continuous gesture stroke so drawing apps receive a real drag.
         */
        fun handleTouchEvent(rawX: Float, rawY: Float, action: Int) {
            val svc = instance ?: return
            val ox = rawX + OffsetState.offsetX
            val oy = rawY + OffsetState.offsetY

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    val path = Path().apply { moveTo(ox, oy) }
                    val stroke = GestureDescription.StrokeDescription(
                        path, 0L, 16L, true  // willContinue = true
                    )
                    lastStroke = stroke
                    lastOx = ox; lastOy = oy
                    dispatch(svc, stroke)
                }
                MotionEvent.ACTION_MOVE -> {
                    val prev = lastStroke ?: return
                    val path = Path().apply { moveTo(lastOx, lastOy); lineTo(ox, oy) }
                    val stroke = prev.continueStroke(path, 0L, 16L, true)
                    lastStroke = stroke
                    lastOx = ox; lastOy = oy
                    dispatch(svc, stroke)
                }
                MotionEvent.ACTION_UP -> {
                    val prev = lastStroke ?: return
                    val path = Path().apply { moveTo(lastOx, lastOy); lineTo(ox, oy) }
                    val stroke = prev.continueStroke(path, 0L, 16L, false)  // willContinue = false → end
                    lastStroke = null
                    dispatch(svc, stroke)
                }
            }
        }

        private fun dispatch(svc: TouchAccessibilityService, stroke: GestureDescription.StrokeDescription) {
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            svc.dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCancelled(g: GestureDescription) { Log.w(TAG, "Gesture cancelled") }
            }, null)
        }
    }

    override fun onServiceConnected() {
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }

    override fun onInterrupt() {
        Log.d(TAG, "Interrupted")
    }

    override fun onDestroy() {
        instance = null
        lastStroke = null
        super.onDestroy()
    }
}
