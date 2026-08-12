package com.ultra.autodetector.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
        val tabs = findViewById<TabLayout>(R.id.auth_tabs)
        val inputEmail = findViewById<TextInputEditText>(R.id.input_email)
        val inputPassword = findViewById<TextInputEditText>(R.id.input_password)
        val btnLogin = findViewById<MaterialButton>(R.id.btn_login)
        val btnRegister = findViewById<MaterialButton>(R.id.btn_register)
        val btnTrial = findViewById<MaterialButton>(R.id.btn_trial)
        val statusCard = findViewById<MaterialCardView>(R.id.status_card)
        val btnRenew = findViewById<MaterialButton>(R.id.btn_renew)
        tabs.addTab(tabs.newTab().setText("LOGIN"))
        tabs.addTab(tabs.newTab().setText("SIGN UP"))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val isLogin = tab?.position == 0
                btnLogin.visibility = if (isLogin) View.VISIBLE else View.GONE
                btnRegister.visibility = if (isLogin) View.GONE else View.VISIBLE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        val doAuth = {
            val email = inputEmail.text.toString().trim()
            val pass = inputPassword.text.toString().trim()
            if (email.isNotEmpty() && pass.length >= 4) {
                authRepo.login(email, pass)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
        btnLogin.setOnClickListener { doAuth() }
        btnRegister.setOnClickListener { doAuth() }
        btnTrial.setOnClickListener {
            authRepo.login("diwanatik84@gmail.com", "admin123")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        btnRenew.setOnClickListener { statusCard.visibility = View.GONE }
    }
}
