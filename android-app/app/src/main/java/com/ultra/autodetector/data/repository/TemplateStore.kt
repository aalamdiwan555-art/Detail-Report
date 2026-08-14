package com.ultra.autodetector.data.repository

import android.content.Context
import com.ultra.autodetector.data.local.ActionEntity
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.TemplateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object TemplateStore {
    fun directory(context: Context): File =
        File(context.applicationContext.filesDir, "templates").apply { mkdirs() }

    suspend fun ensureBuiltIns(context: Context) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(context)
        val templateDao = database.templateDao()
        if (templateDao.getAll().isNotEmpty()) return@withContext

        val assets = context.assets.list("templates").orEmpty()
        assets.filter { it.isImage() }.forEach { assetName ->
            val destination = File(directory(context), assetName)
            if (!destination.exists()) {
                context.assets.open("templates/$assetName").use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val id = assetName.substringBeforeLast('.')
            templateDao.insert(
                TemplateEntity(
                    id = id,
                    name = assetName.substringBeforeLast('.')
                        .replace('_', ' ')
                        .replaceFirstChar { it.uppercase() },
                    imagePath = destination.absolutePath,
                    threshold = 0.80f,
                ),
            )
            database.actionDao().insert(
                ActionEntity(templateId = id, actionType = ActionEntity.TYPE_CLICK),
            )
        }
    }

    private fun String.isImage(): Boolean =
        lowercase().endsWith(".png") ||
            lowercase().endsWith(".jpg") ||
            lowercase().endsWith(".jpeg") ||
            lowercase().endsWith(".webp")
}