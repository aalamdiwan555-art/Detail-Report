package com.ultra.autodetector.data.repository

import com.ultra.autodetector.BuildConfig
import java.security.MessageDigest

object AdminConfig {
    val ADMIN_EMAIL: String
        get() = BuildConfig.ULTRA_ADMIN_EMAIL.trim()

    private val ADMIN_PASSWORD_HASH: String
        get() = BuildConfig.ULTRA_ADMIN_PASSWORD_HASH.trim()

    val isConfigured: Boolean
        get() = ADMIN_EMAIL.isNotBlank() && ADMIN_PASSWORD_HASH.isNotBlank()

    /**
     * CRITICAL FIX: Consistent password hashing with proper salt
     */
    fun hashPass(pass: String): String {
        val salt = "ultra_salt_2024_v2"
        val input = "$salt$pass".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(input)
            .joinToString("") { "%02x".format(it) }
    }

    fun isReservedEmail(email: String): Boolean =
        isConfigured && email.trim().equals(ADMIN_EMAIL, ignoreCase = true)

    /**
     * CRITICAL FIX: Constant-time comparison to prevent timing attacks
     */
    fun matches(email: String, pass: String): Boolean {
        if (!isConfigured) return false
        val emailMatch = email.trim().equals(ADMIN_EMAIL, ignoreCase = true)
        if (!emailMatch) return false

        val computedHash = hashPass(pass).toByteArray(Charsets.UTF_8)
        val storedHash = ADMIN_PASSWORD_HASH.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(computedHash, storedHash)
    }
}
