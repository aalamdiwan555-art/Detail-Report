package com.ultra.autodetector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String = "INFO",
    val message: String,
    val templateName: String? = null,
    val confidence: Float? = null,
    val x: Int? = null,
    val y: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)