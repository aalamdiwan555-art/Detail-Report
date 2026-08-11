package com.ultra.autodetector.data

import kotlinx.serialization.Serializable

@Serializable
enum class AccountStatus { PENDING, ACTIVE, REJECTED, EXPIRED }

@Serializable
data class Account(
    val uid: String,
    val email: String,
    val isAdmin: Boolean = false,
    val status: AccountStatus = AccountStatus.PENDING,
    val expiresAtMillis: Long? = null,
) {
    fun hasActiveLicense(now: Long = System.currentTimeMillis()): Boolean =
        isAdmin || (status == AccountStatus.ACTIVE && (expiresAtMillis ?: 0L) > now)

    fun remainingLabel(now: Long = System.currentTimeMillis()): String {
        if (isAdmin || expiresAtMillis == Long.MAX_VALUE) return "Lifetime access"
        val expiresAt = expiresAtMillis ?: return "Awaiting approval"
        val remaining = expiresAt - now
        if (remaining <= 0L) return "Expired"
        val days = remaining / 86_400_000L
        val hours = (remaining % 86_400_000L) / 3_600_000L
        return "${days}d ${hours}h remaining"
    }
}

@Serializable
data class DetectionTemplate(
    val id: String,
    val name: String,
    val description: String,
    val confidenceThreshold: Float = 0.85f,
    val isActive: Boolean = true,
    val downloadUrl: String = "",
)

data class PermissionState(
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val screenCapture: Boolean = false,
) {
    val allGranted: Boolean get() = accessibility && overlay && screenCapture
}

data class AppState(
    val account: Account? = null,
    val templates: List<DetectionTemplate> = emptyList(),
    val permissionState: PermissionState = PermissionState(),
    val isDetectorRunning: Boolean = false,
    val isDetectorPaused: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
)