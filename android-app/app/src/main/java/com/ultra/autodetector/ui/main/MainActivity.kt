package com.ultra.autodetector.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ultra.autodetector.auth.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.service.DetectionService
import com.ultra.autodetector.service.FloatingWidgetService
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

        binding.btnAccessibilityCard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlayCard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
        binding.btnScreenCaptureCard.setOnClickListener {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }
        binding.btnStartStop.setOnClickListener {
            if (isRunning) stopDetection() else startDetection()
        }
        binding.btnLogout.setOnClickListener {
            auth.logout()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            refreshUi()
        }
    }

    private fun refreshUi() {
        val account = auth.currentUser()
        if (account == null) {
            startActivity(Intent(this, com.ultra.autodetector.ui.auth.AuthActivity::class.java))
            finish()
            return
        }

        val accessibilityEnabled = isAccessibilityEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val captureEnabled = projectionData != null || isRunning

        binding.accessibilityStatus.text = if (accessibilityEnabled) "✓ Granted" else "Grant"
        binding.overlayStatus.text = if (overlayEnabled) "✓ Granted" else "Grant"
        binding.captureStatus.text = if (captureEnabled) "✓ Ready" else "Grant"

        val hasLicense = auth.hasActiveLicense()
        binding.detectorStatus.text = when {
            isRunning -> "Running..."
            hasLicense -> "Ready"
            else -> "License Expired"
        }
        
        val canStart = isRunning || (hasLicense && accessibilityEnabled && overlayEnabled && captureEnabled)
        binding.btnStartStop.isEnabled = canStart
        binding.btnStartStop.text = if (isRunning) "STOP" else "START"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString?.contains(packageName) == true
    }

    private fun startDetection() {
        if (projectionData == null) {
            binding.btnScreenCaptureCard.performClick()
            return
        }
        val data = projectionData!!
        val intent = Intent(this, DetectionService::class.java).apply {
            action = "START"
            putExtra("data", data)
        }
        startForegroundService(intent)
        startService(Intent(this, FloatingWidgetService::class.java))
        isRunning = true
        refreshUi()
    }

    private fun stopDetection() {
        sendBroadcast(Intent("STOP_DETECTION"))
        stopService(Intent(this, FloatingWidgetService::class.java))
        isRunning = false
        refreshUi()
    }
}
