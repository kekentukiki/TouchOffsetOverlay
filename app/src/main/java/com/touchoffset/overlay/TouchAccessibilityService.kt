package com.touchoffset.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService that exposes dispatchOffsetTouch() so OverlayService
 * can call it to inject a synthetic touch at (rawX + offsetX, rawY + offsetY).
 */
class TouchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchA11yService"
        var instance: TouchAccessibilityService? = null
            private set

        /**
         * Dispatch a single tap at the given coordinates via the Accessibility API.
         * Returns true if the gesture was accepted.
         */
        fun dispatchOffsetTouch(rawX: Float, rawY: Float): Boolean {
            val svc = instance ?: return false
            val targetX = rawX + OffsetState.offsetX
            val targetY = rawY + OffsetState.offsetY

            // Clamp to avoid dispatching off-screen
            val path = Path().apply { moveTo(targetX, targetY) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            return svc.dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Gesture dispatched → ($targetX, $targetY)")
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w(TAG, "Gesture cancelled")
                }
            }, null)
        }
    }

    override fun onServiceConnected() {
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't consume events here — touch interception is handled
        // by the transparent overlay window in OverlayService.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
