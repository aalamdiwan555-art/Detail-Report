package com.ultra.autodetector.data.model

enum class LicenseStatus(val wireValue: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    EXPIRED("expired"),
}