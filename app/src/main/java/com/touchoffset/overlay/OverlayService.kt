package com.touchoffset.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.touchoffset.overlay.databinding.OverlayPanelBinding
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "touch_offset_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.touchoffset.overlay.ACTION_STOP"
    }

    private lateinit var windowManager: WindowManager

    // Touch capture overlay (fullscreen, intercepts touches when active)
    private var captureView: View? = null
    private lateinit var captureParams: WindowManager.LayoutParams
    private var isCapturing = false

    // Floating control panel
    private var panelView: View? = null
    private var panelBinding: OverlayPanelBinding? = null
    private var isPanelExpanded = true
    private var step = 5

    // Drag state for panel
    private var panelParamsX = 0; private var panelParamsY = 0
    private var touchRawX = 0f; private var touchRawY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        try { setupCaptureOverlay() } catch (e: Exception) {
            Log.e(TAG, "Capture overlay error: ${e.message}")
        }
        try { addControlPanel() } catch (e: Exception) {
            Log.e(TAG, "Control panel error: ${e.message}")
        }

        OffsetState.isServiceRunning = true
        Log.d(TAG, "OverlayService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        OffsetState.isServiceRunning = false
        captureView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panelView?.let  { try { windowManager.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    // ─── Touch capture overlay ─────────────────────────────────────────────────

    private fun setupCaptureOverlay() {
        captureParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Start NON-TOUCHABLE: touches pass through; user enables interception via button
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val view = View(this)
        view.setBackgroundColor(0x00000000)
        view.setOnTouchListener { _, event ->
            // Consume touch and dispatch offset version
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_UP) {
                TouchAccessibilityService.dispatchOffsetTouch(
                    event.rawX, event.rawY, event.actionMasked
                )
            }
            true  // consume so only offset touch reaches the app
        }
        windowManager.addView(view, captureParams)
        captureView = view
    }

    private fun setCapturing(enabled: Boolean) {
        isCapturing = enabled
        val flags = if (enabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            // No FLAG_NOT_TOUCHABLE → overlay intercepts touches
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // FLAG_NOT_TOUCHABLE → overlay is invisible to touch system
        }
        captureParams.flags = flags
        captureView?.let { try { windowManager.updateViewLayout(it, captureParams) } catch (e: Exception) {
            Log.e(TAG, "updateViewLayout: ${e.message}")
        }}
        panelBinding?.btnToggleCapture?.let { btn ->
            btn.text = if (enabled) "● INTERCEPT ON" else "● INTERCEPT OFF"
            btn.setBackgroundColor(
                if (enabled) 0xFFCF6679.toInt() else 0xFF333355.toInt()
            )
        }
    }

    // ─── Floating panel ────────────────────────────────────────────────────────

    private fun addControlPanel() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 100 }

        val binding = OverlayPanelBinding.inflate(LayoutInflater.from(this))
        panelBinding = binding

        refreshDisplay(binding)

        // Intercept toggle
        binding.btnToggleCapture.setOnClickListener { setCapturing(!isCapturing) }

        // Arrows
        binding.btnUp.setOnClickListener    { nudge(0, -step) }
        binding.btnDown.setOnClickListener  { nudge(0, +step) }
        binding.btnLeft.setOnClickListener  { nudge(-step, 0) }
        binding.btnRight.setOnClickListener { nudge(+step, 0) }
        binding.btnReset.setOnClickListener {
            OffsetState.offsetX = 0; OffsetState.offsetY = 0
            refreshDisplay(binding); broadcast()
        }

        // Step
        listOf(binding.btnStep1 to 1, binding.btnStep5 to 5,
               binding.btnStep10 to 10, binding.btnStep20 to 20)
            .forEach { (btn, s) -> btn.setOnClickListener { step = s; highlightStep(binding) } }
        highlightStep(binding)

        // Collapse
        binding.btnCollapse.setOnClickListener {
            isPanelExpanded = !isPanelExpanded
            binding.expandableContent.visibility = if (isPanelExpanded) View.VISIBLE else View.GONE
            binding.btnCollapse.text = if (isPanelExpanded) "\u2212" else "+"
        }

        // Stop
        binding.overlayBtnStop.setOnClickListener {
            stopSelf()
            sendBroadcast(Intent(MainActivity.ACTION_SERVICE_STOPPED))
        }

        // Drag (on header area = the root itself but only when not clicking buttons)
        binding.overlayRoot.setOnTouchListener(dragListener(params, binding.overlayRoot))

        windowManager.addView(binding.root, params)
        panelView = binding.root
    }

    private fun nudge(dx: Int, dy: Int) {
        OffsetState.offsetX = (OffsetState.offsetX + dx).coerceIn(-400, 400)
        OffsetState.offsetY = (OffsetState.offsetY + dy).coerceIn(-400, 400)
        panelBinding?.let { refreshDisplay(it) }
        broadcast()
    }

    private fun refreshDisplay(b: OverlayPanelBinding) {
        b.overlayTvX.text = OffsetState.offsetX.toString()
        b.overlayTvY.text = OffsetState.offsetY.toString()
        // Use shiftX/shiftY (renamed props in OffsetPreviewView to avoid View conflicts)
        b.offsetPreview.shiftX = OffsetState.offsetX
        b.offsetPreview.shiftY = OffsetState.offsetY
    }

    private fun highlightStep(b: OverlayPanelBinding) {
        val accent = resources.getColor(R.color.accent, null)
        val white = 0xFFFFFFFF.toInt()
        listOf(b.btnStep1 to 1, b.btnStep5 to 5, b.btnStep10 to 10, b.btnStep20 to 20)
            .forEach { (btn, s) -> btn.setTextColor(if (s == step) accent else white) }
    }

    private fun broadcast() {
        sendBroadcast(Intent(MainActivity.ACTION_OFFSET_CHANGED).apply {
            putExtra("offsetX", OffsetState.offsetX)
            putExtra("offsetY", OffsetState.offsetY)
        })
    }

    private fun dragListener(
        params: WindowManager.LayoutParams,
        root: View
    ) = View.OnTouchListener { _, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panelParamsX = params.x; panelParamsY = params.y
                touchRawX = e.rawX; touchRawY = e.rawY
                isDragging = false; false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - touchRawX).toInt()
                val dy = (e.rawY - touchRawY).toInt()
                if (isDragging || abs(dx) > 8 || abs(dy) > 8) {
                    isDragging = true
                    params.x = panelParamsX - dx
                    params.y = panelParamsY + dy
                    try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
                    true
                } else false
            }
            MotionEvent.ACTION_UP -> { val was = isDragging; isDragging = false; was }
            else -> false
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true).build()
    }
}
