package com.ultra.autodetector.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ultra.autodetector.R
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.service.DetectionService
import com.ultra.autodetector.ui.admin.AdminActivity
import com.ultra.autodetector.ui.auth.AuthActivity
import com.ultra.autodetector.util.LogoTapAccessGesture
import com.ultra.autodetector.util.OverlayManager
import com.ultra.autodetector.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * The intentionally small user surface. Templates and logs are admin-only and
 * are never inflated or exposed from this screen.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val auth by lazy { AuthRepository(this) }
    private val settings by lazy { getSharedPreferences("detector_settings", MODE_PRIVATE) }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                startDetection(result.resultCode, data)
            } else {
                Toast.makeText(this, "Screen capture permission is required to start.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        LogoTapAccessGesture.attach(binding.logoAccessTarget) { openAdminPanel() }
        binding.btnToggleDetection.setOnClickListener {
            if (DetectionService.isRunning) stopDetection() else beginDetectionSetup()
        }
        binding.btnLogout.setOnClickListener { logout() }
        loadAccountDetails()
    }

    override fun onResume() {
        super.onResume()
        renderDetectionState()
    }

    private fun loadAccountDetails() {
        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) {
                openLogin()
            } else {
                renderAccount(user)
            }
        }
    }

    private fun renderAccount(user: User) {
        binding.tvUsername.text = user.email.substringBefore("@").ifBlank { "Ultra User" }
        binding.tvEmail.text = user.email
        binding.tvPlan.text = if (user.isAdmin) "Administrator" else "Ultra AutoDetector"
        binding.tvStatus.text = if (user.isAdmin) "Admin access enabled" else auth.remainingLabel(user)
    }

    private fun beginDetectionSetup() {
        when {
            !PermissionHelper.hasOverlayPermission(this) -> showPermissionDialog(
                title = "Allow overlay access",
                message = "ULTRA needs permission to show the PUSH control above other apps.",
                actionText = "OPEN SETTINGS",
                onAction = { startActivity(PermissionHelper.overlayIntent(this)) },
            )
            !PermissionHelper.hasAccessibilityPermission(this) -> showPermissionDialog(
                title = "Enable ULTRA accessibility",
                message = "Accessibility access lets ULTRA perform the configured click and swipe actions.",
                actionText = "OPEN SETTINGS",
                onAction = { startActivity(PermissionHelper.accessibilityIntent()) },
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionHelper.hasNotificationPermission(this) -> {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
            else -> {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(manager.createScreenCaptureIntent())
            }
        }
    }

    private fun showPermissionDialog(
        title: String,
        message: String,
        actionText: String,
        onAction: () -> Unit,
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("NOT NOW", null)
            .setPositiveButton(actionText) { _, _ -> onAction() }
            .show()
    }

    private fun startDetection(resultCode: Int, data: Intent) {
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
                "Unable to start detection. Please try again.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun stopDetection() {
        startService(Intent(this, DetectionService::class.java).setAction(DetectionService.ACTION_STOP))
        OverlayManager.hideOverlay()
        renderDetectionState()
    }

    private fun renderDetectionState() {
        val running = DetectionService.isRunning
        binding.btnToggleDetection.text = if (running) "STOP DETECTION" else "START DETECTION"
        binding.btnToggleDetection.setBackgroundResource(
            if (running) R.drawable.bg_button_stop else R.drawable.bg_button_start,
        )
    }

    private fun logout() {
        if (DetectionService.isRunning) stopDetection()
        lifecycleScope.launch {
            auth.logout()
            openLogin()
        }
    }

    private fun openLogin() {
        startActivity(
            Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        finish()
    }

    private fun openAdminPanel() {
        lifecycleScope.launch {
            auth.loginAdmin()
                .onSuccess {
                    startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                }
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        "Administrator access is not available for this account.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
    }
}