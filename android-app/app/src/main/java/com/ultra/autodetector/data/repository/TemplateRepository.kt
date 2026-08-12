package com.ultra.autodetector.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
            val bitmap = appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read the selected image." }
                BitmapFactory.decodeStream(input)
            }
            requireNotNull(bitmap) { "The selected file is not a supported image." }
            require(bitmap.width > 0 && bitmap.height > 0) { "The selected image is empty." }
            require(bitmap.width <= MAX_TEMPLATE_DIMENSION && bitmap.height <= MAX_TEMPLATE_DIMENSION) {
                "Template images must be ${MAX_TEMPLATE_DIMENSION} px or smaller."
            }
            try {
                destination.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "Unable to save the template image."
                    }
                }
                Template(
                    id,
                    name.trim(),
                    description.trim(),
                    destination.absolutePath,
                    createdBy = createdBy,
                ).also {
                    dao.insert(it)
                    TemplateSyncManager(appContext).sync(it)
                }
            } catch (error: Throwable) {
                destination.delete()
                throw error
            } finally {
                bitmap.recycle()
            }
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val template = dao.findById(id)
        dao.delete(id)
        template?.filePath?.let { File(it).delete() }
    }

    companion object {
        private const val MAX_TEMPLATE_DIMENSION = 4_096
    }

    suspend fun getActiveTemplates(): List<Template> = listActive()
}