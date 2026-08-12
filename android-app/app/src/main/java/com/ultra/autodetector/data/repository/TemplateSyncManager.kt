package com.ultra.autodetector.data.repository

import android.content.Context
import android.content.Intent
import com.ultra.autodetector.data.model.Template
import com.ultra.autodetector.util.Constants
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Local-first template propagation.
 *
 * The admin and detector run in the same application package, so the private
 * shared_templates directory is available to both the Room repository and the
 * long-lived detector services. The explicit package broadcast updates already
 * running processes immediately; WorkManager retries the copy/broadcast after
 * process death or transient storage failures. A remote provider can be added
 * behind this class later without changing callers.
 */
class TemplateSyncManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val sharedDirectory = File(appContext.filesDir, SHARED_DIRECTORY).apply { mkdirs() }

    fun sync(template: Template) {
        copyTemplate(template)
        notifyTemplateUpdated(template.templateId)
        val request = OneTimeWorkRequestBuilder<TemplateSyncWorker>()
            .setInputData(workDataOf(TemplateSyncWorker.KEY_TEMPLATE_ID to template.templateId))
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
    }

    private fun copyTemplate(template: Template) {
        val source = File(template.filePath)
        require(source.exists()) { "Template image is no longer available." }
        val destination = File(sharedDirectory, "${template.templateId}.png")
        val temporary = File(sharedDirectory, "${template.templateId}.tmp")
        source.inputStream().use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        runCatching {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun notifyTemplateUpdated(id: String) {
        appContext.sendBroadcast(
            Intent(Constants.ACTION_TEMPLATE_UPDATED)
                .setPackage(appContext.packageName)
                .putExtra(Constants.EXTRA_TEMPLATE_ID, id),
        )
    }

    companion object {
        const val SHARED_DIRECTORY = "shared_templates"

        fun sharedFile(context: Context, templateId: String): File =
            File(File(context.applicationContext.filesDir, SHARED_DIRECTORY), "$templateId.png")
    }
}

class TemplateSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_TEMPLATE_ID) ?: return Result.failure()
        val template = com.ultra.autodetector.data.local.AppDatabase
            .getInstance(applicationContext)
            .templateDao()
            .findById(id) ?: return Result.success()
        return runCatching {
            applicationContext.sendBroadcast(
                Intent(Constants.ACTION_TEMPLATE_UPDATED)
                    .setPackage(applicationContext.packageName)
                    .putExtra(Constants.EXTRA_TEMPLATE_ID, template.templateId),
            )
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() },
        )
    }

    companion object {
        const val KEY_TEMPLATE_ID = "template_id"
    }
}