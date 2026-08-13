package com.ultra.autodetector.ui.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ultra.autodetector.data.local.UserEntity
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.data.repository.UserRepository
import com.ultra.autodetector.databinding.ActivityAdminBinding
import com.ultra.autodetector.ui.adapter.UserAdapter
import kotlinx.coroutines.launch

/** Approval-only administrator screen. Built-in templates never enter this UI. */
class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private lateinit var auth: AuthRepository
    private lateinit var users: UserRepository
    private val userAdapter = UserAdapter(
        onGrant = { user, days -> approve(user, days) },
        onReject = { user -> reject(user) },
        onOpen = { user -> showUserActions(user) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)
        users = UserRepository(this)
        binding.usersList.layoutManager = LinearLayoutManager(this)
        binding.usersList.adapter = userAdapter
        binding.btnBack.setOnClickListener { finish() }
        binding.btnLogoutAdmin.setOnClickListener {
            lifecycleScope.launch {
                auth.logout()
                finish()
            }
        }
        binding.btnClearPending.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear pending accounts?")
                .setMessage("This removes all accounts waiting for approval.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        users.clearPending()
                        refreshUsers()
                    }
                }
                .show()
        }
        binding.userSearch.addTextChangedListener(SimpleTextWatcher { refreshUsers() })
        lifecycleScope.launch {
            val current = auth.currentUser()
            if (current?.isAdmin != true) finish() else refreshUsers()
        }
    }

    private fun refreshUsers() {
        lifecycleScope.launch {
            userAdapter.submit(users.listUsers(binding.userSearch.text?.toString().orEmpty()))
        }
    }

    private fun approve(user: User, days: Int) {
        lifecycleScope.launch {
            runCatching { users.approve(user.id, days) }
                .onFailure { showMessage("Approval failed", it.message.orEmpty()) }
                .onSuccess { refreshUsers() }
        }
    }

    private fun reject(user: User) {
        lifecycleScope.launch {
            runCatching { users.reject(user.id) }
                .onFailure { showMessage("Reject failed", it.message.orEmpty()) }
                .onSuccess { refreshUsers() }
        }
    }

    private fun showUserActions(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle(user.email)
            .setMessage("Status: ${user.licenseStatus}\n${user.remainingLabel()}")
            .setPositiveButton("Approve 7 days") { _, _ -> approve(user, 7) }
            .setNeutralButton("Approve lifetime") { _, _ -> approve(user, 3650) }
            .setNegativeButton("Reject") { _, _ -> reject(user) }
            .show()
    }

    private fun showMessage(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private class SimpleTextWatcher(private val onChanged: () -> Unit) :
        android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}