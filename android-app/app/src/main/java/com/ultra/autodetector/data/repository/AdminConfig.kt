package com.ultra.autodetector.data.repository

import com.ultra.autodetector.BuildConfig
import java.security.MessageDigest

/**
 * Administrator material is supplied at build time through ULTRA_ADMIN_EMAIL
 * and ULTRA_ADMIN_PASSWORD_HASH (or matching Gradle properties). It is
 * deliberately not embedded in source control.
 */
object AdminConfig {
    private const val LOCAL_ADMIN_EMAIL = "divanatik84@gmail.com"
    private const val LOCAL_ADMIN_PASSWORD = "1qwwq11qw"

    val ADMIN_EMAIL: String
        get() = BuildConfig.ULTRA_ADMIN_EMAIL.trim().ifBlank { LOCAL_ADMIN_EMAIL }

    private val ADMIN_PASSWORD_HASH: String
        get() = BuildConfig.ULTRA_ADMIN_PASSWORD_HASH.trim()

    val isConfigured: Boolean
        get() = ADMIN_EMAIL.isNotBlank() &&
            (ADMIN_PASSWORD_HASH.isNotBlank() || LOCAL_ADMIN_PASSWORD.isNotBlank())

    fun hashPass(pass: String): String {
        val input = "ultra_salt_2024$pass".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(input)
            .joinToString("") { "%02x".format(it) }
    }

    fun isReservedEmail(email: String): Boolean =
        isConfigured && email.trim().equals(ADMIN_EMAIL, ignoreCase = true)

    fun matches(email: String, pass: String): Boolean =
        isConfigured && email.trim().equals(ADMIN_EMAIL, ignoreCase = true) &&
            if (ADMIN_PASSWORD_HASH.isNotBlank()) {
                MessageDigest.isEqual(
                    hashPass(pass).toByteArray(Charsets.UTF_8),
                    ADMIN_PASSWORD_HASH.toByteArray(Charsets.UTF_8),
                )
            } else {
                pass == LOCAL_ADMIN_PASSWORD
            }
}