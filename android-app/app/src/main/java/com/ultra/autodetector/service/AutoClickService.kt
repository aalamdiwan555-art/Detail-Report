package com.ultra.autodetector.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.ultra.autodetector.R
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.opencv.TextTargetDetector
import com.ultra.autodetector.ui.main.MainActivity
import com.ultra.autodetector.util.Constants
import com.ultra.autodetector.util.HumanizationEngine

/**
 * User-enabled accessibility gesture service.
 *
 * Accessibility services are managed by the system, so they cannot literally
 * be made immortal. START_STICKY, an immediate foreground notification, and
 * no stop-on-task-removal behavior make the service resilient to app task
 * dismissal and ordinary process pressure.
 */
class AutoClickService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var receiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        createForegroundNotification()
        registerClickReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CLICKING) {
            stopClicking()
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !DetectionService.isRunning || DetectionService.isPaused) return
        if (event.packageName?.toString() == packageName) return
        if (!HumanizationEngine.isCooldownPassed()) return

        val match = TextTargetDetector.find(rootInActiveWindow) ?: return
        performUserRequestedClick(match.centerX, match.centerY)
        HumanizationEngine.recordClick()
    }

    override fun onInterrupt() = Unit

    /**
     * Posts the gesture to the main looper so all dispatchGesture calls are
     * serialized on the service thread. A one millisecond stroke is valid on
     * all supported Android versions and behaves as a tap.
     */
    fun performUserRequestedClick(x: Float, y: Float) {
        val delay = HumanizationEngine.getMicroDelay().coerceAtLeast(0L)
        handler.postDelayed({
            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels
            if (width <= 0 || height <= 0) return@postDelayed

            val safeX = x.takeIf { it.isFinite() }?.coerceIn(0f, (width - 1).toFloat()) ?: return@postDelayed
            val safeY = y.takeIf { it.isFinite() }?.coerceIn(0f, (height - 1).toFloat()) ?: return@postDelayed
            acquireWakeLock()

            val path = Path().apply { moveTo(safeX, safeY) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 1L))
                .build()
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        // Keep the wake lock while detection is active. It is
                        // released by STOP_CLICKING or service destruction.
                    }
                },
                handler,
            )
        }, delay)
    }

    private fun createForegroundNotification() {
        val notification = NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.autoclick_notification_title))
            .setContentText(getString(R.string.autoclick_notification_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    901,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerClickReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_PERFORM_CLICK)
            addAction(ACTION_STOP_CLICKING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(clickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(clickReceiver, filter)
        }
        receiverRegistered = true
    }

    private val clickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_PERFORM_CLICK -> performUserRequestedClick(
                    intent.getFloatExtra(Constants.EXTRA_CLICK_X, 0f),
                    intent.getFloatExtra(Constants.EXTRA_CLICK_Y, 0f),
                )
                ACTION_STOP_CLICKING -> stopClicking()
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:autoclick",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun stopClicking() {
        handler.removeCallbacksAndMessages(null)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        stopClicking()
        if (receiverRegistered) runCatching { unregisterReceiver(clickReceiver) }
        receiverRegistered = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_CLICKING = "com.ultra.autodetector.STOP_CLICKING"
        private const val NOTIFICATION_ID = 102

        @Volatile
        var instance: AutoClickService? = null
            private set
    }
}