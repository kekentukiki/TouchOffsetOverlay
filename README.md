# Touch Offset Overlay

An Android app that shifts every touch event by a configurable X/Y pixel offset — perfect for digital drawing with a stylus or pen that has nib drift, or for offsetting touch registration on any app.

## Requirements

- Android 10+ (API 29+)
- Android Studio Hedgehog (2023.1.1) or newer
- Kotlin 1.9+, Gradle 8.2

## Open in Android Studio

1. Download and unzip `TouchOffsetOverlay.zip`
2. Open Android Studio → **File → Open** → select the `TouchOffsetOverlay` folder
3. Wait for Gradle sync to complete
4. Run on a physical device (overlay + accessibility services don't work on emulators)

## First-time Setup on Device

### 1. Grant Overlay Permission
The app will prompt you. Tap **Grant** → toggle "Allow display over other apps" → return to the app.

### 2. Enable Accessibility Service
Tap **Enable** → find **"Touch Offset Overlay Service"** in the list → tap it → toggle it on.

### 3. Set Offsets
Use the **X** and **Y** sliders:
- **+X** → shifts touch right | **-X** → shifts touch left
- **+Y** → shifts touch down | **-Y** → shifts touch up

### 4. Start the Overlay
Tap **Start Overlay**. A small floating panel appears. You can:
- Drag it anywhere on screen
- Collapse it to a minimal bar (tap **−**)
- Adjust offsets from the floating panel directly
- Stop from the panel or the main app

## Architecture

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Permission setup, main controls, slider state |
| `OverlayService.kt` | Foreground service: transparent touch-capture window + floating control panel |
| `TouchAccessibilityService.kt` | Injects synthetic offset touch via `dispatchGesture()` |
| `OffsetState.kt` | Shared singleton for X/Y values and running state |

## How Touch Interception Works

```
User finger touches screen
       ↓
Transparent fullscreen overlay (OverlayService) captures ACTION_DOWN
       ↓
Calls TouchAccessibilityService.dispatchOffsetTouch(rawX, rawY)
       ↓
AccessibilityService.dispatchGesture() injects touch at (rawX + offsetX, rawY + offsetY)
       ↓
Original touch also propagates through (overlay returns false)
```

> **Note:** Some apps with cheat/anti-bot detection may reject injected gestures. The app works best with drawing apps, note apps, and standard Android UIs.

## Permissions Used

| Permission | Why |
|-----------|-----|
| `SYSTEM_ALERT_WINDOW` | Draw the floating panel and capture overlay |
| `BIND_ACCESSIBILITY_SERVICE` | Inject synthetic touch events via `dispatchGesture()` |
| `FOREGROUND_SERVICE` | Keep the overlay alive when app is in background |
