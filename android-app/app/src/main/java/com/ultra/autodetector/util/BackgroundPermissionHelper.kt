package com.ultra.autodetector.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object BackgroundPermissionHelper {
    data class Status(
        val accessibility: Boolean,
        val overlay: Boolean,
        val notifications: Boolean,
    ) {
        val mainPermissionsGranted: Boolean
            get() = accessibility && overlay
    }

    fun status(context: Context): Status = Status(
        accessibility = isAccessibilityEnabled(context),
        overlay = canDrawOverlays(context),
        notifications = areNotificationsEnabled(context),
    )

    fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).any { service ->
            val info = service.resolveInfo?.serviceInfo ?: return@any false
            info.packageName == context.packageName &&
            info.name == "com.ultra.autodetector.service.AutoDetectorService"
        }
    }

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun areNotificationsEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun accessibilityIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"))
}
