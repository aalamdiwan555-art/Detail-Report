package com.ultra.autodetector.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ultra.autodetector.R
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.detector.BuiltInTemplateManager
import com.ultra.autodetector.service.AutoDetectorService
import com.ultra.autodetector.service.FloatingOverlayService
import com.ultra.autodetector.ui.auth.AuthActivity
import com.ultra.autodetector.util.BackgroundPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val auth by lazy { AuthRepository(this) }
    private val templateManager by lazy { BuiltInTemplateManager(this) }
    @Volatile private var templatesReady = false
    private var projectionData: Intent? = null
    private var projectionResultCode = Activity.RESULT_CANCELED

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                projectionResultCode = result.resultCode
                projectionData = result.data
                startDetector()
            } else {
                binding.detectorStatus.text = getString(R.string.screen_capture_cancelled)
            }
            refreshUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartDetection.setOnClickListener { requestPermissionsAndStart() }
        binding.btnStopDetection.setOnClickListener { stopDetector() }
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                auth.logout()
                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                finish()
            }
        }
        binding.btnAdmin.setOnClickListener {
            startActivity(Intent(this, com.ultra.autodetector.ui.admin.AdminActivity::class.java))
        }
        binding.btnAccessibility.setOnClickListener {
            startActivity(BackgroundPermissionHelper.accessibilityIntent())
        }
        binding.btnOverlay.setOnClickListener {
            startActivity(BackgroundPermissionHelper.overlayIntent(this))
        }
        binding.btnNotifications.setOnClickListener { requestNotificationPermission() }
        binding.showOverlaySwitch.setOnCheckedChangeListener { _, checked ->
            if (AutoDetectorService.isRunning && checked) startOverlay()
            if (AutoDetectorService.isRunning && !checked) stopService(
                Intent(this, FloatingOverlayService::class.java),
            )
        }
        refreshUi()
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                (application as? com.ultra.autodetector.UltraAutoDetectorApp)
                    ?.ensureOpenCvLoaded()
                templateManager.onCreate()
            }
            templatesReady = true
            refreshUi()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) {
                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                finish()
                return@launch
            }
            binding.accountEmail.text = user.email
            binding.accountStatus.text = if (user.isAdmin) {
                "Administrator"
            } else {
                user.licenseStatus.uppercase()
            }
            binding.btnAdmin.visibility = if (user.isAdmin) View.VISIBLE else View.GONE
            if (templatesReady) renderTemplates()
            renderPermissions()
            renderStatus()
        }
    }

    override fun onDestroy() {
        if (templatesReady) templateManager.close()
        super.onDestroy()
    }

    private fun renderTemplates() {
        binding.templateGrid.removeAllViews()
        val items = templateManager.getAllTemplates()
        binding.templateCount.text = "${items.count { it.isActive }} templates active"
        items.forEach { template ->
            val card = layoutInflater.inflate(
                R.layout.item_builtin_template,
                binding.templateGrid,
                false,
            )
            card.findViewById<TextView>(R.id.template_name).text = template.name
            card.findViewById<android.widget.ImageView>(R.id.template_image)
                .setImageBitmap(template.bitmap)
            card.findViewById<TextView>(R.id.template_active).text =
                if (template.isActive) "Active" else "Inactive"
            val seekBar = card.findViewById<SeekBar>(R.id.template_threshold)
            val thresholdText = card.findViewById<TextView>(R.id.template_threshold_text)
            val initial = templateManager.thresholdFor(template.id)
            seekBar.max = 25
            seekBar.progress = ((initial - 0.70f) * 100).roundToInt().coerceIn(0, 25)
            thresholdText.text = "Threshold %.2f".format(initial)
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = 0.70f + progress / 100f
                    thresholdText.text = "Threshold %.2f".format(value)
                    if (fromUser) templateManager.setThreshold(template.id, value)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
            binding.templateGrid.addView(card)
        }
        binding.noTemplates.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderPermissions() {
        val permissions = BackgroundPermissionHelper.status(this)
        binding.accessibilityStatus.text =
            if (permissions.accessibility) "Granted" else "Required"
        binding.overlayStatus.text = if (permissions.overlay) "Granted" else "Required"
        binding.notificationStatus.text =
            if (permissions.notifications) "Granted" else "Required"
        binding.accessibilityStatus.setTextColor(
            getColor(if (permissions.accessibility) R.color.primary else R.color.error),
        )
        binding.overlayStatus.setTextColor(
            getColor(if (permissions.overlay) R.color.primary else R.color.error),
        )
        binding.notificationStatus.setTextColor(
            getColor(if (permissions.notifications) R.color.primary else R.color.error),
        )
    }

    private fun renderStatus() {
        val running = AutoDetectorService.isRunning
        binding.statusText.text = if (running) "RUNNING" else "STOPPED"
        binding.statusDot.setBackgroundResource(
            if (running) R.drawable.bg_pulse_green else R.drawable.bg_status_red,
        )
        binding.btnStartDetection.isEnabled = !running
        binding.btnStopDetection.isEnabled = running
        binding.btnStartDetection.alpha = if (running) 0.55f else 1f
        binding.btnStopDetection.alpha = if (running) 1f else 0.55f
    }

    private fun requestPermissionsAndStart() {
        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) {
                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                finish()
                return@launch
            }
            if (!user.isAdmin && !user.hasActiveLicense()) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Approval required")
                    .setMessage("Your account must be approved before detection can start.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            val permissions = BackgroundPermissionHelper.status(this@MainActivity)
            when {
                !permissions.accessibility ->
                    startActivity(BackgroundPermissionHelper.accessibilityIntent())
                !permissions.overlay ->
                    startActivity(BackgroundPermissionHelper.overlayIntent(this@MainActivity))
                !permissions.notifications -> requestNotificationPermission()
                else -> requestProjection()
            }
        }
    }

    private fun requestProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startDetector() {
        val data = projectionData ?: return
        ContextCompat.startForegroundService(
            this,
            Intent(this, AutoDetectorService::class.java)
                .setAction(AutoDetectorService.ACTION_START)
                .putExtra(AutoDetectorService.EXTRA_RESULT_CODE, projectionResultCode)
                .putExtra(AutoDetectorService.EXTRA_RESULT_DATA, data),
        )
        if (binding.showOverlaySwitch.isChecked) startOverlay()
        renderStatus()
    }

    private fun stopDetector() {
        startService(Intent(this, AutoDetectorService::class.java).setAction(AutoDetectorService.ACTION_STOP))
        stopService(Intent(this, FloatingOverlayService::class.java))
        projectionData = null
        projectionResultCode = Activity.RESULT_CANCELED
        renderStatus()
    }

    private fun startOverlay() {
        if (Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FloatingOverlayService::class.java),
            )
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS,
            )
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 7001
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()