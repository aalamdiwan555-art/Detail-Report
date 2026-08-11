package com.ultra.autodetector.data.local

import android.content.Context

/**
 * Compatibility facade for the blueprint's PermissionCacheManager name.
 *
 * The imported project centralizes encrypted permission metadata in
 * [EncryptedPrefsManager], so this class intentionally delegates instead of
 * maintaining a second cache with divergent state.
 */
class PermissionCacheManager(context: Context) {
    private val encryptedPrefs = EncryptedPrefsManager(context)

    fun isAccessibilityGranted(): Boolean = encryptedPrefs.isAccessibilityGranted()

    fun setAccessibilityGranted(value: Boolean) {
        encryptedPrefs.setAccessibilityGranted(value)
    }

    fun isOverlayGranted(): Boolean = encryptedPrefs.isOverlayGranted()

    fun setOverlayGranted(value: Boolean) {
        encryptedPrefs.setOverlayGranted(value)
    }

    fun isMediaProjectionGranted(): Boolean = encryptedPrefs.isMediaProjectionGranted()

    fun setMediaProjectionGranted(value: Boolean) {
        encryptedPrefs.setMediaProjectionGranted(value)
    }
}