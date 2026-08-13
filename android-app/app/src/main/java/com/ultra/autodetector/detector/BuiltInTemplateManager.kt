package com.ultra.autodetector.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File

class BuiltInTemplateManager(context: Context) {
    companion object {
        private const val TAG = "BuiltInTemplates"
        private const val ASSET_DIRECTORY = "templates"
        const val DEFAULT_THRESHOLD = 0.80f
    }

    data class Template(
        val id: String,
        val name: String,
        val bitmap: Bitmap,
        val matGray: Mat,
        val isActive: Boolean = true,
    )

    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "builtin_templates")
    private val preferences = appContext.getSharedPreferences("builtin_template_settings", Context.MODE_PRIVATE)

    private val matCache = object : LruCache<String, Mat>(16) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Mat, newValue: Mat?) {
            if (oldValue !== newValue) oldValue.release()
        }
    }

    @Volatile 
    private var templates: List<Template> = emptyList()

    @Synchronized
    fun onCreate() {
        directory.mkdirs()
        val names = appContext.assets.list(ASSET_DIRECTORY)
            .orEmpty()
            .filter { it.isImageFile() }
            .sorted()

        names.forEach { name ->
            val target = File(directory, name)
            if (!target.exists() || target.length() == 0L) {
                try {
                    appContext.assets.open("$ASSET_DIRECTORY/$name").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy template: $name", e)
                }
            }
        }

        val loaded = names.mapNotNull { name ->
            loadTemplate(name)
        }

        templates = loaded
        Log.i(TAG, "Loaded ${loaded.size} built-in templates")
    }

    private fun loadTemplate(name: String): Template? {
        val file = File(directory, name)
        if (!file.exists()) return null

        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val rgba = Mat()
        val gray = Mat()

        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            rgba.release()
            matCache.put(name, gray)
            Template(
                id = name.substringBeforeLast('.'),
                name = name,
                bitmap = bitmap,
                matGray = gray,
                isActive = preferences.getBoolean(activeKey(name), true),
            )
        } catch (e: Exception) {
            rgba.release()
            gray.release()
            bitmap.recycle()
            Log.e(TAG, "Unable to load built-in template $name", e)
            null
        }
    }

    fun getAllTemplates(): List<Template> = templates

    fun setActive(id: String, active: Boolean) {
        preferences.edit().putBoolean(activeKey(id), active).apply()
        templates = templates.map { if (it.id == id) it.copy(isActive = active) else it }
    }

    fun thresholdFor(id: String): Float =
        preferences.getFloat(thresholdKey(id), DEFAULT_THRESHOLD)

    fun setThreshold(id: String, threshold: Float) {
        preferences.edit().putFloat(thresholdKey(id), threshold.coerceIn(0.70f, 0.95f)).apply()
    }

    fun close() {
        templates.forEach { template ->
            matCache.remove(template.name)?.release()
            if (!template.bitmap.isRecycled) template.bitmap.recycle()
        }
        templates = emptyList()
        matCache.evictAll()
    }

    private fun activeKey(id: String) = "active_$id"
    private fun thresholdKey(id: String) = "threshold_$id"

    private fun String.isImageFile(): Boolean =
        lowercase().endsWith(".png") ||
        lowercase().endsWith(".jpg") ||
        lowercase().endsWith(".jpeg") ||
        lowercase().endsWith(".webp")
}
