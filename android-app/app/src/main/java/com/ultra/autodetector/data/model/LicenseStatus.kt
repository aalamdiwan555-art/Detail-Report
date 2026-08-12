package com.ultra.autodetector.data.model

enum class LicenseStatus(val wireValue: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    EXPIRED("expired");

    companion object {
        fun fromWireValue(value: String?): LicenseStatus =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: PENDING
    }
}