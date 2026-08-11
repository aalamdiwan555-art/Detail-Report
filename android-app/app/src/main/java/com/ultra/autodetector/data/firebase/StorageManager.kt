package com.ultra.autodetector.data.firebase

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.ultra.autodetector.util.Constants
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Cloud Storage boundary for template images. The server-side rules remain the
 * authority for size, content type, and administrator access.
 */
class StorageManager(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {
    suspend fun uploadTemplateImage(templateId: String, imageUri: Uri): Result<String> = runCatching {
        val reference = storage.reference.child("${Constants.STORAGE_TEMPLATES_PATH}/$templateId.png")
        reference.putFile(imageUri).await()
        reference.downloadUrl.await().toString()
    }

    suspend fun downloadTemplateImage(downloadUrl: String, templateId: String, context: Context): Result<File> =
        runCatching {
            require(downloadUrl.isNotBlank()) { "Template download URL is empty." }
            val directory = File(context.cacheDir, Constants.STORAGE_TEMPLATES_PATH).apply { mkdirs() }
            val localFile = File(directory, "$templateId.png")
            storage.getReferenceFromUrl(downloadUrl).getFile(localFile).await()
            localFile
        }

    suspend fun deleteTemplateImage(templateId: String): Result<Unit> = runCatching {
        storage.reference
            .child("${Constants.STORAGE_TEMPLATES_PATH}/$templateId.png")
            .delete()
            .await()
    }
}