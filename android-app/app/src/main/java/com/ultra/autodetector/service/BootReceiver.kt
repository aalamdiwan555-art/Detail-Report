package com.ultra.autodetector.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.ultra.autodetector.data.local.EncryptedPrefsManager

/**
 * Android invalidates MediaProjection tokens across reboot. We therefore
 * restore only the user-facing floating controls; a fresh capture consent is
 * still required before DetectionService can run again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)
        ) return
        if (!EncryptedPrefsManager(context).wasDetectorRunning()) return
        context.startService(
            Intent(context, FloatingWidgetService::class.java)
                .setAction(FloatingWidgetService.ACTION_BOOT_RECOVERY),
        )
    }
}