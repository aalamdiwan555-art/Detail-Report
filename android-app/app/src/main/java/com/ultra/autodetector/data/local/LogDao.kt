package com.ultra.autodetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 500): List<LogEntity>

    @Insert
    suspend fun insert(log: LogEntity)

    @Query("DELETE FROM logs")
    suspend fun deleteAll()
}