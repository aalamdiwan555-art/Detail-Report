package com.ultra.autodetector.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Single source of truth for the permissions needed by the detector.
 * System settings are opened only after the user taps the related dialog.
 */
object PermissionHelper {
    fun hasOverlayPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun hasAccessibilityPermission(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val expected = "${context.packageName}/${context.packageName}.service.AutoDetectorService"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(ManifestPermission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun overlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    private object ManifestPermission {
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    }
}