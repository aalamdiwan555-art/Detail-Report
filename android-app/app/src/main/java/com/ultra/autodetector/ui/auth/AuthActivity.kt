package com.ultra.autodetector.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
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
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        if (prefs.contains("email")) {
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

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                btnAction.text = if (tab?.position == 0) "LOGIN" else "CREATE ACCOUNT"
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val doAuth = {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            if (email.isNotEmpty() && pass.length >= 4) {
                authRepo.login(email, pass)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                etEmail.error = "Enter valid email"
            }
        }
        btnAction.setOnClickListener { doAuth() }
        btnTrial.setOnClickListener {
            authRepo.login("diwanatik84@gmail.com", "admin123")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        btnRequest.setOnClickListener { doAuth() }
    }
}
