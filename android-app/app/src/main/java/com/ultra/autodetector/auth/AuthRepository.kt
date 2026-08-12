package com.ultra.autodetector.auth

import android.content.Context

data class UserAccount(
    val email: String,
    val isAdmin: Boolean = false
)

class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun currentUser(): UserAccount? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return UserAccount(email = email, isAdmin = isAdmin())
    }

    /**
     * Local-only authentication used until a remote identity provider is added.
     * Passwords are deliberately not persisted; a successful login creates a
     * local session containing only the normalized email and admin flag.
     */
    fun login(email: String, pass: String): Boolean {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.length < MIN_INPUT_LENGTH || pass.length < MIN_INPUT_LENGTH) {
            return false
        }

        val admin = normalizedEmail == ADMIN_EMAIL && pass == ADMIN_PASSWORD
        prefs.edit()
            .putString(KEY_EMAIL, normalizedEmail)
            .putBoolean(KEY_IS_ADMIN, admin)
            .apply()
        return true
    }

    fun isLoggedIn(): Boolean = prefs.contains(KEY_EMAIL)

    fun isAdmin(): Boolean =
        isLoggedIn() && prefs.getBoolean(KEY_IS_ADMIN, false)

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun hasActiveLicense(): Boolean {
        return isLoggedIn()
    }

    fun remainingLabel(): String {
        return if (isAdmin()) "Administrator Access - Unlimited" else "Local Mode - Active"
    }

    companion object {
        private const val PREFS_NAME = "auth"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val MIN_INPUT_LENGTH = 4
        private const val ADMIN_EMAIL = "divanatik84@gmail.com"
        private const val ADMIN_PASSWORD = "1qwwq11qw"
    }
}
