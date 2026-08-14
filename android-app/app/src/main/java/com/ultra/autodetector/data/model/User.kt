package com.ultra.autodetector.data.model

data class User(
    val id: String,
    val email: String,
    val role: String = ROLE_USER,
    val isApproved: Boolean = true,
    val isAdmin: Boolean = false,
    val licenseStatus: String = "pending",
    val expiryDate: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
) {
    fun hasActiveLicense(now: Long = System.currentTimeMillis()): Boolean =
        isAdmin || (licenseStatus == "approved" && expiryDate > now)

    fun remainingLabel(now: Long = System.currentTimeMillis()): String {
        if (isAdmin || expiryDate == Long.MAX_VALUE) return "Lifetime access"
        if (licenseStatus == "pending") return "Awaiting administrator approval"
        if (licenseStatus == "rejected") return "Access was rejected"
        val remaining = expiryDate - now
        if (remaining <= 0L) return "Expired"
        val days = (remaining / 86_400_000).toInt()
        val hours = ((remaining % 86_400_000) / 3_600_000).toInt()
        return "${days}d ${hours}h remaining"
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ADMIN = "admin"
    }
}
