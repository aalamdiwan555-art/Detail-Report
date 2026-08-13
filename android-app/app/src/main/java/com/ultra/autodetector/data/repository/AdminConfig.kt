package com.ultra.autodetector.data.repository

import com.ultra.autodetector.BuildConfig
import java.security.MessageDigest

object AdminConfig {
    private val ADMIN_PASSWORD_HASH: String
        get() = BuildConfig.ULTRA_ADMIN_PASSWORD_HASH.trim()

    val isConfigured: Boolean
        get() = ADMIN_PASSWORD_HASH.isNotBlank()

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

    /**
     * CRITICAL FIX: Constant-time comparison to prevent timing attacks
     */
    fun matchesPassword(pass: String): Boolean {
        if (!isConfigured) return false
        val computedHash = hashPass(pass).toByteArray(Charsets.UTF_8)
        val storedHash = ADMIN_PASSWORD_HASH.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(computedHash, storedHash)
    }
}
