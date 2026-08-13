package com.ultra.autodetector.ui.auth

import android.content.Intent
import android.os.Bundle
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
        try {
            setContentView(R.layout.activity_auth)
        } catch (e: Exception) {
            e.printStackTrace()
            // Layout crash hai to direct main khol de taaki black screen na aaye
            openMain()
            return
        }

        authRepo = AuthRepository(this)

        // Login check safe banaya
        try {
            if (authRepo.isLoggedIn()) {
                lifecycleScope.launch {
                    try {
                        if (authRepo.currentUser() != null) openMain() else authRepo.logout()
                    } catch (_: Exception) {
                        authRepo.logout()
                    }
                }
            }
        } catch (_: Exception) {}

        val tabs = findViewById<TabLayout>(R.id.tab_layout)
        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val btnAction = findViewById<MaterialButton>(R.id.btn_action)
        val btnTrial = findViewById<TextView>(R.id.btn_trial)
        val btnRequest = findViewById<TextView>(R.id.btn_request_approval)
        val authProgress = findViewById<ProgressBar>(R.id.auth_progress)
        val logo = findViewById<TextView>(R.id.logo_text)

        // FIX 1: logo null check - yahi black screen crash tha
        logo?.let {
            it.alpha = 0f
            it.scaleX = 0.86f
            it.scaleY = 0.86f
            it.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420L).start()
        }

        tabs?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (btnAction != null) {
                    btnAction.text = if (tab?.position == 1) "CREATE ACCOUNT" else "LOGIN"
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        fun setLoading(loading: Boolean) {
            btnAction?.isEnabled = !loading
            btnTrial?.isEnabled = !loading
            btnRequest?.isEnabled = !loading
            authProgress?.visibility = if (loading) View.VISIBLE else View.GONE
            if (!loading && btnAction != null && tabs != null) {
                btnAction.text = if (tabs.selectedTabPosition == 1) "CREATE ACCOUNT" else "LOGIN"
            } else if (loading) {
                btnAction?.text = "AUTHENTICATING..."
            }
        }

        fun doAuth() {
            val email = etEmail?.text?.toString()?.trim().orEmpty()
            val password = etPassword?.text?.toString().orEmpty()
            if (email.length < 4 || password.length < 4) {
                etEmail?.error = "Min 4 chars"
                return
            }
            setLoading(true)
            lifecycleScope.launch {
                try {
                    val result = if (tabs?.selectedTabPosition == 1) {
                        authRepo.signup(email, password)
                    } else {
                        authRepo.login(email, password)
                    }
                    result.onSuccess { openMain() }
                        .onFailure {
                            setLoading(false)
                            etEmail?.error = it.message ?: "Auth failed"
                        }
                } catch (e: Exception) {
                    setLoading(false)
                    etEmail?.error = e.message
                }
            }
        }

        btnAction?.setOnClickListener { doAuth() }
        btnTrial?.setOnClickListener {
            etEmail?.setText("divanatik84@gmail.com")
            etPassword?.setText("1qwwq11qw")
            tabs?.getTabAt(0)?.select()
            doAuth()
        }
        btnRequest?.setOnClickListener {
            tabs?.getTabAt(1)?.select()
            doAuth()
        }
    }

    private fun openMain() {
        try {
            startActivity(Intent(this, MainActivity::class.java))
        } catch (_: Exception) {}
        finish()
    }
}
