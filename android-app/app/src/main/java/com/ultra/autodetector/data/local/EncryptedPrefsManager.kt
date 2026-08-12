package com.ultra.autodetector.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ultra.autodetector.util.Constants

/**
 * Stores only local permission/session metadata. MediaProjection tokens are
 * intentionally not persisted because Android may invalidate them.
 */
class EncryptedPrefsManager(context: Context) {
    private val prefs = run {
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
    }

    fun getSessionUid(): String? = prefs.getString(Constants.KEY_SESSION_UID, null)
    fun setSessionUid(uid: String?) =
        prefs.edit().apply {
            if (uid == null) remove(Constants.KEY_SESSION_UID) else putString(Constants.KEY_SESSION_UID, uid)
        }.apply()

    fun saveCurrentUserJson(json: String) =
        prefs.edit().putString(KEY_CURRENT_USER_JSON, json).apply()

    fun getCurrentUserJson(): String? = prefs.getString(KEY_CURRENT_USER_JSON, null)

    fun setCurrentNotice(notice: String?) =
        prefs.edit().apply {
            if (notice == null) remove(KEY_CURRENT_NOTICE) else putString(KEY_CURRENT_NOTICE, notice)
        }.apply()

    fun getCurrentNotice(): String? = prefs.getString(KEY_CURRENT_NOTICE, null)

    fun wasDetectorRunning() = prefs.getBoolean(KEY_DETECTOR_WAS_RUNNING, false)
    fun setDetectorWasRunning(value: Boolean) =
        prefs.edit().putBoolean(KEY_DETECTOR_WAS_RUNNING, value).apply()

    fun isAccessibilityGranted() = prefs.getBoolean(Constants.KEY_ACCESSIBILITY_GRANTED, false)
    fun setAccessibilityGranted(value: Boolean) =
        prefs.edit().putBoolean(Constants.KEY_ACCESSIBILITY_GRANTED, value).apply()
    fun isOverlayGranted() = prefs.getBoolean(Constants.KEY_OVERLAY_GRANTED, false)
    fun setOverlayGranted(value: Boolean) =
        prefs.edit().putBoolean(Constants.KEY_OVERLAY_GRANTED, value).apply()
    fun isMediaProjectionGranted() = prefs.getBoolean(Constants.KEY_MEDIA_PROJECTION_GRANTED, false)
    fun setMediaProjectionGranted(value: Boolean) =
        prefs.edit().putBoolean(Constants.KEY_MEDIA_PROJECTION_GRANTED, value).apply()

    fun saveMediaProjectionData(resultCode: Int) {
        // Do not serialize permission grants across process/device boundaries.
        prefs.edit().putInt(Constants.KEY_MEDIA_PROJECTION_RESULT, resultCode).apply()
    }

    fun getMediaProjectionResultCode(): Int =
        prefs.getInt(Constants.KEY_MEDIA_PROJECTION_RESULT, -1)

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_CURRENT_USER_JSON = "current_user_json"
        private const val KEY_CURRENT_NOTICE = "current_notice"
        private const val KEY_DETECTOR_WAS_RUNNING = "detector_was_running"
    }
}