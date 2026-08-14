package com.ultra.autodetector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "actions")
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    val actionType: String = TYPE_CLICK,
    val parameters: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_CLICK = "CLICK"
        const val TYPE_SWIPE = "SWIPE"
    }
}