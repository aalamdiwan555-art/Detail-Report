package com.ultra.autodetector.auth

import android.content.Context

data class UserAccount(
    val email: String,
    val isAdmin: Boolean = false
)

class AuthRepository(private val context: Context) {

    // Yahan apne admin emails add kar de
    private val adminEmails = listOf(
        "diwanatik84@gmail.com",
        "aalamdiwan555@gmail.com",
        "admin@ultra.com"
    )

    fun currentUser(): UserAccount? {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null) ?: "diwanatik84@gmail.com"
        val isAdmin = adminEmails.contains(email.trim().lowercase())
        return UserAccount(email = email, isAdmin = isAdmin)
    }

    fun login(email: String, pass: String) {
        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        prefs.edit().putString("email", email.trim().lowercase()).apply()
    }

    fun hasActiveLicense(): Boolean {
        val user = currentUser()
        return user?.isAdmin == true || true // admin hamesha active
    }

    fun remainingLabel(): String {
        return if (currentUser()?.isAdmin == true) "Administrator Access - Unlimited" else "Local Mode - Active"
    }

    fun logout() {
        context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
