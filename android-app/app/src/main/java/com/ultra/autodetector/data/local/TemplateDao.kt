package com.ultra.autodetector.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE enabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabled(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity)

    @Update
    suspend fun update(template: TemplateEntity)

    @Delete
    suspend fun delete(template: TemplateEntity)

    @Query("DELETE FROM templates")
    suspend fun deleteAll()
}