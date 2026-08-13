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
    private val appContext = context.applicationContext
    private val fallbackPrefs by lazy {
        appContext.getSharedPreferences(
            "${Constants.PREFS_FILE_NAME}_fallback",
            Context.MODE_PRIVATE,
        )
    }
    private val prefs = runCatching {
        val key = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            Constants.PREFS_FILE_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Keystore can be unavailable on emulators, restored backups, or after
        // a damaged key. Local session metadata remains usable without it.
        fallbackPrefs
    }

    private fun readString(key: String): String? =
        runCatching { prefs.getString(key, null) }.getOrElse { fallbackPrefs.getString(key, null) }

    private fun writeString(key: String, value: String?) {
        runCatching {
            prefs.edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
        }
        // Keep a recovery copy so a later Keystore failure does not log the
        // user out. This is metadata only; authentication still uses Room.
        runCatching {
            fallbackPrefs.edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
        }
    }

    fun getSessionUid(): String? = readString(Constants.KEY_SESSION_UID)
    fun setSessionUid(uid: String?) = writeString(Constants.KEY_SESSION_UID, uid)

    fun saveCurrentUserJson(json: String) = writeString(KEY_CURRENT_USER_JSON, json)
    fun getCurrentUserJson(): String? = readString(KEY_CURRENT_USER_JSON)

    fun setCurrentNotice(notice: String?) = writeString(KEY_CURRENT_NOTICE, notice)
    fun getCurrentNotice(): String? = readString(KEY_CURRENT_NOTICE)

    fun clearAll() {
        runCatching { prefs.edit().clear().apply() }
        runCatching { fallbackPrefs.edit().clear().apply() }
    }

    companion object {
        private const val KEY_CURRENT_USER_JSON = "current_user_json"
        private const val KEY_CURRENT_NOTICE = "current_notice"
    }
}