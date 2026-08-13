package com.ultra.autodetector.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ultra.autodetector.util.Constants

class EncryptedPrefsManager(context: Context) {
    private val appContext = context.applicationContext

    private val fallbackPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(
            "${Constants.PREFS_FILE_NAME}_fallback",
            Context.MODE_PRIVATE,
        )
    }

    private val prefs: SharedPreferences = runCatching {
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

    fun isPermissionOnboardingComplete(userId: String): Boolean =
        readString("$KEY_PERMISSION_ONBOARDING_PREFIX$userId") == "true"

    fun setPermissionOnboardingComplete(userId: String, complete: Boolean) =
        writeString("$KEY_PERMISSION_ONBOARDING_PREFIX$userId", complete.toString())

    fun clearAll() {
        runCatching { prefs.edit().clear().apply() }
        runCatching { fallbackPrefs.edit().clear().apply() }
    }

    companion object {
        private const val KEY_CURRENT_USER_JSON = "current_user_json"
        private const val KEY_CURRENT_NOTICE = "current_notice"
        private const val KEY_PERMISSION_ONBOARDING_PREFIX = "permission_onboarding_"
    }
}
