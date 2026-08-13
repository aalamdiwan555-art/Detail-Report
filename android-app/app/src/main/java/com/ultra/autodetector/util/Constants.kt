package com.ultra.autodetector.util

/**
 * Central application configuration. Administrator credentials are never
 * accepted from the client prompt or stored as plaintext in the APK.
 */
object Constants {
    const val PREFS_FILE_NAME = "ultra_secure_prefs"
    const val KEY_SESSION_UID = "session_uid"
}