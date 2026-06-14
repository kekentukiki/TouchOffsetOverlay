package com.touchoffset.overlay

object OffsetState {
    @Volatile var offsetX: Int = 0
    @Volatile var offsetY: Int = 0
    @Volatile var isServiceRunning: Boolean = false
    /** True when TouchAccessibilityService is connected and ready to dispatch gestures */
    @Volatile var a11yConnected: Boolean = false
}
