package com.ultra.autodetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices ORDER BY createdAt DESC")
    suspend fun getAll(): List<NoticeEntity>

    @Query("SELECT * FROM notices WHERE isRead = 0 ORDER BY createdAt DESC")
    suspend fun getUnread(): List<NoticeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notice: NoticeEntity)

    @Query("UPDATE notices SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String): Int

    @Query("DELETE FROM notices WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM notices")
    suspend fun clearAll()
}
