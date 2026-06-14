package com.touchoffset.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

class TouchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchA11y"
        private var instance: TouchAccessibilityService? = null

        private var lastStroke: GestureDescription.StrokeDescription? = null
        private var lastOx = 0f
        private var lastOy = 0f

        private val mainHandler = Handler(Looper.getMainLooper())

        fun handleTouchEvent(rawX: Float, rawY: Float, action: Int, density: Float = 1f) {
            val svc = instance ?: run {
                Log.w(TAG, "Accessibility service not connected – cannot dispatch gesture")
                return
            }
            val ox = rawX + (OffsetState.offsetX * density)
            val oy = rawY + (OffsetState.offsetY * density)

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    val path = Path().apply { moveTo(ox, oy) }
                    val stroke = GestureDescription.StrokeDescription(path, 0L, 16L, true)
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
                    val stroke = prev.continueStroke(path, 0L, 16L, false)
                    lastStroke = null
                    dispatch(svc, stroke)
                }
            }
        }

        private fun dispatch(svc: TouchAccessibilityService, stroke: GestureDescription.StrokeDescription) {
            // Signal overlay to pass through the injected gesture (prevents re-capture loop)
            OffsetState.injecting = true
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            svc.dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    // Keep injecting flag alive briefly so the events arrive before we reset
                    mainHandler.postDelayed({ OffsetState.injecting = false }, 32)
                }
                override fun onCancelled(g: GestureDescription) {
                    OffsetState.injecting = false
                    Log.w(TAG, "Gesture cancelled")
                }
            }, null)
        }
    }

    override fun onServiceConnected() {
        instance = this
        OffsetState.a11yConnected = true
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }
    override fun onInterrupt() { Log.d(TAG, "Interrupted") }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        OffsetState.a11yConnected = false
        lastStroke = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        OffsetState.a11yConnected = false
        lastStroke = null
        super.onDestroy()
    }
}
