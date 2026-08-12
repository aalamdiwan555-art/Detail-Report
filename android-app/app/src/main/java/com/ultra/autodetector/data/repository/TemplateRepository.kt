package com.ultra.autodetector.data.repository

import android.content.Context
import android.net.Uri
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.model.Template
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TemplateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).templateDao()
    private val directory = File(appContext.filesDir, "templates").apply { mkdirs() }

    suspend fun listAll(): List<Template> = withContext(Dispatchers.IO) { dao.listAll() }
    suspend fun listActive(): List<Template> = withContext(Dispatchers.IO) { dao.listActive() }

    suspend fun add(name: String, description: String, uri: Uri, createdBy: String): Template =
        withContext(Dispatchers.IO) {
            require(name.isNotBlank()) { "Template name is required." }
            val id = UUID.randomUUID().toString()
            val destination = File(directory, "$id.png")
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read the selected image." }
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            Template(id, name.trim(), description.trim(), destination.absolutePath, createdBy = createdBy)
                .also { dao.insert(it) }
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val template = dao.findById(id)
        dao.delete(id)
        template?.filePath?.let { File(it).delete() }
    }
}