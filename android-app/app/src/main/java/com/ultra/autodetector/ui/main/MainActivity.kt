package com.ultra.autodetector.ui.main

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ultra.autodetector.R
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.service.AutoDetectorService
import com.ultra.autodetector.service.FloatingOverlayService
import com.ultra.autodetector.ui.admin.AdminActivity
import com.ultra.autodetector.ui.auth.AuthActivity
import com.ultra.autodetector.util.BackgroundPermissionHelper
import com.ultra.autodetector.util.LogoTapAccessGesture
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_NOTIFICATIONS = 7001
        private const val REQUEST_CODE_SCREEN = 7002
        private const val PREFS_PERMISSIONS = "ultra_permission_preferences"
        private const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"
    }

    private lateinit var binding: ActivityMainBinding
    private val auth by lazy { AuthRepository(this) }
    private val permissionPrefs by lazy {
        getSharedPreferences(PREFS_PERMISSIONS, MODE_PRIVATE)
    }
    private var mediaProjectionPermissionGranted = false
    private var projectionData: Intent? = null
    private var permissionDialog: Dialog? = null
    private var adminAccessInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) {
                navigateToAuth()
                return@launch
            }
            setupUi()
        }
    }

    private fun navigateToAuth() {
        startActivity(Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun setupUi() {
        binding.btnStartDetection.setOnClickListener { requestPermissionsAndStart() }
        binding.btnStopDetection.setOnClickListener { stopDetector() }

        // SECRET ADMIN - 6 sec hold on ULTRA logo (120dp x 60dp touch area)
        binding.logoAccessTarget.apply {
            isClickable = true
            isLongClickable = true
            LogoTapAccessGesture.attach(this) { openAdminPanel() }
        }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                auth.logout()
                navigateToAuth()
            }
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
            if (AutoDetectorService.isRunning && !checked) {
                stopService(Intent(this, FloatingOverlayService::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) {
                navigateToAuth()
                return@launch
            }
            refreshUi()
        }
    }

    override fun onDestroy() {
        dismissPermissionDialog()
        super.onDestroy()
    }

    private fun refreshUi() {
        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) {
                navigateToAuth()
                return@launch
            }
            binding.accountEmail.text = user.email
            binding.accountStatus.text = if (user.isAdmin) {
                "Administrator • ${user.remainingLabel()}"
            } else {
                "${user.licenseStatus.uppercase()} • ${user.remainingLabel()}"
            }
            binding.accountDetails.text =
                "Account ID: ${user.id}\nDevice: ${user.deviceId.ifBlank { "This device" }}"
            val permissions = BackgroundPermissionHelper.status(this@MainActivity)
            binding.detectorControls.visibility = View.VISIBLE
            updatePermissionUI(permissions)
            renderStatus()
            if (permissionPrefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)) {
                dismissPermissionDialog()
            } else if (permissionDialog != null) {
                updatePermissionDialog()
            } else if (!permissions.mainPermissionsGranted) {
                showPermissionOnboarding()
            } else {
                dismissPermissionDialog()
            }
        }
    }

    private fun updatePermissionUI(permissions: BackgroundPermissionHelper.Status) {
        binding.accessibilityStatus.text =
            if (permissions.accessibility) "Granted" else "Not Granted"
        binding.overlayStatus.text =
            if (permissions.overlay) "Granted" else "Not Granted"
        binding.notificationStatus.text =
            if (permissions.notifications) "Granted" else "Not Granted"

        val grantedColor = getColor(R.color.primary)
        val errorColor = getColor(R.color.error)
        binding.accessibilityStatus.setTextColor(if (permissions.accessibility) grantedColor else errorColor)
        binding.overlayStatus.setTextColor(if (permissions.overlay) grantedColor else errorColor)
        binding.notificationStatus.setTextColor(if (permissions.notifications) grantedColor else errorColor)
        binding.btnAccessibility.visibility = if (permissions.accessibility) View.GONE else View.VISIBLE
        binding.btnOverlay.visibility = if (permissions.overlay) View.GONE else View.VISIBLE
        binding.btnNotifications.visibility =
            if (permissions.notifications) View.GONE else View.VISIBLE
    }

    private fun showPermissionOnboarding() {
        if (permissionDialog != null) {
            updatePermissionDialog()
            return
        }
        dismissPermissionDialog()
        if (isFinishing || isDestroyed) return

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_permissions_fullscreen)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.85f)
        permissionDialog = dialog
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
            )
            updatePermissionDialog()
        }
        dialog.show()
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
        )
        updatePermissionDialog()
    }

    private fun updatePermissionDialog() {
        val dialog = permissionDialog ?: return
        val permissions = BackgroundPermissionHelper.status(this)
        val statusViews = listOf(
            R.id.permission_accessibility_status to permissions.accessibility,
            R.id.permission_overlay_status to permissions.overlay,
            R.id.permission_notifications_status to permissions.notifications,
            R.id.permission_battery_status to isIgnoringBatteryOptimizations(),
        )
        statusViews.forEach { (id, granted) ->
            dialog.findViewById<TextView>(id)?.apply {
                text = if (granted) "Granted" else "Not Granted"
                setTextColor(getColor(if (granted) R.color.primary else R.color.error))
            }
        }

        dialog.findViewById<MaterialButton>(R.id.btn_permission_accessibility)?.setOnClickListener {
            startActivity(BackgroundPermissionHelper.accessibilityIntent())
        }
        dialog.findViewById<MaterialButton>(R.id.btn_permission_overlay)?.setOnClickListener {
            startActivity(BackgroundPermissionHelper.overlayIntent(this@MainActivity))
        }
        dialog.findViewById<MaterialButton>(R.id.btn_permission_notifications)?.setOnClickListener {
            requestNotificationPermission()
        }
        dialog.findViewById<MaterialButton>(R.id.btn_permission_battery)?.setOnClickListener {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:$packageName"),
                    ),
                )
            }
        }
        dialog.findViewById<MaterialButton>(R.id.btn_permission_continue)?.apply {
            isEnabled = permissions.mainPermissionsGranted
            setOnClickListener {
                permissionPrefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, true).apply()
                dismissPermissionDialog()
            }
        }
    }

    private fun dismissPermissionDialog() {
        permissionDialog?.dismiss()
        permissionDialog = null
    }

    private fun renderStatus() {
        val running = AutoDetectorService.isRunning
        binding.tvStatus.text = if (running) "RUNNING" else "STOPPED"
        binding.statusDot.setBackgroundResource(if (running) R.drawable.bg_pulse_green else R.drawable.bg_status_red)
        binding.btnStartDetection.isEnabled = !running
        binding.btnStopDetection.isEnabled = running
        binding.btnStartDetection.alpha = if (running) 0.55f else 1f
        binding.btnStopDetection.alpha = if (running) 1f else 0.55f
    }

    private fun openAdminPanel() {
        if (adminAccessInProgress || isFinishing || isDestroyed) return
        adminAccessInProgress = true
        binding.logoAccessTarget.isEnabled = false

        lifecycleScope.launch {
            auth.loginAdmin()
                .onSuccess {
                    startActivity(Intent(this@MainActivity, AdminActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                .onFailure { error ->
                    adminAccessInProgress = false
                    binding.logoAccessTarget.isEnabled = true
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Administrator access failed")
                        .setMessage(error.message ?: "Unable to open administrator panel.")
                        .setPositiveButton("OK", null)
                        .show()
                }
        }
    }

    private fun requestPermissionsAndStart() {
        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) { navigateToAuth(); return@launch }

            val permissions = BackgroundPermissionHelper.status(this@MainActivity)
            if (!permissions.mainPermissionsGranted) {
                if (!permissionPrefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)) {
                    showPermissionOnboarding()
                } else {
                    when {
                        !permissions.accessibility ->
                            startActivity(BackgroundPermissionHelper.accessibilityIntent())
                        !permissions.overlay ->
                            startActivity(BackgroundPermissionHelper.overlayIntent(this@MainActivity))
                        else -> requestNotificationPermission()
                    }
                }
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

            when {
                !permissions.accessibility -> startActivity(BackgroundPermissionHelper.accessibilityIntent())
                !permissions.overlay -> startActivity(BackgroundPermissionHelper.overlayIntent(this@MainActivity))
                !permissions.notifications -> requestNotificationPermission()
                else -> requestProjection()
            }
        }
    }

    private fun requestProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (!mediaProjectionPermissionGranted || projectionData == null) {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN)
        } else {
            startDetector()
        }
    }

    private fun startDetector() {
        val data = projectionData ?: return
        ContextCompat.startForegroundService(
            this,
            Intent(this, AutoDetectorService::class.java)
                .setAction(AutoDetectorService.ACTION_START)
                .putExtra(AutoDetectorService.EXTRA_RESULT_CODE, Activity.RESULT_OK)
                .putExtra(AutoDetectorService.EXTRA_RESULT_DATA, data),
        )
        if (binding.showOverlaySwitch.isChecked) startOverlay()
        renderStatus()
    }

    private fun stopDetector() {
        startService(Intent(this, AutoDetectorService::class.java).setAction(AutoDetectorService.ACTION_STOP))
        stopService(Intent(this, FloatingOverlayService::class.java))
        renderStatus()
    }

    @Deprecated("Use the Activity Result API in new screens")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE_SCREEN) return

        if (resultCode == Activity.RESULT_OK && data != null) {
            mediaProjectionPermissionGranted = true
            projectionData = data
            startDetector()
        } else {
            binding.detectorStatus.text = getString(R.string.screen_capture_cancelled)
        }
        refreshUi()
    }

    private fun startOverlay() {
        if (Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(this, Intent(this, FloatingOverlayService::class.java))
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }
}
