package com.ultra.autodetector.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.Dialog
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ultra.autodetector.R
import com.ultra.autodetector.data.local.EncryptedPrefsManager
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.databinding.ActivityMainBinding
import com.ultra.autodetector.detector.BuiltInTemplateManager
import com.ultra.autodetector.service.AutoDetectorService
import com.ultra.autodetector.service.FloatingOverlayService
import com.ultra.autodetector.ui.admin.AdminActivity
import com.ultra.autodetector.ui.auth.AuthActivity
import com.ultra.autodetector.util.BackgroundPermissionHelper
import com.ultra.autodetector.util.LongPressAccessGesture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_NOTIFICATIONS = 7001
    }

    private lateinit var binding: ActivityMainBinding
    private val auth by lazy { AuthRepository(this) }
    private val encryptedPrefs by lazy { EncryptedPrefsManager(this) }
    private val templateManager by lazy { BuiltInTemplateManager(this) }
    @Volatile private var templatesReady = false
    private var projectionData: Intent? = null
    private var projectionResultCode = Activity.RESULT_CANCELED
    private var permissionDialog: Dialog? = null
    private var permissionDialogUserId: String? = null

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

        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) {
                navigateToAuth()
                return@launch
            }
            setupUi()
            initializeTemplates()
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
        LongPressAccessGesture.attach(binding.logoText) { showAdminAccessDialog() }
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

    private fun initializeTemplates() {
        refreshUi()
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                (application as? com.ultra.autodetector.UltraAutoDetectorApp)?.ensureOpenCvLoaded()
                templateManager.onCreate()
            }
            templatesReady = true
            refreshUi()
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
        if (templatesReady) templateManager.close()
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
            val canViewTemplates = user.isAdmin
            val templateVisibility = if (canViewTemplates) View.VISIBLE else View.GONE
            binding.templateCount.visibility = templateVisibility
            binding.templateGrid.visibility = templateVisibility
            binding.noTemplates.visibility = templateVisibility
            if (templatesReady) renderTemplates()
            val permissions = BackgroundPermissionHelper.status(this@MainActivity)
            val onboardingComplete = encryptedPrefs.isPermissionOnboardingComplete(user.id) ||
                permissions.allGranted
            if (permissions.allGranted) {
                encryptedPrefs.setPermissionOnboardingComplete(user.id, true)
            }
            binding.detectorControls.visibility =
                if (onboardingComplete) View.VISIBLE else View.GONE
            renderPermissions(permissions)
            renderStatus()
            if (onboardingComplete) {
                dismissPermissionDialog()
            } else {
                showPermissionOnboarding(user)
            }
        }
    }

    private fun renderTemplates() {
        binding.templateGrid.removeAllViews()
        val items = templateManager.getAllTemplates()
        binding.templateCount.text = "${items.count { it.isActive }} templates active"

        items.forEach { template ->
            val card = layoutInflater.inflate(R.layout.item_builtin_template, binding.templateGrid, false)
            card.findViewById<android.widget.TextView>(R.id.template_name).text = template.name
            card.findViewById<android.widget.ImageView>(R.id.template_image).setImageBitmap(template.bitmap)
            card.findViewById<android.widget.TextView>(R.id.template_active).text = 
                if (template.isActive) "Active" else "Inactive"

            val seekBar = card.findViewById<SeekBar>(R.id.template_threshold)
            val thresholdText = card.findViewById<android.widget.TextView>(R.id.template_threshold_text)
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

    private fun renderPermissions(permissions: BackgroundPermissionHelper.Status) {
        binding.accessibilityStatus.text = if (permissions.accessibility) "Granted" else "Required"
        binding.overlayStatus.text = if (permissions.overlay) "Granted" else "Required"
        binding.notificationStatus.text = if (permissions.notifications) "Granted" else "Required"

        val grantedColor = getColor(R.color.primary)
        val errorColor = getColor(R.color.error)
        binding.accessibilityStatus.setTextColor(if (permissions.accessibility) grantedColor else errorColor)
        binding.overlayStatus.setTextColor(if (permissions.overlay) grantedColor else errorColor)
        binding.notificationStatus.setTextColor(if (permissions.notifications) grantedColor else errorColor)
    }

    private fun showPermissionOnboarding(user: User) {
        if (permissionDialog != null && permissionDialogUserId == user.id) {
            updatePermissionDialog()
            return
        }
        dismissPermissionDialog()
        if (isFinishing || isDestroyed) return

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_permission_setup)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.85f)
        permissionDialog = dialog
        permissionDialogUserId = user.id
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
        )
        statusViews.forEach { (id, granted) ->
            dialog.findViewById<TextView>(id)?.apply {
                text = if (granted) "Granted" else "Required"
                setTextColor(getColor(if (granted) R.color.primary else R.color.error))
            }
        }

        if (permissions.allGranted) {
            permissionDialogUserId?.let {
                encryptedPrefs.setPermissionOnboardingComplete(it, true)
            }
            dismissPermissionDialog()
            return
        }

        val nextPermission = when {
            !permissions.accessibility -> "Accessibility service"
            !permissions.overlay -> "Draw over other apps"
            else -> "Notifications"
        }
        dialog.findViewById<TextView>(R.id.permission_setup_message)?.text =
            "Grant $nextPermission to continue. Detection controls stay locked until every permission is ready."
        dialog.findViewById<MaterialButton>(R.id.btn_permission_continue)?.apply {
            text = "GRANT $nextPermission".uppercase()
            setOnClickListener {
                when {
                    !permissions.accessibility ->
                        startActivity(BackgroundPermissionHelper.accessibilityIntent())
                    !permissions.overlay ->
                        startActivity(BackgroundPermissionHelper.overlayIntent(this@MainActivity))
                    else -> requestNotificationPermission()
                }
            }
        }
    }

    private fun dismissPermissionDialog() {
        permissionDialog?.dismiss()
        permissionDialog = null
        permissionDialogUserId = null
    }

    private fun renderStatus() {
        val running = AutoDetectorService.isRunning
        binding.statusText.text = if (running) "RUNNING" else "STOPPED"
        binding.statusDot.setBackgroundResource(if (running) R.drawable.bg_pulse_green else R.drawable.bg_status_red)
        binding.btnStartDetection.isEnabled = !running
        binding.btnStopDetection.isEnabled = running
        binding.btnStartDetection.alpha = if (running) 0.55f else 1f
        binding.btnStopDetection.alpha = if (running) 1f else 0.55f
    }

    private fun showAdminAccessDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_admin_access, null)
        val email = content.findViewById<android.widget.EditText>(R.id.admin_email)
        val password = content.findViewById<android.widget.EditText>(R.id.admin_password)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(content)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("OPEN ADMIN", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val enteredEmail = email.text?.toString()?.trim().orEmpty()
                val enteredPassword = password.text?.toString().orEmpty()
                if (enteredEmail.isBlank() || enteredPassword.isBlank()) {
                    email.error = "Enter administrator credentials"
                    return@setOnClickListener
                }

                it.isEnabled = false
                lifecycleScope.launch {
                    auth.loginAdmin(enteredEmail, enteredPassword)
                        .onSuccess {
                            dialog.dismiss()
                            startActivity(Intent(this@MainActivity, AdminActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                        .onFailure { error ->
                            it.isEnabled = true
                            password.error = error.message ?: "Administrator authentication failed"
                        }
                }
            }
        }
        dialog.show()
    }

    private fun requestPermissionsAndStart() {
        lifecycleScope.launch {
            val user = auth.currentUser()
            if (user == null) { navigateToAuth(); return@launch }

            val permissions = BackgroundPermissionHelper.status(this@MainActivity)
            if (!permissions.allGranted ||
                !encryptedPrefs.isPermissionOnboardingComplete(user.id)
            ) {
                showPermissionOnboarding(user)
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
            ContextCompat.startForegroundService(this, Intent(this, FloatingOverlayService::class.java))
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
