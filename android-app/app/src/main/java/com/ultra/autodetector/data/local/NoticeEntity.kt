package com.ultra.autodetector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notices")
data class NoticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
)