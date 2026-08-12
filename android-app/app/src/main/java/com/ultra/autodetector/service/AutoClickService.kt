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
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.ultra.autodetector.R
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.detector.FastClicker
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
    private lateinit var fastClicker: FastClicker

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        fastClicker = FastClicker(this, handler)
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
        performUserRequestedClick(Rect(match.centerX.toInt(), match.centerY.toInt(), match.centerX.toInt() + 1, match.centerY.toInt() + 1))
        HumanizationEngine.recordClick()
    }

    override fun onInterrupt() = Unit

    /**
     * Posts the gesture to the main looper so all dispatchGesture calls are
     * serialized on the service thread. A one millisecond stroke is valid on
     * all supported Android versions and behaves as a tap.
     */
    fun performUserRequestedClick(x: Float, y: Float) {
        performUserRequestedClick(Rect(x.toInt(), y.toInt(), x.toInt() + 1, y.toInt() + 1))
    }

    fun performUserRequestedClick(rect: Rect) {
        val delay = HumanizationEngine.getMicroDelay().coerceAtLeast(0L)
        handler.postDelayed({
            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels
            if (width <= 0 || height <= 0) return@postDelayed

            val safeRect = Rect(
                rect.left.coerceIn(0, width - 1),
                rect.top.coerceIn(0, height - 1),
                rect.right.coerceIn(1, width),
                rect.bottom.coerceIn(1, height),
            )
            acquireWakeLock()
            fastClicker.click(safeRect)
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
            addAction(Constants.ACTION_TEMPLATE_UPDATED)
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
                    Rect(
                        intent.getIntExtra(Constants.EXTRA_CLICK_LEFT, intent.getFloatExtra(Constants.EXTRA_CLICK_X, 0f).toInt()),
                        intent.getIntExtra(Constants.EXTRA_CLICK_TOP, intent.getFloatExtra(Constants.EXTRA_CLICK_Y, 0f).toInt()),
                        intent.getIntExtra(Constants.EXTRA_CLICK_LEFT, intent.getFloatExtra(Constants.EXTRA_CLICK_X, 0f).toInt()) +
                            intent.getIntExtra(Constants.EXTRA_CLICK_WIDTH, 1),
                        intent.getIntExtra(Constants.EXTRA_CLICK_TOP, intent.getFloatExtra(Constants.EXTRA_CLICK_Y, 0f).toInt()) +
                            intent.getIntExtra(Constants.EXTRA_CLICK_HEIGHT, 1),
                    ),
                )
                ACTION_STOP_CLICKING -> stopClicking()
                Constants.ACTION_TEMPLATE_UPDATED -> {
                    // DetectionService also reloads its Room-backed cache. This
                    // receiver keeps the accessibility side immediately aware
                    // of a new global template generation.
                    android.util.Log.i(TAG, "Template updated; detector cache will reload")
                }
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
        if (::fastClicker.isInitialized) fastClicker.close()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_CLICKING = "com.ultra.autodetector.STOP_CLICKING"
        private const val TAG = "AutoClickService"
        private const val NOTIFICATION_ID = 102

        @Volatile
        var instance: AutoClickService? = null
            private set
    }
}