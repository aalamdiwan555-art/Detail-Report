package com.ultra.autodetector.ui.main

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ultra.autodetector.R
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.service.DetectionService
import com.ultra.autodetector.ui.admin.AdminActivity
import com.ultra.autodetector.ui.auth.AuthActivity
import com.ultra.autodetector.util.LogoTapAccessGesture
import com.ultra.autodetector.util.OverlayManager
import com.ultra.autodetector.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var authRepo: AuthRepository
    private var isDetecting = false

    private val settings by lazy {
        getSharedPreferences("detector_settings", MODE_PRIVATE)
    }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                startDetection(result.resultCode, data)
            } else {
                OverlayManager.hideOverlay()
                isDetecting = false
                updateButton()
                Toast.makeText(
                    this,
                    "Screen capture permission is required to start.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authRepo = AuthRepository(this)

        OverlayManager.hideOverlay()
        setupUserInfo()
        setupClicks()
        setupAdminAccess()
        updateButton()
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionHelper.hasAllPermissions(this)) {
            stopDetectionService()
            OverlayManager.hideOverlay()
            isDetecting = false
            updateButton()
        } else {
            binding.btnToggleDetection.isEnabled = true
            isDetecting = DetectionService.isRunning
            updateButton()
        }
    }

    private fun setupUserInfo() {
        lifecycleScope.launch {
            val user = authRepo.currentUser()
            if (user == null) {
                goToLogin()
                return@launch
            }

            binding.tvUsername.text = user.email.substringBefore("@").ifBlank { "Ultra User" }
            binding.tvEmail.text = user.email
            binding.tvStatus.text =
                if (user.isApproved && user.licenseStatus == "approved") {
                    "Approved"
                } else {
                    "Awaiting administrator approval"
                }
            binding.tvPlan.text = if (user.isAdmin) "Administrator" else "Ultra AutoDetector"
        }
    }

    private fun setupClicks() {
        binding.btnToggleDetection.setOnClickListener {
            when {
                !PermissionHelper.hasOverlayPermission(this) -> {
                    showPermissionGuideDialog(isOverlay = true)
                }
                !PermissionHelper.hasAccessibilityPermission(this) -> {
                    showPermissionGuideDialog(isOverlay = false)
                }
                else -> toggleDetection()
            }
        }

        binding.btnLogout.setOnClickListener {
            OverlayManager.hideOverlay()
            stopDetectionService()
            lifecycleScope.launch {
                authRepo.logout()
                goToLogin()
            }
        }
    }

    private fun setupAdminAccess() {
        LogoTapAccessGesture.attach(
            view = binding.logoAccessTarget,
            onHoldStart = {
                Toast.makeText(this, "Hold 6 sec for admin...", Toast.LENGTH_SHORT).show()
            },
            onTrigger = {
                vibrateAdminAccess()
                Toast.makeText(this, "Admin opening...", Toast.LENGTH_SHORT).show()
                openAdminPanel()
            },
        )
    }

    private fun toggleDetection() {
        if (isDetecting) {
            OverlayManager.hideOverlay()
            stopDetectionService()
            isDetecting = false
            Toast.makeText(this, "Detection stopped", Toast.LENGTH_SHORT).show()
            updateButton()
            return
        }

        if (!PermissionHelper.hasAllPermissions(this)) {
            OverlayManager.hideOverlay()
            showPermissionGuideDialog(
                isOverlay = !PermissionHelper.hasOverlayPermission(this),
            )
            return
        }

        // MediaProjection is a separate Android consent step. The overlay is
        // intentionally not shown until this callback returns successfully.
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startDetection(resultCode: Int, data: Intent) {
        if (!PermissionHelper.hasAllPermissions(this)) {
            OverlayManager.hideOverlay()
            isDetecting = false
            updateButton()
            return
        }

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
            isDetecting = true
            OverlayManager.showOverlay(this, true)
            updateButton()
            Toast.makeText(this, "Detection started", Toast.LENGTH_SHORT).show()
        }.onFailure {
            OverlayManager.hideOverlay()
            isDetecting = false
            updateButton()
            Toast.makeText(this, "Unable to start detection", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDetectionService() {
        startService(
            Intent(this, DetectionService::class.java)
                .setAction(DetectionService.ACTION_STOP),
        )
    }

    private fun updateButton() {
        binding.btnToggleDetection.text = if (isDetecting) {
            "STOP DETECTION"
        } else {
            "START DETECTION"
        }
        binding.btnToggleDetection.setBackgroundResource(
            if (isDetecting) R.drawable.bg_button_stop else R.drawable.bg_button_start,
        )
    }

    private fun showPermissionGuideDialog(isOverlay: Boolean) {
        // This must happen before the dialog and before the settings activity.
        OverlayManager.hideOverlay()
        AlertDialog.Builder(this)
            .setTitle(
                if (isOverlay) {
                    "Overlay Permission Required"
                } else {
                    "Accessibility Permission Required"
                },
            )
            .setMessage(
                if (isOverlay) {
                    "Allow ULTRA to display over other apps."
                } else {
                    "IMPORTANT:\n1. Turn OFF Assistive Ball.\n2. Turn OFF other AutoClickers.\n3. Then enable ULTRA."
                },
            )
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("OPEN SETTINGS") { _, _ ->
                if (isOverlay) {
                    requestOverlayPermission()
                } else {
                    requestAccessibilityPermission()
                }
            }
            .show()
    }

    private fun requestAccessibilityPermission() {
        OverlayManager.hideOverlay()
        Handler(Looper.getMainLooper()).postDelayed(
            { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            SETTINGS_DELAY_MS,
        )
    }

    private fun requestOverlayPermission() {
        OverlayManager.hideOverlay()
        Handler(Looper.getMainLooper()).postDelayed(
            {
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName"),
                    ),
                    REQUEST_OVERLAY_PERMISSION,
                )
            },
            SETTINGS_DELAY_MS,
        )
    }

    private fun vibrateAdminAccess() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200L)
        }
    }

    private fun openAdminPanel() {
        startActivity(Intent(this, AdminActivity::class.java))
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        finish()
    }

    companion object {
        private const val SETTINGS_DELAY_MS = 500L
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }
}