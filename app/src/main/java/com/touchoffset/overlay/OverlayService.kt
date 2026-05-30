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
    private var touchCaptureView: View? = null
    private var panelView: View? = null
    private var panelBinding: OverlayPanelBinding? = null
    private var isPanelExpanded = true
    private var step = 5

    private var panelInitialX = 0
    private var panelInitialY = 0
    private var touchInitialX = 0f
    private var touchInitialY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            addTouchCaptureOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Touch overlay failed: ${e.message}")
        }
        try {
            addControlPanel()
        } catch (e: Exception) {
            Log.e(TAG, "Control panel failed: ${e.message}")
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
        removeTouchCaptureOverlay()
        removeControlPanel()
        super.onDestroy()
    }

    // ─── Transparent fullscreen touch capture ─────────────────────────────────

    private fun addTouchCaptureOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSPARENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val capture = View(this)
        capture.setBackgroundColor(0x00000000)
        capture.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                TouchAccessibilityService.dispatchOffsetTouch(event.rawX, event.rawY)
            }
            false
        }
        windowManager.addView(capture, params)
        touchCaptureView = capture
    }

    private fun removeTouchCaptureOverlay() {
        touchCaptureView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { /* ignore */ }
            touchCaptureView = null
        }
    }

    // ─── Floating control panel ────────────────────────────────────────────────

    private fun addControlPanel() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 120
        }

        val binding = OverlayPanelBinding.inflate(LayoutInflater.from(this))
        panelBinding = binding

        updatePanelDisplay(binding)

        // Arrow buttons
        binding.btnUp.setOnClickListener    { changeOffset(0, -step);  updatePanelDisplay(binding) }
        binding.btnDown.setOnClickListener  { changeOffset(0, +step);  updatePanelDisplay(binding) }
        binding.btnLeft.setOnClickListener  { changeOffset(-step, 0);  updatePanelDisplay(binding) }
        binding.btnRight.setOnClickListener { changeOffset(+step, 0);  updatePanelDisplay(binding) }
        binding.btnReset.setOnClickListener {
            OffsetState.offsetX = 0; OffsetState.offsetY = 0
            updatePanelDisplay(binding)
            broadcastOffsetChanged()
        }

        // Step size buttons
        listOf(
            binding.btnStep1  to 1,
            binding.btnStep5  to 5,
            binding.btnStep10 to 10,
            binding.btnStep20 to 20
        ).forEach { (btn, s) ->
            btn.setOnClickListener {
                step = s
                highlightStep(binding, s)
            }
        }
        highlightStep(binding, step)

        // Collapse / expand
        binding.btnCollapse.setOnClickListener {
            if (isPanelExpanded) {
                binding.expandableContent.visibility = View.GONE
                binding.btnCollapse.text = "+"
                isPanelExpanded = false
            } else {
                binding.expandableContent.visibility = View.VISIBLE
                binding.btnCollapse.text = "\u2212"
                isPanelExpanded = true
            }
        }

        // Stop
        binding.overlayBtnStop.setOnClickListener {
            stopSelf()
            sendBroadcast(Intent(MainActivity.ACTION_SERVICE_STOPPED))
        }

        // Drag
        binding.overlayRoot.setOnTouchListener(makeDragListener(params, binding.overlayRoot))

        windowManager.addView(binding.root, params)
        panelView = binding.root
    }

    private fun changeOffset(dx: Int, dy: Int) {
        OffsetState.offsetX = (OffsetState.offsetX + dx).coerceIn(-300, 300)
        OffsetState.offsetY = (OffsetState.offsetY + dy).coerceIn(-300, 300)
        broadcastOffsetChanged()
    }

    private fun updatePanelDisplay(binding: OverlayPanelBinding) {
        binding.overlayTvX.text = OffsetState.offsetX.toString()
        binding.overlayTvY.text = OffsetState.offsetY.toString()
        binding.offsetPreview.offsetX = OffsetState.offsetX
        binding.offsetPreview.offsetY = OffsetState.offsetY
    }

    private fun highlightStep(binding: OverlayPanelBinding, active: Int) {
        mapOf(
            binding.btnStep1  to 1,
            binding.btnStep5  to 5,
            binding.btnStep10 to 10,
            binding.btnStep20 to 20
        ).forEach { (btn, s) ->
            btn.setTextColor(
                if (s == active) resources.getColor(R.color.accent, null)
                else 0xFFFFFFFF.toInt()
            )
        }
    }

    private fun makeDragListener(
        params: WindowManager.LayoutParams,
        rootView: View
    ): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    panelInitialX = params.x
                    panelInitialY = params.y
                    touchInitialX = event.rawX
                    touchInitialY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchInitialX).toInt()
                    val dy = (event.rawY - touchInitialY).toInt()
                    if (isDragging || abs(dx) > 8 || abs(dy) > 8) {
                        isDragging = true
                        params.x = panelInitialX - dx
                        params.y = panelInitialY + dy
                        try { windowManager.updateViewLayout(rootView, params) } catch (e: Exception) { /* ignore */ }
                        true
                    } else false
                }
                MotionEvent.ACTION_UP -> { val wasDragging = isDragging; isDragging = false; wasDragging }
                else -> false
            }
        }
    }

    private fun removeControlPanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { /* ignore */ }
            panelView = null
        }
        panelBinding = null
    }

    private fun broadcastOffsetChanged() {
        sendBroadcast(Intent(MainActivity.ACTION_OFFSET_CHANGED).apply {
            putExtra("offsetX", OffsetState.offsetX)
            putExtra("offsetY", OffsetState.offsetY)
        })
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.channel_description); setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }
}
