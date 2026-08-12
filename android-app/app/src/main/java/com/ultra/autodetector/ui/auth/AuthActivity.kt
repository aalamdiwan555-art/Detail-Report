package com.ultra.autodetector.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import com.ultra.autodetector.R
import com.ultra.autodetector.auth.AuthRepository
import com.ultra.autodetector.ui.main.MainActivity

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        val authRepo = AuthRepository(this)
        if (authRepo.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        fun setLoading(loading: Boolean, label: String = "LOGIN") {
            btnAction.isEnabled = !loading
            btnTrial.isEnabled = !loading
            btnRequest.isEnabled = !loading
            authProgress.visibility = if (loading) View.VISIBLE else View.GONE
            btnAction.text = if (loading) "AUTHENTICATING..." else label
        }

        fun openMain() {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        fun doAuth() {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            if (email.length >= 4 && pass.length >= 4) {
                setLoading(true)
                btnAction.postDelayed({
                    if (authRepo.login(email, pass)) {
                        openMain()
                    } else {
                        setLoading(false, if (tabs.selectedTabPosition == 1) "CREATE ACCOUNT" else "LOGIN")
                        etEmail.error = "Enter at least 4 characters"
                    }
                }, 220L)
            } else {
                etEmail.error = "Email and password must be at least 4 characters"
            }
        }
        btnAction.setOnClickListener { doAuth() }
        btnTrial.setOnClickListener {
            setLoading(true, "LOGIN")
            btnAction.postDelayed({
                authRepo.login("divanatik84@gmail.com", "1qwwq11qw")
                openMain()
            }, 220L)
        }
        btnRequest.setOnClickListener { doAuth() }

        val pressListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
            }
            false
        }
        btnAction.setOnTouchListener(pressListener)
        btnTrial.setOnTouchListener(pressListener)
        btnRequest.setOnTouchListener(pressListener)
    }
}
