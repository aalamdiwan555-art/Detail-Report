package com.ultra.autodetector.ui.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ultra.autodetector.R
import com.ultra.autodetector.data.local.ActionEntity
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.TemplateEntity
import com.ultra.autodetector.data.local.UserEntity
import com.ultra.autodetector.data.repository.TemplateStore
import com.ultra.autodetector.databinding.ActivityAdminBinding
import com.ultra.autodetector.ui.logs.LogsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private val database by lazy { AppDatabase.getInstance(this) }
    private val settings by lazy { getSharedPreferences("detector_settings", MODE_PRIVATE) }
    private val pickImageLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {
            it?.let { uri -> showTemplateEditor(null, uri) }
        }
    private val exportLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) exportLogs(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.globalEnableSwitch.isChecked = settings.getBoolean("global_enabled", true)
        binding.globalEnableSwitch.setOnCheckedChangeListener { _, checked ->
            settings.edit().putBoolean("global_enabled", checked).apply()
            Toast.makeText(this, if (checked) "Detection enabled" else "Detection disabled", Toast.LENGTH_SHORT).show()
        }
        binding.intervalSeekBar.progress =
            ((settings.getLong("interval_ms", 500L) - 100L) / 100L).toInt().coerceIn(0, 19)
        updateIntervalLabel()
        binding.intervalSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                settings.edit().putLong("interval_ms", 100L + progress * 100L).apply()
                updateIntervalLabel()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddTemplate.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
        }
        binding.btnClearData.setOnClickListener { confirmClearData() }
        binding.btnExportLogs.setOnClickListener { exportLauncher.launch("ultra-detection-logs.txt") }
        binding.btnOpenLogs.setOnClickListener { startActivity(Intent(this, LogsActivity::class.java)) }
        binding.btnManageUsers.setOnClickListener { showUserManagement() }
        refreshTemplates()
    }

    private fun updateIntervalLabel() {
        binding.intervalLabel.text =
            "Capture interval: ${settings.getLong("interval_ms", 500L)} ms"
    }

    private fun refreshTemplates() {
        lifecycleScope.launch {
            val templates = withContext(Dispatchers.IO) { database.templateDao().getAll() }
            binding.templatesContainer.removeAllViews()
            if (templates.isEmpty()) {
                binding.templatesContainer.addView(TextView(this@AdminActivity).apply {
                    text = "No templates yet. Add a screenshot to begin."
                    setTextColor(getColor(R.color.muted))
                    setPadding(0, 24, 0, 24)
                })
            } else {
                templates.forEach { binding.templatesContainer.addView(templateRow(it)) }
            }
        }
    }

    private fun templateRow(template: TemplateEntity): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
            addView(TextView(this@AdminActivity).apply {
                text = template.name
                textSize = 17f
                setTextColor(getColor(R.color.primary))
            })
            addView(TextView(this@AdminActivity).apply {
                text = "${(template.threshold * 100).toInt()}% threshold • ${if (template.enabled) "Enabled" else "Disabled"}"
                setTextColor(getColor(R.color.muted))
            })
            addView(LinearLayout(this@AdminActivity).apply {
                addView(MaterialButton(this@AdminActivity).apply {
                    text = "EDIT"
                    setOnClickListener { showTemplateEditor(template, null) }
                })
                addView(MaterialButton(this@AdminActivity).apply {
                    text = "DELETE"
                    setTextColor(getColor(R.color.error))
                    setOnClickListener { confirmDelete(template) }
                })
            })
        }

    private fun showTemplateEditor(existing: TemplateEntity?, imageUri: Uri?) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Template name"
            setText(existing?.name.orEmpty())
        }
        val thresholdLabel = TextView(this).apply {
            text = "Confidence threshold: ${((existing?.threshold ?: 0.8f) * 100).toInt()}%"
            setTextColor(getColor(R.color.muted))
        }
        val threshold = SeekBar(this).apply {
            max = 45
            progress = (((existing?.threshold ?: 0.8f) - 0.5f) * 100).toInt().coerceIn(0, 45)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    thresholdLabel.text = "Confidence threshold: ${50 + progress}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val enabled = SwitchMaterial(this).apply {
            text = "Template enabled"
            isChecked = existing?.enabled ?: true
        }
        val action = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AdminActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(ActionEntity.TYPE_CLICK, ActionEntity.TYPE_SWIPE),
            )
        }
        val paramsInput = EditText(this).apply {
            hint = "Swipe coordinates x1,y1,x2,y2 (optional)"
            setText("")
        }
        root.addView(nameInput)
        root.addView(thresholdLabel)
        root.addView(threshold)
        root.addView(enabled)
        root.addView(action)
        root.addView(paramsInput)

        lifecycleScope.launch {
            existing?.let {
                val saved = withContext(Dispatchers.IO) { database.actionDao().getForTemplate(it.id) }
                saved?.let { selected ->
                    action.setSelection(if (selected.actionType == ActionEntity.TYPE_SWIPE) 1 else 0)
                    paramsInput.setText(selected.parameters)
                }
            }
            MaterialAlertDialogBuilder(this@AdminActivity)
                .setTitle(if (existing == null) "Add template" else "Edit template")
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    saveTemplate(existing, imageUri, nameInput.text.toString(), threshold.progress, enabled.isChecked, action.selectedItem.toString(), paramsInput.text.toString())
                }
                .show()
        }
    }

    private fun saveTemplate(
        existing: TemplateEntity?,
        imageUri: Uri?,
        name: String,
        thresholdProgress: Int,
        enabled: Boolean,
        actionType: String,
        parameters: String,
    ) {
        val safeName = name.trim().ifBlank { "Template ${System.currentTimeMillis()}" }
        lifecycleScope.launch(Dispatchers.IO) {
            val id = existing?.id ?: UUID.randomUUID().toString()
            val imagePath = existing?.imagePath ?: File(TemplateStore.directory(this@AdminActivity), "$id.png").absolutePath
            if (imageUri != null) {
                contentResolver.openInputStream(imageUri)?.use { input ->
                    File(imagePath).outputStream().use { output -> input.copyTo(output) }
                }
            }
            database.templateDao().insert(
                TemplateEntity(
                    id = id,
                    name = safeName,
                    imagePath = imagePath,
                    threshold = ((50 + thresholdProgress).coerceIn(50, 95)) / 100f,
                    enabled = enabled,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                ),
            )
            database.actionDao().deleteForTemplate(id)
            database.actionDao().insert(ActionEntity(templateId = id, actionType = actionType, parameters = parameters.trim()))
            withContext(Dispatchers.Main) { refreshTemplates() }
        }
    }

    private fun confirmDelete(template: TemplateEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete ${template.name}?")
            .setMessage("The screenshot and its action will be removed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.templateDao().delete(template)
                    database.actionDao().deleteForTemplate(template.id)
                    File(template.imagePath).delete()
                    withContext(Dispatchers.Main) { refreshTemplates() }
                }
            }
            .show()
    }

    private fun confirmClearData() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear detector data?")
            .setMessage("This removes all templates, actions, and logs.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.templateDao().deleteAll()
                    database.actionDao().deleteAll()
                    database.logDao().deleteAll()
                    TemplateStore.directory(this@AdminActivity).listFiles()?.forEach { it.delete() }
                    withContext(Dispatchers.Main) { refreshTemplates() }
                }
            }
            .show()
    }

    private fun showUserManagement() {
        lifecycleScope.launch {
            val users = withContext(Dispatchers.IO) { database.userDao().getAll() }
            val list = LinearLayout(this@AdminActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(26, 8, 26, 0)
            }

            if (users.isEmpty()) {
                list.addView(TextView(this@AdminActivity).apply {
                    text = "No accounts have been created yet."
                    setTextColor(getColor(R.color.muted))
                    setPadding(0, 18, 0, 18)
                })
            } else {
                users.forEach { user ->
                    val row = LinearLayout(this@AdminActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 12, 0, 12)
                    }
                    row.addView(TextView(this@AdminActivity).apply {
                        text = user.email
                        textSize = 16f
                        setTextColor(getColor(R.color.white))
                    })
                    row.addView(TextView(this@AdminActivity).apply {
                        text = if (user.isAdmin) "ADMIN • Lifetime access" else user.remainingLabel()
                        setTextColor(getColor(R.color.muted))
                    })
                    if (!user.isAdmin) {
                        val actions = LinearLayout(this@AdminActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }
                        actions.addView(MaterialButton(this@AdminActivity).apply {
                            text = "APPROVE 30 DAYS"
                            setOnClickListener {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    database.userDao().updateStatus(
                                        user.id,
                                        UserEntity.STATUS_APPROVED,
                                        System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L,
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            this@AdminActivity,
                                            "${user.email} approved",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        showUserManagement()
                                    }
                                }
                            }
                        })
                        actions.addView(MaterialButton(this@AdminActivity).apply {
                            text = "REJECT"
                            setTextColor(getColor(R.color.error))
                            setOnClickListener {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    database.userDao().updateStatus(user.id, UserEntity.STATUS_REJECTED)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            this@AdminActivity,
                                            "${user.email} rejected",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        showUserManagement()
                                    }
                                }
                            }
                        })
                        row.addView(actions)
                    }
                    list.addView(row)
                }
            }

            MaterialAlertDialogBuilder(this@AdminActivity)
                .setTitle("Manage users")
                .setView(ScrollView(this@AdminActivity).apply { addView(list) })
                .setPositiveButton("DONE", null)
                .show()
        }
    }

    private fun exportLogs(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val logs = database.logDao().recent(2000).asReversed()
            val text = logs.joinToString("\n") {
                "${java.util.Date(it.createdAt)} [${it.level}] ${it.message}" +
                    (it.confidence?.let { confidence -> " confidence=${"%.2f".format(confidence)}" } ?: "")
            }
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AdminActivity, "Logs exported", Toast.LENGTH_SHORT).show()
            }
        }
    }
}