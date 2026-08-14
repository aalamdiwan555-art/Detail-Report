package com.ultra.autodetector.util

import android.content.Context
import android.content.Intent

object BackgroundPermissionHelper {
    data class Status(
        val accessibility: Boolean,
        val overlay: Boolean,
        val notifications: Boolean,
    ) {
        val mainPermissionsGranted: Boolean
            get() = accessibility && overlay && notifications

        val allGranted: Boolean
            get() = accessibility && overlay && notifications
    }

    fun status(context: Context): Status = Status(
        accessibility = isAccessibilityEnabled(context),
        overlay = canDrawOverlays(context),
        notifications = areNotificationsEnabled(context),
    )

    fun isAccessibilityEnabled(context: Context): Boolean =
        PermissionHelper.hasAccessibilityPermission(context)

    fun canDrawOverlays(context: Context): Boolean =
        PermissionHelper.hasOverlayPermission(context)

    fun areNotificationsEnabled(context: Context): Boolean =
        PermissionHelper.hasNotificationPermission(context)

    fun accessibilityIntent(): Intent =
        PermissionHelper.accessibilityIntent()

    fun overlayIntent(context: Context): Intent =
        PermissionHelper.overlayIntent(context)
}
