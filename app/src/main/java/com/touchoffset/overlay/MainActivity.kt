package com.touchoffset.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.touchoffset.overlay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_SERVICE_STOPPED = "com.touchoffset.overlay.SERVICE_STOPPED"
        const val ACTION_OFFSET_CHANGED  = "com.touchoffset.overlay.OFFSET_CHANGED"
        private const val REQ_OVERLAY = 1001
    }

    private lateinit var binding: ActivityMainBinding

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SERVICE_STOPPED -> updateUI()
                ACTION_OFFSET_CHANGED -> {
                    val x = intent.getIntExtra("offsetX", 0)
                    val y = intent.getIntExtra("offsetY", 0)
                    binding.sliderX.value = x.toFloat()
                    binding.sliderY.value = y.toFloat()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSliders()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        val filter = IntentFilter().apply {
            addAction(ACTION_SERVICE_STOPPED)
            addAction(ACTION_OFFSET_CHANGED)
        }
        registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun setupSliders() {
        binding.sliderX.value = OffsetState.offsetX.toFloat().coerceIn(-300f, 300f)
        binding.sliderY.value = OffsetState.offsetY.toFloat().coerceIn(-300f, 300f)
        binding.tvOffsetX.text = "${OffsetState.offsetX} px"
        binding.tvOffsetY.text = "${OffsetState.offsetY} px"

        binding.sliderX.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            OffsetState.offsetX = v
            binding.tvOffsetX.text = "$v px"
        }
        binding.sliderY.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            OffsetState.offsetY = v
            binding.tvOffsetY.text = "$v px"
        }

        binding.btnResetOffsets.setOnClickListener {
            binding.sliderX.value = 0f
            binding.sliderY.value = 0f
            OffsetState.offsetX = 0
            OffsetState.offsetY = 0
            binding.tvOffsetX.text = "0 px"
            binding.tvOffsetY.text = "0 px"
        }
    }

    private fun setupButtons() {
        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnGrantAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnToggleService.setOnClickListener { toggleService() }
    }

    // ─── Service toggle ───────────────────────────────────────────────────────

    private fun toggleService() {
        if (OffsetState.isServiceRunning) {
            stopOverlayService()
        } else {
            if (!hasOverlayPermission()) {
                showDialog(
                    "Overlay Permission Required",
                    getString(R.string.permission_overlay_required)
                ) { requestOverlayPermission() }
                return
            }
            if (!isAccessibilityEnabled()) {
                showDialog(
                    "Accessibility Service Required",
                    getString(R.string.accessibility_required)
                ) { openAccessibilitySettings() }
                return
            }
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
        updateUI()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        OffsetState.isServiceRunning = false
        updateUI()
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return list.any { it.id.contains(packageName) }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQ_OVERLAY)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) updateUI()
    }

    // ─── UI state ─────────────────────────────────────────────────────────────

    private fun updateUI() {
        val overlayOk = hasOverlayPermission()
        val a11yOk = isAccessibilityEnabled()
        val running = OffsetState.isServiceRunning

        // Permission statuses
        binding.tvOverlayStatus.apply {
            text = if (overlayOk) "✓ Overlay Permission" else "Overlay Permission"
            setTextColor(if (overlayOk) getColor(R.color.active_green) else getColor(R.color.text_primary))
        }
        binding.btnGrantOverlay.apply {
            text = if (overlayOk) "Granted" else "Grant"
            isEnabled = !overlayOk
        }

        binding.tvAccessibilityStatus.apply {
            text = if (a11yOk) "✓ Accessibility Service" else "Accessibility Service"
            setTextColor(if (a11yOk) getColor(R.color.active_green) else getColor(R.color.text_primary))
        }
        binding.btnGrantAccessibility.apply {
            text = if (a11yOk) "Enabled" else "Enable"
            isEnabled = !a11yOk
        }

        // Service toggle button
        binding.btnToggleService.apply {
            text = if (running) "Stop Overlay" else "Start Overlay"
            setBackgroundColor(
                if (running) getColor(R.color.inactive_gray)
                else getColor(R.color.accent)
            )
        }

        // Status label
        binding.tvServiceStatus.apply {
            text = if (running) "● Active" else "○ Inactive"
            setTextColor(
                if (running) getColor(R.color.active_green)
                else getColor(R.color.inactive_gray)
            )
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun showDialog(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
