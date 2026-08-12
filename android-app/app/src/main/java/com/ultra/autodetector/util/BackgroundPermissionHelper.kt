package com.ultra.autodetector.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Centralizes permissions which are required for a reliable user-started
 * background detector. Vendor auto-start controls are best-effort because
 * Android does not expose a standard API for them.
 */
object BackgroundPermissionHelper {
    data class Status(
        val accessibility: Boolean,
        val overlay: Boolean,
        val batteryOptimization: Boolean,
        val notifications: Boolean,
        val autoStart: Boolean,
    ) {
        val mainPermissionsGranted: Boolean
            get() = accessibility && overlay && batteryOptimization
    }

    fun status(context: Context): Status = Status(
        accessibility = isAccessibilityEnabled(context),
        overlay = canDrawOverlays(context),
        batteryOptimization = isIgnoringBatteryOptimizations(context),
        notifications = areNotificationsEnabled(context),
        autoStart = isAutoStartSupported(context).not(),
    )

    fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).any { service ->
            val info = service.resolveInfo?.serviceInfo ?: return@any false
            info.packageName == context.packageName &&
                info.name == "com.ultra.autodetector.service.AutoClickService"
        }
    }

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)

    fun areNotificationsEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun accessibilityIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )

    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> listOf(
                Intent("miui.intent.action.OP_AUTO_START").setData(appUri(context)),
                Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST"),
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                Intent("com.coloros.safecenter.action自启动管理").setData(appUri(context)),
                Intent("com.oppo.safe.permission.PermissionActivity").setData(appUri(context)),
            )
            manufacturer.contains("vivo") -> listOf(
                Intent("com.vivo.permissionmanager").setData(appUri(context)),
                Intent("com.iqoo.secure").setData(appUri(context)),
            )
            manufacturer.contains("samsung") -> listOf(
                Intent("com.samsung.android.sm.ACTION_AUTO_RUN").setData(appUri(context)),
            )
            else -> emptyList()
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(context))
        (candidates + fallback).firstOrNull { intent ->
            intent.resolveActivity(context.packageManager) != null
        }?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun isAutoStartSupported(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("oppo") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("vivo") ||
            manufacturer.contains("samsung")
    }

    private fun appUri(context: Context): Uri =
        Uri.parse("package:${context.packageName}")
}