package com.ultra.autodetector.util

import android.content.Context

/**
 * Start-gate used by MainActivity. MediaProjection is intentionally supplied
 * by the activity because Android grants it through a one-time user consent
 * result rather than a normal manifest permission.
 */
object PermissionHelper {
    fun allGranted(context: Context, mediaProjectionReady: Boolean): Boolean {
        val status = BackgroundPermissionHelper.status(context)
        return status.accessibility &&
            status.overlay &&
            status.batteryOptimization &&
            status.notifications &&
            mediaProjectionReady
    }
}