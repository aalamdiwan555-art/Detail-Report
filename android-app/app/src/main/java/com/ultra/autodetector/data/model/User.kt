package com.ultra.autodetector.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val uid: String,
    val email: String,
    val passwordHash: String,
    val role: String = "user",
    val status: String = LicenseStatus.PENDING.wireValue,
    val expirationTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null,
    val deviceInfo: String = "",
) {
    val licenseStatus: LicenseStatus get() = LicenseStatus.fromWireValue(status)
    val isAdmin: Boolean get() = role == "admin"

    fun hasActiveLicense(now: Long = System.currentTimeMillis()): Boolean =
        isAdmin || (licenseStatus == LicenseStatus.APPROVED && (expirationTimestamp ?: 0L) > now)

    fun remainingLabel(now: Long = System.currentTimeMillis()): String {
        if (isAdmin || expirationTimestamp == Long.MAX_VALUE) return "Lifetime access"
        val remaining = (expirationTimestamp ?: return "Awaiting approval") - now
        if (remaining <= 0L) return "Expired"
        val days = remaining / 86_400_000L
        val hours = (remaining % 86_400_000L) / 3_600_000L
        return "${days}d ${hours}h remaining"
    }
}