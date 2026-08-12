package com.ultra.autodetector.util

/**
 * Central application configuration. Administrator credentials are never
 * accepted from the client prompt or stored as plaintext in the APK.
 */
object Constants {
    const val TELEGRAM_ADMIN_USERNAME = "dminofclicker"
    const val TELEGRAM_DEEP_LINK = "https://t.me/dminofclicker"
    const val ROLE_USER = "user"
    const val ROLE_ADMIN = "admin"
    const val ONE_DAY_MS = 86_400_000L
    const val LIFETIME_MS = Long.MAX_VALUE
    const val CONFIDENCE_THRESHOLD = 0.85
    const val JITTER_RANGE_PX = 2
    const val COOLDOWN_INTERVAL_MS = 300L

    const val PREFS_FILE_NAME = "ultra_secure_prefs"
    const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
    const val KEY_ACCESSIBILITY_GRANTED = "accessibility_granted"
    const val KEY_OVERLAY_GRANTED = "overlay_granted"
    const val KEY_MEDIA_PROJECTION_GRANTED = "media_projection_granted"
    const val KEY_MEDIA_PROJECTION_RESULT = "media_projection_result_code"
    const val KEY_SESSION_UID = "session_uid"

    const val ACTION_PERFORM_CLICK = "com.ultra.autodetector.PERFORM_CLICK"
    const val ACTION_TEMPLATE_UPDATED = "com.ultra.autodetector.TEMPLATE_UPDATED"
    const val ACTION_START_DETECTION = "com.ultra.autodetector.START_DETECTION"
    const val ACTION_STOP_DETECTION = "com.ultra.autodetector.STOP_DETECTION"
    const val ACTION_PAUSE_DETECTION = "com.ultra.autodetector.PAUSE_DETECTION"
    const val ACTION_SHOW_FLOATING_WIDGET = "com.ultra.autodetector.SHOW_FLOATING_WIDGET"
    const val ACTION_HIDE_FLOATING_WIDGET = "com.ultra.autodetector.HIDE_FLOATING_WIDGET"
    const val EXTRA_CLICK_X = "click_x"
    const val EXTRA_CLICK_Y = "click_y"
    const val EXTRA_CLICK_LEFT = "click_left"
    const val EXTRA_CLICK_TOP = "click_top"
    const val EXTRA_CLICK_WIDTH = "click_width"
    const val EXTRA_CLICK_HEIGHT = "click_height"
    const val EXTRA_TEMPLATE_ID = "template_id"
    const val REQUEST_MEDIA_PROJECTION = 1001
    const val REQUEST_OVERLAY_PERMISSION = 1002
    const val REQUEST_TEMPLATE_IMAGE = 1003

    const val TELEGRAM_MESSAGE_TEMPLATE =
        "ACCOUNT RENEWAL REQUEST\nEmail: %s\nStatus: %s\nUID: %s\nDevice: %s\nAndroid: %s\nRequest Time: %s"
}