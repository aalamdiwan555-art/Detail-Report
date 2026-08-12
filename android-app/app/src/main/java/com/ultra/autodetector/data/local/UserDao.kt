package com.ultra.autodetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE lower(email) = lower(:email) LIMIT 1")
    suspend fun getByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    suspend fun getAll(): List<UserEntity>

    @Query("SELECT * FROM users WHERE lower(email) LIKE '%' || lower(:query) || '%' ORDER BY createdAt DESC")
    suspend fun search(query: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE licenseStatus = 'pending' ORDER BY createdAt ASC")
    suspend fun getPending(): List<UserEntity>

    @Query("SELECT * FROM users WHERE licenseStatus = 'approved' AND expiryDate > :now ORDER BY createdAt DESC")
    suspend fun getActive(now: Long = System.currentTimeMillis()): List<UserEntity>

    @Query("SELECT * FROM users WHERE expiryDate <= :now AND isAdmin = 0 ORDER BY expiryDate DESC")
    suspend fun getExpired(now: Long = System.currentTimeMillis()): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM users WHERE licenseStatus = 'approved' AND expiryDate > :now")
    suspend fun countActive(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM users WHERE licenseStatus = 'pending'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM users WHERE expiryDate <= :now AND isAdmin = 0")
    suspend fun countExpired(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM users WHERE createdAt >= :start AND createdAt < :end")
    suspend fun countCreatedBetween(start: Long, end: Long): Int

    @androidx.room.Insert
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM users WHERE licenseStatus = 'pending'")
    suspend fun deletePending()
}