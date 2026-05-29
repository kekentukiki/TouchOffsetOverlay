package com.touchoffset.overlay

/**
 * Singleton that holds shared state between MainActivity, OverlayService,
 * and TouchAccessibilityService without needing IPC or a full ViewModel.
 */
object OffsetState {
    @Volatile var offsetX: Int = 0
    @Volatile var offsetY: Int = 0
    @Volatile var isServiceRunning: Boolean = false
}
