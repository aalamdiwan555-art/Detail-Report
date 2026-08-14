package com.ultra.autodetector.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ultra.autodetector.data.model.User
import java.util.concurrent.TimeUnit

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)],
)
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val passwordHash: String,
    val role: String = User.ROLE_USER,
    val isApproved: Boolean = true,
    val isAdmin: Boolean = false,
    val licenseStatus: String = STATUS_PENDING,
    val expiryDate: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
) {
    fun hasActiveLicense(now: Long = System.currentTimeMillis()): Boolean =
        isAdmin || (licenseStatus == STATUS_APPROVED && expiryDate > now)

    fun remainingLabel(now: Long = System.currentTimeMillis()): String {
        if (isAdmin || expiryDate == Long.MAX_VALUE) return "Lifetime access"
        if (licenseStatus == STATUS_PENDING) return "Awaiting administrator approval"
        if (licenseStatus == STATUS_REJECTED) return "Access was rejected"
        val remaining = expiryDate - now
        if (remaining <= 0L) return "Expired"
        val days = TimeUnit.MILLISECONDS.toDays(remaining)
        val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
        return "${days}d ${hours}h remaining"
    }

    /**
     * CRITICAL FIX: Convert Entity to Model for UI layer
     */
    fun toUserModel(): User {
        return User(
            id = id,
            email = email,
            role = role,
            isApproved = isApproved,
            isAdmin = isAdmin,
            licenseStatus = licenseStatus,
            expiryDate = expiryDate,
            createdAt = createdAt,
            deviceId = deviceId
        )
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_EXPIRED = "expired"
    }
}
