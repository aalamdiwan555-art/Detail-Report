package com.ultra.autodetector.ui.logs

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ultra.autodetector.R
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.databinding.ActivityLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class LogsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogsBinding
    private val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnClearLogs.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                database.logDao().deleteAll()
                withContext(Dispatchers.Main) { renderLogs() }
            }
        }
        renderLogs()
    }

    private fun renderLogs() {
        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) { database.logDao().recent(500) }
            binding.logsContainer.removeAllViews()
            if (logs.isEmpty()) {
                binding.logsContainer.addView(TextView(this@LogsActivity).apply {
                    text = "No detection events yet."
                    setTextColor(getColor(R.color.muted))
                    setPadding(0, 24, 0, 24)
                })
            } else {
                val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                logs.forEach { log ->
                    binding.logsContainer.addView(TextView(this@LogsActivity).apply {
                        text = "${formatter.format(Date(log.createdAt))}  [${log.level}]\n${log.message}"
                        setTextColor(
                            getColor(if (log.level == "ERROR") R.color.error else R.color.muted),
                        )
                        setPadding(0, 10, 0, 10)
                    })
                }
            }
        }
    }
}