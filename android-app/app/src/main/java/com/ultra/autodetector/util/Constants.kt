package com.ultra.autodetector.util

/**
 * Central application configuration. Secrets and administrator credentials must
 * be provisioned by Firebase/BuildConfig, never stored in the APK.
 */
object Constants {
    const val TELEGRAM_ADMIN_USERNAME = "dminofclicker"
    const val TELEGRAM_DEEP_LINK = "https://t.me/dminofclicker"

    const val COLLECTION_USERS = "users"
    const val COLLECTION_TEMPLATES = "templates"
    const val COLLECTION_ADMIN_LOGS = "adminLogs"
    const val STORAGE_TEMPLATES_PATH = "templates"

    const val ROLE_USER = "user"
    const val ROLE_ADMIN = "admin"
    const val STATUS_PENDING = "pending"
    const val STATUS_APPROVED = "approved"
    const val STATUS_REJECTED = "rejected"
    const val STATUS_EXPIRED = "expired"

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
    const val KEY_MEDIA_PROJECTION_DATA = "media_projection_intent_data"
    const val KEY_MEDIA_PROJECTION_RESULT = "media_projection_result_code"

    const val ACTION_START_DETECTION = "com.ultra.autodetector.START_DETECTION"
    const val ACTION_STOP_DETECTION = "com.ultra.autodetector.STOP_DETECTION"
    const val ACTION_SHOW_FLOATING_WIDGET = "com.ultra.autodetector.SHOW_FLOATING_WIDGET"
    const val ACTION_HIDE_FLOATING_WIDGET = "com.ultra.autodetector.HIDE_FLOATING_WIDGET"
    const val REQUEST_MEDIA_PROJECTION = 1001
    const val REQUEST_OVERLAY_PERMISSION = 1002
    const val REQUEST_TEMPLATE_IMAGE = 1003

    const val TELEGRAM_MESSAGE_TEMPLATE =
        "Hello Admin, I want to renew my Ultra AutoDetector account. " +
            "Email: %s, Status: %s, UID: %s, Device: %s, Request Time: %s."
}