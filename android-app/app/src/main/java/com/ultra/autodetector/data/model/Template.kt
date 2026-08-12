package com.ultra.autodetector.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class Template(
    @PrimaryKey val templateId: String,
    val name: String,
    val description: String = "",
    val filePath: String,
    val confidenceThreshold: Double = 0.85,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
)