package com.ultra.autodetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ultra.autodetector.data.model.Template

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates WHERE isActive = 1 ORDER BY createdAt ASC")
    suspend fun listActive(): List<Template>

    @Query("SELECT * FROM templates ORDER BY createdAt ASC")
    suspend fun listAll(): List<Template>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: Template)

    @Query("DELETE FROM templates WHERE templateId = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM templates WHERE templateId = :id LIMIT 1")
    suspend fun findById(id: String): Template?
}