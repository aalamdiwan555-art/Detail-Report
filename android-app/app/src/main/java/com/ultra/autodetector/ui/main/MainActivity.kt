package com.ultra.autodetector.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.UserEntity
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.service.AutoClickService
import com.ultra.autodetector.service.DetectionService
import com.ultra.autodetector.service.FloatingWidgetService
import com.ultra.autodetector.ui.admin.AdminActivity
import com.ultra.autodetector.ui.auth.AuthActivity
import com.ultra.autodetector.util.TelegramHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: AuthRepository
    private var user: User? = null
    private var projectionCode = Activity.RESULT_CANCELED
    private var projectionData: Intent? = null

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                projectionCode = result.resultCode
                projectionData = result.data
                refreshUi()
            } else {
                appendLog("Screen capture permission was not granted.")
            }
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }
        binding.btnScreenCapture.setOnClickListener {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }
        binding.btnStartStop.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (DetectionService.isRunning) stopDetection() else startDetection()
        }
        binding.btnPause.setOnClickListener {
            sendBroadcast(
                Intent(DetectionService.ACTION_PAUSE).setPackage(packageName)
                    .putExtra(DetectionService.EXTRA_PAUSED, !DetectionService.isPaused),
            )
            refreshUi()
        }
        binding.btnCloseNotice.setOnClickListener { binding.noticeCard.visibility = View.GONE }
        binding.btnRenew.setOnClickListener { user?.let { TelegramHelper.openRenewalChat(this, it) } }
        binding.btnAdmin.setOnClickListener { startActivity(Intent(this, AdminActivity::class.java)) }
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                auth.logout()
                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            user = auth.currentUser()
            if (user == null) {
                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                finish()
            } else {
                refreshUi()
            }
        }
    }

    private fun refreshUi() {
        val account = user ?: return
        val accessibility = isAccessibilityEnabled()
        val overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val capture = projectionData != null || DetectionService.isRunning
        auth.setAccessibilityGranted(accessibility)
        auth.setOverlayGranted(overlay)

        binding.avatarText.text = account.email.firstOrNull()?.uppercase() ?: "U"
        binding.accountEmail.text = account.email
        binding.accessibilityStatus.text = if (accessibility) "✓ Granted" else "Grant access"
        binding.overlayStatus.text = if (overlay) "✓ Granted" else "Grant access"
        binding.captureStatus.text = if (capture) "✓ Ready" else "Grant access"
        binding.subscriptionTitle.text = when {
            account.isAdmin -> "Administrator access"
            account.licenseStatus == UserEntity.STATUS_PENDING -> "Pending approval"
            account.licenseStatus == UserEntity.STATUS_REJECTED -> "Access rejected"
            account.hasActiveLicense() -> "Approved subscription"
            else -> "Account expired"
        }
        binding.subscriptionDetails.text = account.remainingLabel()
        val days = if (account.isAdmin) 100 else
            ((account.expiryDate - System.currentTimeMillis()) / 86_400_000L).coerceIn(0L, 100L).toInt()
        binding.subscriptionProgress.progress = days
        binding.btnRenew.visibility = if (account.isAdmin || account.hasActiveLicense()) View.GONE else View.VISIBLE
        binding.btnAdmin.visibility = if (account.isAdmin) View.VISIBLE else View.GONE
        binding.detectorStatus.text = when {
            DetectionService.isPaused -> "Detector paused"
            DetectionService.isRunning -> "Detector running"
            else -> "Detector ready"
        }
        binding.btnPause.visibility = if (DetectionService.isRunning) View.VISIBLE else View.GONE
        binding.btnStartStop.text = if (DetectionService.isRunning) "STOP DETECTION" else "START DETECTION"
        val canStart = account.isAdmin || account.hasActiveLicense()
        binding.btnStartStop.isEnabled = DetectionService.isRunning || (canStart && accessibility && overlay && capture)
        binding.detectorError.visibility = if (!canStart) View.VISIBLE else View.GONE
        binding.detectorError.text = if (!canStart) "Your account needs approval before detection can start." else ""
        binding.permissionHint.text = if (accessibility && overlay && capture) {
            "All permissions are ready."
        } else {
            "Grant all three permissions to enable detection."
        }
        lifecycleScope.launch {
            val notice = AppDatabase.getInstance(this@MainActivity).noticeDao().getLatest()
            binding.noticeCard.visibility = if (notice == null) View.GONE else View.VISIBLE
            binding.noticeText.text = notice?.message.orEmpty()
        }
        appendLog(if (DetectionService.isRunning) "Detector state: running" else "Detector state: ready")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val component = "$packageName/${AutoClickService::class.java.name}"
        return manager.isEnabled && services.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun startDetection() {
        if (projectionData == null) {
            binding.btnScreenCapture.performClick()
            return
        }
        val data = projectionData!!
        val intent = Intent(this, DetectionService::class.java).apply {
            action = DetectionService.ACTION_START
            putExtra(DetectionService.EXTRA_RESULT_CODE, projectionCode)
            putExtra(DetectionService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
        startService(Intent(this, FloatingWidgetService::class.java))
        appendLog("Detection started.")
        refreshUi()
    }

    private fun stopDetection() {
        sendBroadcast(Intent(DetectionService.ACTION_STOP).setPackage(packageName))
        stopService(Intent(this, FloatingWidgetService::class.java))
        appendLog("Detection stopped.")
        refreshUi()
    }

    private fun appendLog(message: String) {
        val current = binding.consoleText.text?.toString().orEmpty()
        val lines = (current.lines() + "› $message").takeLast(7)
        binding.consoleText.text = lines.joinToString("\n")
    }
}