package com.ultra.autodetector.data.model

data class Template(
    val templateId: String = "",
    val name: String = "",
    val description: String = "",
    val confidenceThreshold: Double = 0.85,
    val isActive: Boolean = true,
    val downloadUrl: String = "",
    val createdAt: com.google.firebase.Timestamp? = null,
)