package com.ultra.autodetector.ui.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.data.repository.TemplateRepository
import com.ultra.autodetector.data.repository.UserRepository
import com.ultra.autodetector.databinding.ActivityAdminBinding
import com.ultra.autodetector.ui.adapter.TemplateAdapter
import com.ultra.autodetector.ui.adapter.UserAdapter
import kotlinx.coroutines.launch

class AdminActivity : ComponentActivity() {
    private lateinit var binding: ActivityAdminBinding
    private lateinit var auth: AuthRepository
    private lateinit var users: UserRepository
    private lateinit var templates: TemplateRepository
    private var admin: User? = null
    private val userAdapter = UserAdapter(::grant, ::reject)
    private val templateAdapter = TemplateAdapter { id -> lifecycleScope.launch { templates.delete(id); refresh() } }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && binding.inputTemplateName.text?.isNotBlank() == true) {
            lifecycleScope.launch {
                runCatching {
                    templates.add(
                        binding.inputTemplateName.text.toString(),
                        binding.inputTemplateDescription.text.toString(),
                        uri,
                        admin?.uid.orEmpty(),
                    )
                }.onFailure { Toast.makeText(this@AdminActivity, it.message, Toast.LENGTH_LONG).show() }
                    .onSuccess { refresh() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)
        users = UserRepository(this)
        templates = TemplateRepository(this)
        binding.usersList.layoutManager = LinearLayoutManager(this)
        binding.usersList.adapter = userAdapter
        binding.templatesList.layoutManager = GridLayoutManager(this, 2)
        binding.templatesList.adapter = templateAdapter
        binding.btnBack.setOnClickListener { finish() }
        binding.btnUploadTemplate.setOnClickListener { imagePicker.launch("image/*") }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            admin = auth.currentUser()
            if (admin?.isAdmin != true) {
                finish()
            } else {
                refresh()
            }
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            userAdapter.submit(users.listUsers())
            templateAdapter.submit(templates.listAll())
        }
    }

    private fun grant(user: User, days: Int?) {
        lifecycleScope.launch {
            runCatching { users.grant(user.uid, days) }
                .onFailure { Toast.makeText(this@AdminActivity, it.message, Toast.LENGTH_LONG).show() }
                .onSuccess { refresh() }
        }
    }

    private fun reject(user: User) {
        lifecycleScope.launch {
            runCatching { users.reject(user.uid) }.onSuccess { refresh() }
        }
    }
}