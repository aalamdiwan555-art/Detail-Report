package com.ultra.autodetector.ui.main

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ultra.autodetector.R
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.data.repository.TemplateRepository
import com.ultra.autodetector.data.local.EncryptedPrefsManager
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.service.AutoClickService
import com.ultra.autodetector.service.DetectionService
import com.ultra.autodetector.service.FloatingWidgetService
import com.ultra.autodetector.ui.auth.AuthActivity
import com.ultra.autodetector.util.BackgroundPermissionHelper
import com.ultra.autodetector.util.Constants
import com.ultra.autodetector.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val auth by lazy { AuthRepository(this) }
    private var projectionData: Intent? = null
    private var projectionResultCode: Int = Activity.RESULT_CANCELED

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                projectionResultCode = result.resultCode
                projectionData = result.data
                EncryptedPrefsManager(this@MainActivity).apply {
                    setMediaProjectionGranted(true)
                    saveMediaProjectionData(result.resultCode)
                }
            }
            refreshUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        findViewById<View>(R.id.btn_accessibility).setOnClickListener {
            startActivity(BackgroundPermissionHelper.accessibilityIntent())
        }
        findViewById<View>(R.id.btn_overlay).setOnClickListener {
            startActivity(BackgroundPermissionHelper.overlayIntent(this))
        }
        findViewById<View>(R.id.btn_screen_capture).setOnClickListener {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }
        findViewById<View>(R.id.btn_battery).setOnClickListener {
            runCatching { startActivity(BackgroundPermissionHelper.batteryOptimizationIntent(this)) }
                .onFailure { openAppDetails() }
        }
        findViewById<View>(R.id.btn_notifications).setOnClickListener {
            requestNotificationPermission()
        }
        findViewById<View>(R.id.btn_autostart).setOnClickListener {
            BackgroundPermissionHelper.openAutoStartSettings(this)
        }

        binding.btnStartStop.setOnClickListener {
            if (DetectionService.isRunning) stopDetection() else startDetection()
        }
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                auth.logout()
                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                finish()
            }
        }
        binding.btnCloseNotice.setOnClickListener { binding.noticeCard.visibility = View.GONE }
        binding.btnPause.setOnClickListener { stopDetection() }
        binding.btnAdmin.setOnClickListener {
            startActivity(Intent(this, com.ultra.autodetector.ui.admin.AdminActivity::class.java))
        }

        // Notifications are a runtime permission on Android 13+ and are needed
        // for the persistent foreground-service notification to be visible.
        window.decorView.post { requestNotificationPermission() }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(Constants.ACTION_TEMPLATE_UPDATED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(templateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(templateReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(templateReceiver) }
        super.onStop()
    }

    private fun refreshUi() {
        lifecycleScope.launch {
            val account = auth.currentUser()
            if (account == null) {
                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                finish()
                return@launch
            }

            binding.accountEmail.text = account.email
            binding.avatarText.text = account.email.firstOrNull()?.uppercase() ?: "U"
            val activeTemplates = TemplateRepository(this@MainActivity).getActiveTemplates()
            binding.activeTemplatesList.removeAllViews()
            activeTemplates.forEach { template ->
                binding.activeTemplatesList.addView(
                    TextView(this@MainActivity).apply {
                        text = "● ${template.name}"
                        setTextColor(getColor(R.color.primary))
                        textSize = 13f
                        setPadding(0, 5, 0, 5)
                        contentDescription = "Active template ${template.name}"
                    },
                )
            }
            binding.activeTemplatesEmpty.visibility =
                if (activeTemplates.isEmpty()) View.VISIBLE else View.GONE
            binding.activeTemplatesStatus.text =
                if (DetectionService.isRunning) {
                    "Scanning... Found ${activeTemplates.size} templates active"
                } else {
                    "Found ${activeTemplates.size} templates active"
                }

            val permissions = BackgroundPermissionHelper.status(this@MainActivity)
            updatePermission(
                R.id.accessibility_status,
                R.id.btn_accessibility,
                permissions.accessibility,
                "Accessibility",
            )
            updatePermission(
                R.id.overlay_status,
                R.id.btn_overlay,
                permissions.overlay,
                "Overlay",
            )
            updatePermission(
                R.id.battery_status,
                R.id.btn_battery,
                permissions.batteryOptimization,
                "Battery optimization",
            )
            updatePermission(
                R.id.notifications_status,
                R.id.btn_notifications,
                permissions.notifications,
                "Notifications",
            )
            val autoStartStatus = findViewById<TextView>(R.id.autostart_status)
            autoStartStatus.text = if (permissions.autoStart) "Optional" else "Review"
            autoStartStatus.setTextColor(
                getColor(if (permissions.autoStart) R.color.primary else R.color.muted),
            )

            val captureEnabled = projectionData != null || DetectionService.isRunning
            findViewById<TextView>(R.id.capture_status).text =
                if (captureEnabled) "✓ Ready" else "Not ready"
            findViewById<MaterialButton>(R.id.btn_screen_capture).text =
                if (captureEnabled) "Ready" else "Grant"

            val hasLicense = account.isAdmin || account.hasActiveLicense()
            binding.detectorStatus.text = when {
                DetectionService.isRunning -> "● Running"
                hasLicense -> "Ready to Start"
                else -> "License Expired"
            }
            binding.permissionHint.text = if (permissions.mainPermissionsGranted) {
                "Core background permissions granted."
            } else {
                "For autoclicker to work in background, please allow all permissions."
            }

            // The detector needs accessibility, overlay, and battery exemption.
            // Screen capture is requested after the user presses Start.
            binding.btnStartStop.isEnabled =
                DetectionService.isRunning ||
                    (hasLicense && permissions.mainPermissionsGranted && permissions.notifications)
            binding.btnStartStop.text =
                if (DetectionService.isRunning) "STOP DETECTION" else "START DETECTION"
            binding.btnAdmin.visibility = if (account.isAdmin) View.VISIBLE else View.GONE
        }
    }

    private fun updatePermission(statusId: Int, buttonId: Int, granted: Boolean, label: String) {
        val status = findViewById<TextView>(statusId)
        val button = findViewById<MaterialButton>(buttonId)
        status.text = if (granted) "✓ Granted" else "Not ready"
        status.setTextColor(getColor(if (granted) R.color.primary else R.color.error))
        button.text = if (granted) "Granted" else "Grant"
        button.isEnabled = !granted
        button.contentDescription = if (granted) "$label granted" else "Grant $label"
    }

    private fun startDetection() {
        val permissions = BackgroundPermissionHelper.status(this)
        if (!permissions.mainPermissionsGranted || !permissions.notifications) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permissions required")
                .setMessage("Accessibility, overlay, battery, and notification permissions are required first.")
                .setPositiveButton("OPEN ACCESSIBILITY") { _, _ ->
                    startActivity(BackgroundPermissionHelper.accessibilityIntent())
                }
                .setNegativeButton("CANCEL", null)
                .show()
            return
        }
        val data = projectionData
        if (data == null) {
            findViewById<View>(R.id.btn_screen_capture).performClick()
            return
        }
        if (!PermissionHelper.allGranted(this, mediaProjectionReady = true)) return

        val intent = Intent(this, DetectionService::class.java)
            .setAction(DetectionService.ACTION_START)
            .putExtra(DetectionService.EXTRA_RESULT_CODE, projectionResultCode)
            .putExtra(DetectionService.EXTRA_RESULT_DATA, data)
        ContextCompat.startForegroundService(this, intent)
        ContextCompat.startForegroundService(this, Intent(this, FloatingWidgetService::class.java))
        refreshUi()
    }

    private fun stopDetection() {
        sendBroadcast(Intent(DetectionService.ACTION_STOP).setPackage(packageName))
        sendBroadcast(Intent(AutoClickService.ACTION_STOP_CLICKING).setPackage(packageName))
        stopService(Intent(this, DetectionService::class.java))
        stopService(Intent(this, FloatingWidgetService::class.java))
        binding.consoleText.text = "> Local console ready"
        refreshUi()
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !BackgroundPermissionHelper.areNotificationsEnabled(this)
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS,
            )
        }
    }

    private val templateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_TEMPLATE_UPDATED) refreshUi()
        }
    }

    private fun openAppDetails() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName"),
            ),
        )
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 7001
    }
}