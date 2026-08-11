package com.ultra.autodetector.data.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val email: String = "",
    val role: String = "user",
    val status: String = "pending",
    val expirationTimestamp: Long? = null,
    val createdAt: Timestamp? = null,
    val lastLoginAt: Timestamp? = null,
    val deviceInfo: String = "",
) {
    fun isLicenseActive(now: Long = System.currentTimeMillis()): Boolean =
        role == "admin" || (status == "approved" && (expirationTimestamp ?: 0L) > now)
}