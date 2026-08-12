package com.ultra.autodetector.ui.admin

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ultra.autodetector.BuildConfig
import com.ultra.autodetector.R
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.UserEntity
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.data.repository.AdminConfig
import com.ultra.autodetector.data.repository.AuthRepository
import com.ultra.autodetector.data.repository.TemplateRepository
import com.ultra.autodetector.data.repository.UserRepository
import com.ultra.autodetector.databinding.ActivityAdminBinding
import com.ultra.autodetector.ui.adapter.UserAdapter
import com.ultra.autodetector.ui.adapter.TemplateAdapter
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private lateinit var auth: AuthRepository
    private lateinit var users: UserRepository
    private lateinit var templates: TemplateRepository
    private var admin: User? = null
    private var selectedFilter = "all"
    private var pendingExport: String? = null

    private val templateAdapter = TemplateAdapter(onDelete = ::confirmDeleteTemplate)

    private val userAdapter = UserAdapter(
        onGrant = { user, days -> approve(user, days) },
        onReject = { user -> reject(user) },
        onOpen = { user -> showUserActions(user) },
    )

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                runCatching {
                    val output = contentResolver.openOutputStream(uri)
                        ?: error("Unable to open the selected file.")
                    output.use { it.write(pendingExport.orEmpty().toByteArray(Charsets.UTF_8)) }
                }.onFailure { showMessage("Export failed", it.message.orEmpty()) }
            }
        }

    private val chooseTemplate =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            showTemplateDetailsDialog(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = AuthRepository(this)
        users = UserRepository(this)
        templates = TemplateRepository(this)
        binding.usersList.layoutManager = LinearLayoutManager(this)
        binding.usersList.adapter = userAdapter
        binding.templatesList.layoutManager = LinearLayoutManager(this)
        binding.templatesList.adapter = templateAdapter
        binding.btnBack.setOnClickListener { finish() }
        setupTabs()
        setupActions()
        lifecycleScope.launch { verifyAdmin() }
    }

    private suspend fun verifyAdmin() {
        admin = auth.currentUser()
        if (admin?.isAdmin != true ||
            AdminConfig.ADMIN_EMAIL.isBlank() ||
            !admin!!.email.equals(AdminConfig.ADMIN_EMAIL, ignoreCase = true)
        ) {
            showMessage("Access denied", "Only the provisioned local administrator can open this panel.") {
                finish()
            }
            return
        }
        binding.adminEmailValue.text = admin!!.email
        binding.appVersion.text = "Ultra AutoDetector ${BuildConfig.VERSION_NAME} • offline storage"
        showReauth()
    }

    private fun showReauth() {
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "Administrator password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 0, 48, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm administrator access")
            .setMessage("Re-enter the provisioned password to continue.")
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("CANCEL") { _, _ -> finish() }
            .setPositiveButton("CONTINUE") { _, _ ->
                if (!AdminConfig.matches(admin?.email.orEmpty(), input.text?.toString().orEmpty())) {
                    showMessage("Access denied", "The administrator password is incorrect.") { finish() }
                } else {
                    refreshAll()
                }
            }
            .show()
    }

    private fun setupTabs() {
        listOf("Dashboard", "Users", "Templates", "Broadcast", "Settings").forEach {
            binding.adminTabs.addTab(binding.adminTabs.newTab().setText(it))
        }
        binding.adminTabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                val pages = listOf(
                    binding.pageDashboard,
                    binding.pageUsers,
                    binding.pageTemplates,
                    binding.pageBroadcast,
                    binding.pageSettings,
                )
                pages.forEachIndexed { index, page -> page.visibility = if (index == tab.position) View.VISIBLE else View.GONE }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
    }

    private fun setupActions() {
        binding.userSearch.addTextChangedListener(SimpleTextWatcher { refreshUsers() })
        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedFilter = when (checkedIds.firstOrNull()) {
                R.id.chip_pending -> UserEntity.STATUS_PENDING
                R.id.chip_approved -> UserEntity.STATUS_APPROVED
                R.id.chip_expired -> UserEntity.STATUS_EXPIRED
                else -> "all"
            }
            refreshUsers()
        }
        binding.usersRefresh.setOnRefreshListener { refreshUsers() }
        binding.btnUploadTemplate.setOnClickListener {
            chooseTemplate.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/bmp"))
        }
        binding.btnRefreshTemplates.setOnClickListener { refreshTemplates() }
        binding.templatesRefresh.setOnRefreshListener { refreshTemplates() }
        binding.btnPostNotice.setOnClickListener { postNotice() }
        binding.btnClearNotice.setOnClickListener { clearNotice() }
        binding.btnClearPending.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear pending users?")
                .setMessage("This permanently removes every pending local account.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR") { _, _ ->
                    lifecycleScope.launch { users.clearPending(); refreshAll() }
                }.show()
        }
        binding.btnExportUsers.setOnClickListener { exportUsers() }
        binding.btnLogoutAdmin.setOnClickListener {
            lifecycleScope.launch {
                auth.logout()
                startActivity(Intent(this@AdminActivity, com.ultra.autodetector.ui.auth.AuthActivity::class.java))
                finishAffinity()
            }
        }
    }

    private fun refreshAll() {
        lifecycleScope.launch {
            val stats = users.stats()
            animateCount(binding.statTotal, stats.total)
            animateCount(binding.statActive, stats.active)
            animateCount(binding.statPending, stats.pending)
            animateCount(binding.statExpired, stats.expired)
            val entries = users.registrationsLastSevenDays().mapIndexed { index, count ->
                BarEntry(index.toFloat(), count.toFloat())
            }
            binding.userChart.data = BarData(
                BarDataSet(entries, "Registrations").apply {
                    color = getColor(R.color.primary)
                    valueTextColor = getColor(R.color.muted)
                    valueTextSize = 10f
                },
            ).apply { barWidth = 0.55f }
            binding.userChart.description.isEnabled = false
            binding.userChart.legend.textColor = getColor(R.color.muted)
            binding.userChart.axisLeft.textColor = getColor(R.color.muted)
            binding.userChart.axisRight.isEnabled = false
            binding.userChart.xAxis.isEnabled = false
            binding.userChart.invalidate()
            refreshUsers()
            refreshTemplates()
            val recent = users.listUsers().take(5)
            binding.recentUsers.text = if (recent.isEmpty()) {
                "No users yet."
            } else {
                recent.joinToString("\n") { "• ${it.email}  ·  ${it.licenseStatus.uppercase()}" }
            }
            val latest = AppDatabase.getInstance(this@AdminActivity).noticeDao().getLatest()
            binding.noticePreview.text = latest?.message ?: "No notice is currently posted."
        }
    }

    private fun refreshUsers() {
        lifecycleScope.launch {
            userAdapter.submit(users.listUsers(binding.userSearch.text?.toString().orEmpty(), selectedFilter))
            binding.usersRefresh.isRefreshing = false
        }
    }

    private fun refreshTemplates() {
        lifecycleScope.launch {
            templateAdapter.submit(templates.listAll())
            binding.templatesRefresh.isRefreshing = false
        }
    }

    // --- FIXED FUNCTION - YAHI BUG THA ---
    private fun showTemplateDetailsDialog(uri: android.net.Uri) {
        val nameInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "Template name"
            isSingleLine = true
            maxLines = 1
        }
        val descriptionInput = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "Description (optional)"
            setTextColor(Color.BLACK)
            isSingleLine = false
            minLines = 2
            maxLines = 3
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(
                nameInput,
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                descriptionInput,
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 16 },
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Upload OpenCV template")
            .setMessage("Choose a clear screenshot crop of the approval target.")
            .setView(container)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("UPLOAD") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    nameInput.error = "Enter a template name."
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    runCatching {
                        templates.add(
                            name = name,
                            description = descriptionInput.text?.toString().orEmpty(),
                            uri = uri,
                            createdBy = admin?.email.orEmpty(),
                        )
                    }.onSuccess {
                        refreshTemplates()
                        showMessage("Template uploaded", "The image is ready for detection.")
                    }.onFailure {
                        showMessage("Upload failed", it.message ?: "Unable to save this image.")
                    }
                }
            }
            .show()
    }

    private fun confirmDeleteTemplate(id: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete template?")
            .setMessage("This removes the image from the local detector.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                lifecycleScope.launch {
                    runCatching { templates.delete(id) }
                        .onFailure { showMessage("Delete failed", it.message.orEmpty()) }
                        .onSuccess { refreshTemplates() }
                }
            }
            .show()
    }

    private fun approve(user: User, days: Int) {
        lifecycleScope.launch {
            runCatching { users.approve(user.id, days) }
                .onFailure { showMessage("Could not approve", it.message.orEmpty()) }
                .onSuccess { refreshAll() }
        }
    }

    private fun reject(user: User) {
        lifecycleScope.launch {
            runCatching { users.reject(user.id) }
                .onFailure { showMessage("Could not reject", it.message.orEmpty()) }
                .onSuccess { refreshAll() }
        }
    }

    private fun showUserActions(user: User) {
        val options = arrayOf("Approve 7 days", "Approve 30 days", "Approve 365 days", "Edit expiry", "Reject", "Delete")
        MaterialAlertDialogBuilder(this)
            .setTitle(user.email)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> approve(user, 7)
                    1 -> approve(user, 30)
                    2 -> approve(user, 365)
                    3 -> editExpiry(user)
                    4 -> reject(user)
                    5 -> confirmDelete(user)
                }
            }.show()
    }

    private fun editExpiry(user: User) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select expiry date")
            .setSelection(user.expiryDate.takeIf { it > 0L && it != Long.MAX_VALUE }
                ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { date ->
            lifecycleScope.launch {
                users.setExpiry(user.id, date)
                refreshAll()
            }
        }
        picker.show(supportFragmentManager, "expiry")
    }

    private fun confirmDelete(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete account?")
            .setMessage(user.email)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                lifecycleScope.launch { users.delete(user.id); refreshAll() }
            }.show()
    }

    private fun postNotice() {
        val message = binding.noticeInput.text?.toString()?.trim().orEmpty()
        if (message.isBlank()) {
            binding.noticeInput.error = "Enter a message."
            return
        }
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@AdminActivity).noticeDao()
            dao.deleteAll()
            dao.insert(com.ultra.autodetector.data.local.NoticeEntity(message = message))
            binding.noticeInput.text?.clear()
            refreshAll()
            showMessage("Notice posted", "The notice is stored locally and will appear on the dashboard.")
        }
    }

    private fun clearNotice() {
        lifecycleScope.launch {
            AppDatabase.getInstance(this@AdminActivity).noticeDao().deleteAll()
            refreshAll()
        }
    }

    private fun exportUsers() {
        lifecycleScope.launch {
            val list = users.listUsers()
            pendingExport = buildString {
                appendLine("email,status,expiryDate,createdAt,isAdmin,deviceId")
                list.forEach { user ->
                    appendLine(
                        listOf(
                            user.email,
                            user.licenseStatus,
                            user.expiryDate,
                            user.createdAt,
                            user.isAdmin,
                            user.deviceId,
                        ).joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" },
                    )
                }
            }
            createDocument.launch("ultra-users.csv")
        }
    }

    private fun animateCount(target: TextView, value: Int) {
        val start = (target.text?.toString()?.toIntOrNull() ?: 0)
        android.animation.ValueAnimator.ofInt(start, value).apply {
            duration = 420
            addUpdateListener { target.text = it.animatedValue.toString() }
            start()
        }
    }

    private fun showMessage(title: String, message: String, after: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> after?.invoke() }
            .show()
    }

    private class SimpleTextWatcher(private val onChanged: () -> Unit) :
        android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}
