package com.ultra.autodetector.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.databinding.ActivityAuthBinding
import com.ultra.autodetector.ui.main.MainActivity
import com.ultra.autodetector.util.TelegramHelper
import kotlinx.coroutines.launch

class AuthActivity : ComponentActivity() {
    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: AuthRepository
    private var displayedUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)
        lifecycleScope.launch {
            AuthRepository.seedAdmin(this@AuthActivity)
            auth.currentUser()?.let(::showMain)
        }
        binding.btnLogin.setOnClickListener { submit(false) }
        binding.btnRegister.setOnClickListener { submit(true) }
        binding.btnRenew.setOnClickListener {
            displayedUser?.let { TelegramHelper.openRenewalChat(this, it) }
        }
    }

    private fun submit(register: Boolean) {
        val email = binding.inputEmail.text?.toString().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        lifecycleScope.launch {
            val result = if (register) auth.register(email, password) else auth.login(email, password)
            result.onSuccess { user ->
                if (user.hasActiveLicense() || user.isAdmin) showMain(user) else showStatus(user)
            }.onFailure { error ->
                Toast.makeText(this@AuthActivity, error.message ?: "Unable to continue.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showStatus(user: User) {
        displayedUser = user
        binding.statusCard.visibility = android.view.View.VISIBLE
        binding.statusTitle.text = user.licenseStatus.wireValue.replaceFirstChar { it.uppercase() }
        binding.statusDetails.text = user.remainingLabel()
    }

    private fun showMain(user: User) {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}