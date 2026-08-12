package com.ultra.autodetector.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)

        binding.authTabs.addTab(binding.authTabs.newTab().setText("LOGIN"))
        binding.authTabs.addTab(binding.authTabs.newTab().setText("SIGN UP"))
        binding.authTabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                isRegisterMode = tab.position == 1
                updateMode()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
        binding.btnLogin.setOnClickListener { isRegisterMode = false; submit() }
        binding.btnRegister.setOnClickListener { isRegisterMode = true; submit() }
        binding.btnTrial.setOnClickListener { isRegisterMode = true; submit() }
        binding.btnRenew.setOnClickListener {
            displayedUser?.let { TelegramHelper.openRenewalChat(this, it) }
        }
        updateMode()

        lifecycleScope.launch {
            auth.currentUser()?.let { account ->
                if (account.isAdmin || account.hasActiveLicense()) showMain() else showStatus(account)
            }
        }
    }

    private fun updateMode() {
        binding.btnLogin.visibility = if (isRegisterMode) View.GONE else View.VISIBLE
        binding.btnRegister.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
        binding.btnTrial.visibility = if (isRegisterMode) View.GONE else View.VISIBLE
    }

    private fun submit() {
        val email = binding.inputEmail.text?.toString().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        binding.inputEmail.error = null
        binding.inputPassword.error = null
        lifecycleScope.launch {
            binding.btnLogin.isEnabled = false
            binding.btnRegister.isEnabled = false
            binding.btnTrial.isEnabled = false
            val result = if (isRegisterMode) auth.signup(email, password) else auth.login(email, password)
            result.onSuccess { user ->
                if (user.isAdmin || user.hasActiveLicense()) showMain() else showStatus(user)
            }.onFailure { error ->
                binding.root.animate().translationX(12f).setDuration(45).withEndAction {
                    binding.root.animate().translationX(-12f).setDuration(45).withEndAction {
                        binding.root.animate().translationX(0f).setDuration(45).start()
                    }.start()
                }.start()
                MaterialAlertDialogBuilder(this@AuthActivity)
                    .setTitle("Could not continue")
                    .setMessage(error.message ?: "Check your details and try again.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            binding.btnLogin.isEnabled = true
            binding.btnRegister.isEnabled = true
            binding.btnTrial.isEnabled = true
        }
    }

    private fun showStatus(user: User) {
        displayedUser = user
        binding.statusCard.visibility = View.VISIBLE
        binding.statusTitle.text = user.licenseStatus.replaceFirstChar { it.uppercase() }
        binding.statusDetails.text = user.remainingLabel()
    }

    private fun showMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}