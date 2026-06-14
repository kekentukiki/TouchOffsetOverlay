package com.touchoffset.overlay

object OffsetState {
    @Volatile var offsetX: Int = 0
    @Volatile var offsetY: Int = 0
    @Volatile var isServiceRunning: Boolean = false
    /** True while AccessibilityService is injecting a gesture — overlay must pass it through */
    @Volatile var injecting: Boolean = false
    /** True when TouchAccessibilityService is connected and ready */
    @Volatile var a11yConnected: Boolean = false
}
