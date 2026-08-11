package com.ultra.autodetector.data.local

import android.content.Context
import android.content.Intent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ultra.autodetector.util.Constants

/**
 * Stores only local permission/session metadata. MediaProjection tokens are
 * intentionally not persisted because Android may invalidate them.
 */
class EncryptedPrefsManager(context: Context) {
    private val prefs = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            Constants.PREFS_FILE_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences(Constants.PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    fun isAccessibilityGranted() = prefs.getBoolean(Constants.KEY_ACCESSIBILITY_GRANTED, false)
    fun setAccessibilityGranted(value: Boolean) =
        prefs.edit().putBoolean(Constants.KEY_ACCESSIBILITY_GRANTED, value).apply()
    fun isOverlayGranted() = prefs.getBoolean(Constants.KEY_OVERLAY_GRANTED, false)
    fun setOverlayGranted(value: Boolean) =
        prefs.edit().putBoolean(Constants.KEY_OVERLAY_GRANTED, value).apply()
    fun isMediaProjectionGranted() = prefs.getBoolean(Constants.KEY_MEDIA_PROJECTION_GRANTED, false)
    fun setMediaProjectionGranted(value: Boolean) =
        prefs.edit().putBoolean(Constants.KEY_MEDIA_PROJECTION_GRANTED, value).apply()

    fun saveMediaProjectionData(resultCode: Int, data: Intent) {
        // Do not serialize permission grants across process/device boundaries.
        prefs.edit().putInt(Constants.KEY_MEDIA_PROJECTION_RESULT, resultCode).apply()
    }

    fun getMediaProjectionResultCode(): Int =
        prefs.getInt(Constants.KEY_MEDIA_PROJECTION_RESULT, -1)

    fun getMediaProjectionData(): Intent? = null

    fun clearAll() = prefs.edit().clear().apply()
}