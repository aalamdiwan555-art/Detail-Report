package com.ultra.autodetector.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.ultra.autodetector.R
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.ui.admin.AdminActivity
import com.ultra.autodetector.ui.main.MainActivity
import com.ultra.autodetector.util.LogoTapAccessGesture
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "AuthActivity"
        private const val NAVIGATION_DEBOUNCE_MS = 2000L
    }

    private lateinit var authRepo: AuthRepository
    private var navigationStarted = false
    private var lastNavigationTime = 0L
    private var adminAccessInProgress = false

    private var tabs: TabLayout? = null
    private var etEmail: TextInputEditText? = null
    private var etPassword: TextInputEditText? = null
    private var btnAction: MaterialButton? = null
    private var authProgress: ProgressBar? = null
    private var logoTarget: View? = null
    private var ultraTitle: View? = null
    private var diamondIcon: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_auth)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inflate auth layout", e)
            showFatalError("Failed to load login screen")
            return
        }

        authRepo = AuthRepository(this)
        initializeViews()

        if (etEmail == null || etPassword == null || btnAction == null) {
            Log.e(TAG, "Critical views missing")
            showFatalError("Login screen is corrupted")
            return
        }

        checkExistingSession()
        setupUi()
    }

    private fun initializeViews() {
        tabs = findViewById(R.id.tab_layout)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnAction = findViewById(R.id.btn_action)
        authProgress = findViewById(R.id.auth_progress)
        // Main secret target from your layout
        logoTarget = findViewById(R.id.logo_access_target)
        // Extra fallbacks - will attach to all available
        ultraTitle = findViewById(R.id.tv_app_title)
        diamondIcon = findViewById(R.id.iv_logo_diamond)
        // Also try common IDs if your layout uses different names
        if (ultraTitle == null) ultraTitle = findViewById(R.id.tvUltra)
        if (diamondIcon == null) diamondIcon = findViewById(R.id.ivLogo)
        if (diamondIcon == null) diamondIcon = findViewById(R.id.logoImage)
    }

    private fun checkExistingSession() {
        lifecycleScope.launch {
            try {
                if (!authRepo.isLoggedIn()) return@launch

                authRepo.currentUser()?.let {
                    Log.i(TAG, "Valid session: ${it.email}")
                    if (it.isAdmin) openAdminActivity() else openMain()
                } ?: run {
                    Log.w(TAG, "Stale session, clearing")
                    authRepo.logout()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Session check error", e)
            }
        }
    }

    private fun setupUi() {
        // Intro animation for logo
        logoTarget?.let {
            it.alpha = 0f
            it.scaleX = 0.86f
            it.scaleY = 0.86f
            it.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(420L)
                .start()
        }

        tabs?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { updateButtonText() }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        // SECRET ADMIN - 6 sec hold on logo (NO VISIBLE HINT)
        // Attach to all logo-related views so user can hold anywhere on logo area
        val allTargets = listOfNotNull(logoTarget, ultraTitle, diamondIcon)
        if (allTargets.isNotEmpty()) {
            allTargets.forEach { view ->
                LogoTapAccessGesture.attach(view) {
                    openAdminPanel()
                }
            }
            Log.d(TAG, "Admin gesture attached to ${allTargets.size} views")
        } else {
            Log.w(TAG, "No logo view found for admin gesture - checking by ID")
            // Fallback attach by resource name
            listOf("logo_access_target", "tv_app_title", "iv_logo_diamond", "ivLogo", "logoImage").forEach { name ->
                try {
                    val id = resources.getIdentifier(name, "id", packageName)
                    if (id != 0) {
                        findViewById<View>(id)?.let {
                            LogoTapAccessGesture.attach(it) { openAdminPanel() }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        btnAction?.setOnClickListener { performAuth() }
        updateButtonText()
    }

    private fun updateButtonText() {
        val isSignup = tabs?.selectedTabPosition == 1
        btnAction?.text = if (isSignup) "CREATE ACCOUNT" else "LOGIN"
    }

    private fun setLoading(loading: Boolean) {
        btnAction?.isEnabled = !loading
        authProgress?.visibility = if (loading) View.VISIBLE else View.GONE
        if (!loading) updateButtonText() else btnAction?.text = "AUTHENTICATING..."
    }

    private fun performAuth() {
        val email = etEmail?.text?.toString()?.trim().orEmpty()
        val password = etPassword?.text?.toString().orEmpty()

        if (email.length < 4) {
            etEmail?.error = "Email must be at least 4 characters"
            etEmail?.requestFocus()
            return
        }
        if (password.length < 4) {
            etPassword?.error = "Password must be at least 4 characters"
            etPassword?.requestFocus()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val isSignup = tabs?.selectedTabPosition == 1
                val result = if (isSignup) authRepo.signup(email, password)
                           else authRepo.login(email, password)

                result
                    .onSuccess { 
                        Log.i(TAG, "Auth success: ${it.email}")
                        openMain() 
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Auth failed: ${error.message}")
                        setLoading(false)
                        showAuthError(error.message ?: "Authentication failed")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Auth exception", e)
                setLoading(false)
                showAuthError(e.message ?: "Unexpected error")
            }
        }
    }

    private fun showAuthError(message: String) {
        etEmail?.error = message
        etEmail?.requestFocus()
    }

    // FIXED: Direct open, no loginAdmin() dependency which was blocking gesture
    private fun openAdminPanel() {
        if (adminAccessInProgress || isFinishing || isDestroyed) return
        adminAccessInProgress = true
        
        // Haptic + visual feedback
        logoTarget?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        
        Log.d(TAG, "Admin gesture triggered - opening admin")
        openAdminActivity()
    }

    private fun openAdminActivity() {
        if (isFinishing || isDestroyed) return
        try {
            startActivity(Intent(this, AdminActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open admin", e)
            adminAccessInProgress = false
        }
    }

    private fun openMain() {
        val now = System.currentTimeMillis()
        if (now - lastNavigationTime < NAVIGATION_DEBOUNCE_MS) return
        if (navigationStarted || isFinishing || isDestroyed) return

        navigationStarted = true
        lastNavigationTime = now

        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } catch (error: Exception) {
            Log.e(TAG, "Navigation failed", error)
            navigationStarted = false
            setLoading(false)
            showAuthError("Failed to open: ${error.message}")
        }
    }

    private fun showFatalError(message: String) {
        Log.e(TAG, "Fatal: $message")
        finish()
    }
}
