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

    private var panelInitialX = 0
    private var panelInitialY = 0
    private var touchInitialX = 0f
    private var touchInitialY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addTouchCaptureOverlay()
        addControlPanel()
        OffsetState.isServiceRunning = true
        Log.d(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        OffsetState.isServiceRunning = false
        removeTouchCaptureOverlay()
        removeControlPanel()
        super.onDestroy()
        Log.d(TAG, "OverlayService destroyed")
    }

    // ─── Touch capture overlay ────────────────────────────────────────────────

    private fun addTouchCaptureOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val capture = View(this)
        capture.setBackgroundColor(0x00000000)
        capture.setOnTouchListener { _, event -> handleTouchEvent(event) }

        windowManager.addView(capture, params)
        touchCaptureView = capture
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val rawX = event.rawX
            val rawY = event.rawY
            val dispatched = TouchAccessibilityService.dispatchOffsetTouch(rawX, rawY)
            Log.d(TAG, "Touch at ($rawX,$rawY) → dispatched=$dispatched")
        }
        return false
    }

    private fun removeTouchCaptureOverlay() {
        touchCaptureView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { Log.w(TAG, "removeView: ${e.message}") }
            touchCaptureView = null
        }
    }

    // ─── Control panel ────────────────────────────────────────────────────────

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

        val inflater = LayoutInflater.from(this)
        val binding = OverlayPanelBinding.inflate(inflater)
        panelBinding = binding

        binding.overlaySliderX.value = OffsetState.offsetX.toFloat().coerceIn(-300f, 300f)
        binding.overlaySliderY.value = OffsetState.offsetY.toFloat().coerceIn(-300f, 300f)
        binding.overlayTvX.text = OffsetState.offsetX.toString()
        binding.overlayTvY.text = OffsetState.offsetY.toString()

        binding.overlaySliderX.addOnChangeListener { _, value, _ ->
            OffsetState.offsetX = value.toInt()
            binding.overlayTvX.text = value.toInt().toString()
            broadcastOffsetChanged()
        }
        binding.overlaySliderY.addOnChangeListener { _, value, _ ->
            OffsetState.offsetY = value.toInt()
            binding.overlayTvY.text = value.toInt().toString()
            broadcastOffsetChanged()
        }

        binding.overlayBtnStop.setOnClickListener {
            stopSelf()
            sendBroadcast(Intent(MainActivity.ACTION_SERVICE_STOPPED))
        }

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

        binding.overlayRoot.setOnTouchListener(makeDragListener(params, binding.overlayRoot))

        windowManager.addView(binding.root, params)
        panelView = binding.root
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
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchInitialX).toInt()
                    val dy = (event.rawY - touchInitialY).toInt()
                    if (abs(dx) > 4 || abs(dy) > 4) {
                        params.x = panelInitialX - dx
                        params.y = panelInitialY + dy
                        try {
                            windowManager.updateViewLayout(rootView, params)
                        } catch (e: Exception) {
                            Log.w(TAG, "updateViewLayout: ${e.message}")
                        }
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun removeControlPanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { Log.w(TAG, "removePanel: ${e.message}") }
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
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(null, "Stop", stopIntent).build()
            )
            .setOngoing(true)
            .build()
    }
}
