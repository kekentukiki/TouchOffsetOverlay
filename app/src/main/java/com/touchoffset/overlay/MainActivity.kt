package com.touchoffset.overlay

import android.accessibilityservice.AccessibilityServiceInfo
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
                    // update sliders without triggering listeners
                    binding.sliderX.value = x.toFloat().coerceIn(-400f, 400f)
                    binding.sliderY.value = y.toFloat().coerceIn(-400f, 400f)
                    binding.tvOffsetX.text = "$x px"
                    binding.tvOffsetY.text = "$y px"
                    syncSketch()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
    }

    // ─── Sliders ──────────────────────────────────────────────────────────────

    private fun setupSliders() {
        binding.sliderX.value = OffsetState.offsetX.toFloat().coerceIn(-400f, 400f)
        binding.sliderY.value = OffsetState.offsetY.toFloat().coerceIn(-400f, 400f)
        binding.tvOffsetX.text = "${OffsetState.offsetX} px"
        binding.tvOffsetY.text = "${OffsetState.offsetY} px"

        binding.sliderX.addOnChangeListener { _, value, _ ->
            OffsetState.offsetX = value.toInt()
            binding.tvOffsetX.text = "${value.toInt()} px"
            syncSketch()
        }
        binding.sliderY.addOnChangeListener { _, value, _ ->
            OffsetState.offsetY = value.toInt()
            binding.tvOffsetY.text = "${value.toInt()} px"
            syncSketch()
        }
        binding.btnResetOffsets.setOnClickListener {
            binding.sliderX.value = 0f
            binding.sliderY.value = 0f
            OffsetState.offsetX = 0; OffsetState.offsetY = 0
            binding.tvOffsetX.text = "0 px"; binding.tvOffsetY.text = "0 px"
            syncSketch()
        }
    }

    // ─── Sketch sync ──────────────────────────────────────────────────────────

    private fun syncSketch() {
        binding.testSketch.shiftX = OffsetState.offsetX
        binding.testSketch.shiftY = OffsetState.offsetY
    }

    // ─── Buttons ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnGrantAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnToggleService.setOnClickListener { toggleService() }
        binding.btnClearSketch.setOnClickListener { binding.testSketch.clear() }
    }

    // ─── Service ──────────────────────────────────────────────────────────────

    private fun toggleService() {
        if (OffsetState.isServiceRunning) {
            stopOverlayService()
        } else {
            if (!hasOverlayPermission()) {
                showDialog("Overlay Permission Required",
                    getString(R.string.permission_overlay_required)) { requestOverlayPermission() }
                return
            }
            if (!isAccessibilityEnabled()) {
                showDialog("Accessibility Service Required",
                    getString(R.string.accessibility_required)) { openAccessibilitySettings() }
                return
            }
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, OverlayService::class.java))
        updateUI()
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
        OffsetState.isServiceRunning = false
        updateUI()
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id.contains(packageName) }
    }

    private fun requestOverlayPermission() {
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            REQ_OVERLAY
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) updateUI()
    }

    // ─── UI state ─────────────────────────────────────────────────────────────

    private fun updateUI() {
        val overlayOk = hasOverlayPermission()
        val a11yOk = isAccessibilityEnabled()
        val running = OffsetState.isServiceRunning

        binding.tvOverlayStatus.text = if (overlayOk) "✓ Overlay Permission" else "Overlay Permission"
        binding.tvOverlayStatus.setTextColor(
            if (overlayOk) getColor(R.color.active_green) else getColor(R.color.text_primary))
        binding.btnGrantOverlay.text = if (overlayOk) "Granted" else "Grant"
        binding.btnGrantOverlay.isEnabled = !overlayOk

        binding.tvAccessibilityStatus.text = if (a11yOk) "✓ Accessibility Service" else "Accessibility Service"
        binding.tvAccessibilityStatus.setTextColor(
            if (a11yOk) getColor(R.color.active_green) else getColor(R.color.text_primary))
        binding.btnGrantAccessibility.text = if (a11yOk) "Enabled" else "Enable"
        binding.btnGrantAccessibility.isEnabled = !a11yOk

        binding.btnToggleService.text = if (running) "Stop Overlay" else "Start Overlay"
        binding.btnToggleService.setBackgroundColor(
            if (running) getColor(R.color.inactive_gray) else getColor(R.color.accent))

        binding.tvServiceStatus.text = if (running) "● Active" else "○ Inactive"
        binding.tvServiceStatus.setTextColor(
            if (running) getColor(R.color.active_green) else getColor(R.color.inactive_gray))

        syncSketch()
    }

    private fun showDialog(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title).setMessage(message)
            .setPositiveButton("Open Settings") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null).show()
    }
}
