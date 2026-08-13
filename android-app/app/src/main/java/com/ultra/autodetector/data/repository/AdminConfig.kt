package com.ultra.autodetector.data.repository

import java.security.MessageDigest

object AdminConfig {
    /**
     * Consistent password hashing with a stable app-local salt.
     */
    fun hashPass(pass: String): String {
        val salt = "ultra_salt_2024_v2"
        val input = "$salt$pass".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(input)
            .joinToString("") { "%02x".format(it) }
    }
}
