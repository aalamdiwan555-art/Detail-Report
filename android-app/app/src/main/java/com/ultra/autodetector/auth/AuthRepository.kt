package com.ultra.autodetector.auth

import android.content.Context

data class UserAccount(
    val email: String,
    val isAdmin: Boolean = false
)

class AuthRepository(private val context: Context) {

    fun currentUser(): UserAccount? {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null) ?: "user@local.com"
        // Agar login nahi hai to bhi local user dega taaki app crash na ho
        return UserAccount(email = email, isAdmin = false)
    }

    fun hasActiveLicense(): Boolean {
        return true // Local mode me hamesha active
    }

    fun remainingLabel(): String {
        return "Local Mode - No Expiry"
    }

    fun logout() {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
