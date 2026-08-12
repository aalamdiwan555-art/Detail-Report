package com.ultra.autodetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): NoticeEntity?

    @Insert
    suspend fun insert(notice: NoticeEntity)

    @Query("DELETE FROM notices")
    suspend fun deleteAll()
}