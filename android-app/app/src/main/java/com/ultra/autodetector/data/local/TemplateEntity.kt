package com.ultra.autodetector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imagePath: String,
    val threshold: Float = 0.80f,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)