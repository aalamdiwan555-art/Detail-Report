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
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
            }
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)
        binding.btnAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        binding.btnOverlay.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        binding.btnScreenCapture.setOnClickListener {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }
        binding.btnStartStop.setOnClickListener {
            if (DetectionService.isRunning) stopDetection() else startDetection()
        }
        binding.btnPause.setOnClickListener {
            sendBroadcast(
                Intent(DetectionService.ACTION_PAUSE).setPackage(packageName)
                    .putExtra(DetectionService.EXTRA_PAUSED, !DetectionService.isPaused),
            )
            refreshUi()
        }
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
            refreshUi()
        }
    }

    private fun refreshUi() {
        val account = user ?: return
        val accessibility = isAccessibilityEnabled()
        val overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val capture = projectionData != null || DetectionService.isRunning
        auth.setAccessibilityGranted(accessibility)
        auth.setOverlayGranted(overlay)
        binding.accessibilityStatus.text = if (accessibility) "✓ Granted" else "Grant"
        binding.overlayStatus.text = if (overlay) "✓ Granted" else "Grant"
        binding.captureStatus.text = if (capture) "✓ Granted" else "Grant"
        binding.subscriptionTitle.text = when {
            account.isAdmin -> "Administrator access"
            account.licenseStatus.wireValue == "pending" -> "Pending approval"
            account.licenseStatus.wireValue == "rejected" -> "Access rejected"
            account.hasActiveLicense() -> "Approved subscription"
            else -> "Account expired"
        }
        binding.subscriptionDetails.text = account.remainingLabel()
        binding.btnRenew.visibility = if (account.isAdmin || account.hasActiveLicense()) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnAdmin.visibility = if (account.isAdmin) android.view.View.VISIBLE else android.view.View.GONE
        binding.detectorStatus.text = when {
            DetectionService.isPaused -> "Detector paused"
            DetectionService.isRunning -> "Detector running"
            else -> "Detector ready"
        }
        binding.btnPause.visibility = if (DetectionService.isRunning) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnStartStop.text = if (DetectionService.isRunning) "STOP DETECTION" else "START DETECTION"
        binding.btnStartStop.isEnabled = DetectionService.isRunning || (account.hasActiveLicense() && accessibility && overlay && capture)
        binding.detectorError.visibility = if (!account.hasActiveLicense() && !account.isAdmin) android.view.View.VISIBLE else android.view.View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
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
        refreshUi()
    }

    private fun stopDetection() {
        sendBroadcast(Intent(DetectionService.ACTION_STOP).setPackage(packageName))
        stopService(Intent(this, FloatingWidgetService::class.java))
        refreshUi()
    }
}
