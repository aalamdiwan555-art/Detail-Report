package com.ultra.autodetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActionDao {
    @Query("SELECT * FROM actions WHERE templateId = :templateId ORDER BY id DESC LIMIT 1")
    suspend fun getForTemplate(templateId: String): ActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: ActionEntity)

    @Query("DELETE FROM actions WHERE templateId = :templateId")
    suspend fun deleteForTemplate(templateId: String)

    @Query("DELETE FROM actions")
    suspend fun deleteAll()
}