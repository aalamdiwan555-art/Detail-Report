package com.ultra.autodetector.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.ultra.autodetector.R
import com.ultra.autodetector.auth.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.service.DetectionService
import com.ultra.autodetector.service.FloatingWidgetService
import com.ultra.autodetector.ui.auth.AuthActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val auth by lazy { AuthRepository(this) }
    private var projectionData: Intent? = null
    private var isRunning = false

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            projectionData = result.data
            refreshUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tere XMLs me ye 3 IDs hain
        binding.root.findViewById<View>(R.id.btn_accessibility)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.root.findViewById<View>(R.id.btn_overlay)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
        binding.root.findViewById<View>(R.id.btn_screen_capture)?.setOnClickListener {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }

        binding.btnStartStop.setOnClickListener {
            if (isRunning) stopDetection() else startDetection()
        }
        binding.btnLogout.setOnClickListener {
            auth.logout()
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
        binding.btnCloseNotice?.setOnClickListener { binding.noticeCard.visibility = View.GONE }
        binding.btnPause?.setOnClickListener { stopDetection() }
        binding.btnAdmin?.setOnClickListener { startActivity(Intent(this, com.ultra.autodetector.ui.admin.AdminActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { refreshUi() }
    }

    private fun refreshUi() {
        val account = auth.currentUser()
        if (account == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        binding.accountEmail.text = account.email
        binding.avatarText.text = account.email.firstOrNull()?.uppercase() ?: "U"

        val accessibilityEnabled = isAccessibilityEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val captureEnabled = projectionData != null || isRunning

        // Status TextViews update - tere XML ke IDs
        binding.root.findViewById<TextView>(R.id.accessibility_status)?.text = if (accessibilityEnabled) "✓ Granted" else "Not ready"
        binding.root.findViewById<TextView>(R.id.overlay_status)?.text = if (overlayEnabled) "✓ Granted" else "Not ready"
        binding.root.findViewById<TextView>(R.id.capture_status)?.text = if (captureEnabled) "✓ Ready" else "Not ready"

        binding.root.findViewById<MaterialButton>(R.id.btn_accessibility)?.text = if (accessibilityEnabled) "Granted" else "Grant"
        binding.root.findViewById<MaterialButton>(R.id.btn_overlay)?.text = if (overlayEnabled) "Granted" else "Grant"
        binding.root.findViewById<MaterialButton>(R.id.btn_screen_capture)?.text = if (captureEnabled) "Ready" else "Grant"

        val hasLicense = auth.hasActiveLicense()
        binding.detectorStatus.text = when {
            isRunning -> "● Running"
            hasLicense -> "Ready to Start"
            else -> "License Expired"
        }

        val canStart = isRunning || (hasLicense && accessibilityEnabled && overlayEnabled)
        binding.btnStartStop.isEnabled = canStart
        binding.btnStartStop.text = if (isRunning) "STOP DETECTION" else "START DETECTION"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString?.contains(packageName) == true
    }

    private fun startDetection() {
        if (projectionData == null) {
            binding.root.findViewById<View>(R.id.btn_screen_capture)?.performClick()
            return
        }
        val data = projectionData!!
        startForegroundService(Intent(this, DetectionService::class.java).apply { action = "START"; putExtra("data", data) })
        startService(Intent(this, FloatingWidgetService::class.java))
        isRunning = true
        binding.consoleText.text = "> Detection initialized..."
        refreshUi()
    }

    private fun stopDetection() {
        sendBroadcast(Intent("STOP_DETECTION"))
        stopService(Intent(this, FloatingWidgetService::class.java))
        isRunning = false
        binding.consoleText.text = "> Local console ready"
        refreshUi()
    }
}
