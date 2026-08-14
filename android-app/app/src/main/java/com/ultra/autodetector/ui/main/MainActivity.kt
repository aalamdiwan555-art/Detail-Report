package com.ultra.autodetector.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.service.DetectionService
import com.ultra.autodetector.ui.admin.AdminActivity
import com.ultra.autodetector.ui.logs.LogsActivity
import com.ultra.autodetector.util.LogoTapAccessGesture
import com.ultra.autodetector.util.OverlayManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val auth by lazy { AuthRepository(this) }
    private val settings by lazy { getSharedPreferences("detector_settings", MODE_PRIVATE) }
    private var pendingProjection: Intent? = null
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                startDetection(result.resultCode, data)
            } else {
                binding.tvPermissionSummary.text = "Screen capture permission was cancelled"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUi()
    }

    private fun setupUi() {
        LogoTapAccessGesture.attach(binding.logoAccessTarget) {
            Toast.makeText(this, "Admin opening...", Toast.LENGTH_SHORT).show()
            openAdminPanel()
        }
        binding.btnToggleDetection.setOnClickListener {
            if (DetectionService.isRunning) stopDetection() else beginDetectionSetup()
        }
        binding.btnManageTemplates.setOnClickListener { openAdminPanel() }
        binding.btnViewLogs.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        binding.btnOverlayPermission.setOnClickListener { requestOverlayPermission() }
        binding.btnAccessibilityPermission.setOnClickListener { requestAccessibility() }
        binding.btnNotificationsPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        renderDetectionState()
    }

    private fun beginDetectionSetup() {
        when {
            !hasOverlayPermission() -> requestOverlayPermission()
            !hasAccessibilityPermission() -> requestAccessibility()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
            else -> {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(manager.createScreenCaptureIntent())
            }
        }
    }

    private fun startDetection(resultCode: Int, data: Intent) {
        pendingProjection = data
        val intent = Intent(this, DetectionService::class.java)
            .setAction(DetectionService.ACTION_START)
            .putExtra(DetectionService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(DetectionService.EXTRA_RESULT_DATA, data)
            .putExtra(
                DetectionService.EXTRA_INTERVAL_MS,
                settings.getLong("interval_ms", 500L).coerceIn(100L, 2000L),
            )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            OverlayManager.showOverlay(this, true)
            renderDetectionState()
        }.onFailure {
            Toast.makeText(
                this,
                "Unable to start detection: ${it.message ?: "try again"}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun stopDetection() {
        startService(Intent(this, DetectionService::class.java).setAction(DetectionService.ACTION_STOP))
        OverlayManager.hideOverlay()
        renderDetectionState()
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun hasAccessibilityPermission(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any {
            it.equals("$packageName/${packageName}.service.AutoDetectorService", ignoreCase = true)
        }
    }

    private fun refreshPermissions() {
        val overlay = hasOverlayPermission()
        val accessibility = hasAccessibilityPermission()
        val notification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        binding.tvOverlayStatus.text = if (overlay) "Granted" else "Required"
        binding.tvAccessibilityStatus.text = if (accessibility) "Granted" else "Required"
        binding.tvNotificationStatus.text = if (notification) "Granted" else "Recommended"
        binding.btnOverlayPermission.isEnabled = !overlay
        binding.btnAccessibilityPermission.isEnabled = !accessibility
        binding.btnNotificationsPermission.isEnabled = !notification
        binding.tvPermissionSummary.text = when {
            !overlay -> "Grant overlay access to display the PUSH control."
            !accessibility -> "Enable ULTRA in Accessibility settings to perform gestures."
            else -> "Ready. Screen capture permission is requested when detection starts."
        }
    }

    private fun renderDetectionState() {
        val running = DetectionService.isRunning
        binding.btnToggleDetection.text = if (running) "STOP DETECTION" else "START DETECTION"
        binding.btnToggleDetection.setBackgroundColor(
            getColor(if (running) com.ultra.autodetector.R.color.error else com.ultra.autodetector.R.color.primary),
        )
        binding.tvDetectionState.text = if (running) "ULTRA ACTIVE" else "DETECTOR READY"
        if (running) OverlayManager.updateState(true)
    }

    private fun openAdminPanel() {
        lifecycleScope.launch {
            auth.loginAdmin()
                .onSuccess { startActivity(Intent(this@MainActivity, AdminActivity::class.java)) }
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        it.message ?: "Administrator access failed",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }
}