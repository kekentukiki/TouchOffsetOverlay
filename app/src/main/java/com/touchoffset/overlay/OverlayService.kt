package com.touchoffset.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
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
        const val CHANNEL_ID      = "touch_offset_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP     = "com.touchoffset.overlay.ACTION_STOP"
        const val ACTION_PAUSE    = "com.touchoffset.overlay.ACTION_PAUSE"
        const val ACTION_RESUME   = "com.touchoffset.overlay.ACTION_RESUME"

        // Flags used when the capture overlay is ACTIVE (intercepts real touches)
        private const val FLAGS_TOUCHABLE =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        // Flags used when we want the overlay to be transparent to input
        private const val FLAGS_NOT_TOUCHABLE =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }

    private lateinit var windowManager: WindowManager
    private lateinit var captureParams: WindowManager.LayoutParams
    private var captureView: View? = null
    private var isCapturing = false

    private var panelView: View? = null
    private var panelBinding: OverlayPanelBinding? = null
    private var isPanelExpanded = true
    private var step = 5

    private var panelParamsX = 0; private var panelParamsY = 0
    private var touchRawX = 0f;   private var touchRawY = 0f
    private var isDragging = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Re-enables touch capture after the injected gesture has had time to be delivered. */
    private val resumeCaptureRunnable = Runnable {
        if (isCapturing) {
            captureParams.flags = FLAGS_TOUCHABLE
            captureView?.let {
                try { windowManager.updateViewLayout(it, captureParams) }
                catch (e: Exception) { Log.e(TAG, "resumeCapture: ${e.message}") }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(capturing = false))

        try { setupCaptureOverlay() } catch (e: Exception) { Log.e(TAG, "Capture: ${e.message}") }
        try { addControlPanel()     } catch (e: Exception) { Log.e(TAG, "Panel: ${e.message}")   }

        setCapturing(true)   // active immediately — no extra button press needed
        OffsetState.isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP   -> stopSelf()
            ACTION_PAUSE  -> setCapturing(false)
            ACTION_RESUME -> setCapturing(true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        OffsetState.isServiceRunning = false
        mainHandler.removeCallbacks(resumeCaptureRunnable)
        captureView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panelView?.let   { try { windowManager.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    // ─── Touch capture overlay ────────────────────────────────────────────────
    //
    // HOW THE INJECTION LOOP IS AVOIDED:
    //
    //   1. Real user touch arrives → overlay is TOUCHABLE → we capture it.
    //   2. Before dispatching the offset gesture we immediately flip the overlay
    //      to FLAG_NOT_TOUCHABLE.  Window-Manager applies this synchronously before
    //      the next input event is routed.
    //   3. The AccessibilityService injects the offset gesture.  Because the overlay
    //      is now FLAG_NOT_TOUCHABLE the input system skips it and delivers the
    //      gesture directly to the drawing app below.  ✓
    //   4. A Handler callback flips the overlay back to TOUCHABLE ~16 ms later so
    //      the next real MOVE/DOWN can be captured again.
    //
    // Net effect: the drawing app sees only the offset gestures, never the raw ones.

    private fun setupCaptureOverlay() {
        captureParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            FLAGS_NOT_TOUCHABLE,   // starts non-touchable; enabled in setCapturing()
            PixelFormat.TRANSPARENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val view = View(this)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    // Step 1: make overlay transparent to input BEFORE dispatching,
                    // so the injected offset gesture reaches the drawing app.
                    captureParams.flags = FLAGS_NOT_TOUCHABLE
                    try { windowManager.updateViewLayout(view, captureParams) }
                    catch (e: Exception) { Log.e(TAG, "pauseCapture: ${e.message}") }

                    // Step 2: dispatch offset gesture (arrives while overlay is NOT_TOUCHABLE)
                    TouchAccessibilityService.handleTouchEvent(
                        event.rawX, event.rawY, event.actionMasked,
                        resources.displayMetrics.density
                    )

                    // Step 3: restore touchable after one frame so the next real event
                    // is captured.  Remove any pending restore first to avoid stacking.
                    mainHandler.removeCallbacks(resumeCaptureRunnable)
                    mainHandler.postDelayed(resumeCaptureRunnable, 16L)
                }
            }
            true   // consume the real touch; only the offset copy reaches the drawing app
        }
        windowManager.addView(view, captureParams)
        captureView = view
    }

    private fun setCapturing(enabled: Boolean) {
        // Guard: silently refuse if AccessibilityService is not yet bound
        if (enabled && !OffsetState.a11yConnected) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(capturing = false, a11yMissing = true))
            panelBinding?.btnToggleCapture?.let { btn ->
                btn.text = "⚠ Aktifkan Accessibility Service dulu!"
                btn.setBackgroundColor(0xFFB8860B.toInt())
            }
            return
        }

        isCapturing = enabled
        mainHandler.removeCallbacks(resumeCaptureRunnable)

        captureParams.flags = if (enabled) FLAGS_TOUCHABLE else FLAGS_NOT_TOUCHABLE
        captureView?.let {
            try { windowManager.updateViewLayout(it, captureParams) }
            catch (e: Exception) { Log.e(TAG, "setCapturing: ${e.message}") }
        }

        panelBinding?.btnToggleCapture?.let { btn ->
            btn.text = if (enabled) "⏸ JEDA offset (aktif)" else "▶ MULAI offset (mati)"
            btn.setBackgroundColor(if (enabled) 0xFFCF6679.toInt() else 0xFF1B5E20.toInt())
        }

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(capturing = enabled))
    }

    // ─── Floating panel ──────────────────────────────────────────────────────

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

        binding.btnToggleCapture.setOnClickListener { setCapturing(!isCapturing) }
        binding.btnUp.setOnClickListener    { nudge(0, -step) }
        binding.btnDown.setOnClickListener  { nudge(0, +step) }
        binding.btnLeft.setOnClickListener  { nudge(-step, 0) }
        binding.btnRight.setOnClickListener { nudge(+step, 0) }
        binding.btnReset.setOnClickListener {
            OffsetState.offsetX = 0; OffsetState.offsetY = 0
            refreshDisplay(binding); broadcast()
        }
        listOf(binding.btnStep1 to 1, binding.btnStep5 to 5,
               binding.btnStep10 to 10, binding.btnStep20 to 20)
            .forEach { (btn, s) -> btn.setOnClickListener { step = s; highlightStep(binding) } }
        highlightStep(binding)

        binding.btnCollapse.setOnClickListener {
            isPanelExpanded = !isPanelExpanded
            binding.expandableContent.visibility = if (isPanelExpanded) View.VISIBLE else View.GONE
            binding.btnCollapse.text = if (isPanelExpanded) "\u2212" else "+"
        }
        binding.overlayBtnStop.setOnClickListener {
            stopSelf()
            sendBroadcast(Intent(MainActivity.ACTION_SERVICE_STOPPED))
        }
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
        b.offsetPreview.shiftX = OffsetState.offsetX
        b.offsetPreview.shiftY = OffsetState.offsetY
    }

    private fun highlightStep(b: OverlayPanelBinding) {
        val accent = resources.getColor(R.color.accent, null)
        listOf(b.btnStep1 to 1, b.btnStep5 to 5, b.btnStep10 to 10, b.btnStep20 to 20)
            .forEach { (btn, s) -> btn.setTextColor(if (s == step) accent else 0xFFFFFFFF.toInt()) }
    }

    private fun broadcast() {
        sendBroadcast(Intent(MainActivity.ACTION_OFFSET_CHANGED).apply {
            putExtra("offsetX", OffsetState.offsetX)
            putExtra("offsetY", OffsetState.offsetY)
        })
    }

    private fun dragListener(params: WindowManager.LayoutParams, root: View) =
        View.OnTouchListener { _, e ->
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
                        params.x = panelParamsX - dx; params.y = panelParamsY + dy
                        try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
                        true
                    } else false
                }
                MotionEvent.ACTION_UP -> { val was = isDragging; isDragging = false; was }
                else -> false
            }
        }

    // ─── Notification ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(capturing: Boolean, a11yMissing: Boolean = false): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, OverlayService::class.java).apply {
            action = if (capturing) ACTION_PAUSE else ACTION_RESUME
        }
        val toggle = PendingIntent.getService(
            this, 2, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val statusText = when {
            a11yMissing -> "⚠ Buka Setelan → Aksesibilitas → aktifkan Touch Offset!"
            capturing   -> "Offset AKTIF (${OffsetState.offsetX}, ${OffsetState.offsetY})"
            else        -> "Offset DIJEDA — ketuk LANJUT untuk aktifkan"
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Touch Offset Overlay")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(
                null, if (capturing) "⏸ JEDA" else "▶ LANJUT", toggle).build())
            .addAction(Notification.Action.Builder(null, "✕ Stop", stop).build())
            .setOngoing(true)
            .build()
    }
}
