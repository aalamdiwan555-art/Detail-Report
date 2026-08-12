package com.ultra.autodetector.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.ultra.autodetector.R
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.ui.main.MainActivity
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    private lateinit var authRepo: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        authRepo = AuthRepository(this)

        if (authRepo.isLoggedIn()) {
            lifecycleScope.launch {
                if (authRepo.currentUser() != null) openMain() else authRepo.logout()
            }
            return
        }

        val tabs = findViewById<TabLayout>(R.id.tab_layout)
        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val btnAction = findViewById<MaterialButton>(R.id.btn_action)
        val btnTrial = findViewById<TextView>(R.id.btn_trial)
        val btnRequest = findViewById<TextView>(R.id.btn_request_approval)
        val authProgress = findViewById<ProgressBar>(R.id.auth_progress)
        val logo = findViewById<TextView>(R.id.logo_text)

        logo.alpha = 0f
        logo.scaleX = 0.86f
        logo.scaleY = 0.86f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420L).start()

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                btnAction.text = if (tab?.position == 1) "CREATE ACCOUNT" else "LOGIN"
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        fun setLoading(loading: Boolean) {
            btnAction.isEnabled = !loading
            btnTrial.isEnabled = !loading
            btnRequest.isEnabled = !loading
            authProgress.visibility = if (loading) View.VISIBLE else View.GONE
            btnAction.text = when {
                loading -> "AUTHENTICATING..."
                tabs.selectedTabPosition == 1 -> "CREATE ACCOUNT"
                else -> "LOGIN"
            }
        }

        fun doAuth() {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()
            if (email.length < 4 || password.length < 4) {
                etEmail.error = "Email and password must be at least 4 characters"
                return
            }
            setLoading(true)
            lifecycleScope.launch {
                val result = if (tabs.selectedTabPosition == 1) {
                    authRepo.signup(email, password)
                } else {
                    authRepo.login(email, password)
                }
                result.onSuccess { openMain() }
                    .onFailure {
                        setLoading(false)
                        etEmail.error = it.message ?: "Authentication failed"
                    }
            }
        }

        btnAction.setOnClickListener { doAuth() }
        btnTrial.setOnClickListener {
            etEmail.setText("divanatik84@gmail.com")
            etPassword.setText("1qwwq11qw")
            tabs.getTabAt(0)?.select()
            doAuth()
        }
        btnRequest.setOnClickListener {
            tabs.getTabAt(1)?.select()
            doAuth()
        }

        val pressListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
            }
            false
        }
        btnAction.setOnTouchListener(pressListener)
        btnTrial.setOnTouchListener(pressListener)
        btnRequest.setOnTouchListener(pressListener)
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}