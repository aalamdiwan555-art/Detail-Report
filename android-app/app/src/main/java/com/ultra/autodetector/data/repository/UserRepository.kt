package com.ultra.autodetector.data.repository

import android.content.Context
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.UserEntity
import com.ultra.autodetector.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class UserRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context.applicationContext).userDao()

    suspend fun listUsers(query: String = "", filter: String = "all"): List<User> =
        withContext(Dispatchers.IO) {
            val result = if (query.isBlank()) dao.getAll() else dao.search(query.trim())
            when (filter) {
                UserEntity.STATUS_PENDING -> result.filter { it.licenseStatus == UserEntity.STATUS_PENDING }
                UserEntity.STATUS_APPROVED -> result.filter { it.hasActiveLicense() }
                UserEntity.STATUS_EXPIRED -> result.filter {
                    !it.isAdmin && it.expiryDate <= System.currentTimeMillis()
                }
                else -> result
            }.map { it.toUserModel() }
        }

    suspend fun stats(): Stats = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        Stats(
            total = dao.count(),
            active = dao.countActive(now),
            pending = dao.countPending(),
            expired = dao.countExpired(now),
        )
    }

    suspend fun registrationsLastSevenDays(): List<Int> = withContext(Dispatchers.IO) {
        val day = 86_400_000L
        val today = System.currentTimeMillis() / day * day
        (6 downTo 0).map { offset ->
            dao.countCreatedBetween(today - offset * day, today - (offset - 1) * day)
        }
    }

    suspend fun approve(id: String, days: Int) = withContext(Dispatchers.IO) {
        val user = dao.getById(id) ?: error("User not found.")
        val now = System.currentTimeMillis()
        val base = if (user.expiryDate in (now + 1)..Long.MAX_VALUE) user.expiryDate else now
        val expiry = if (days >= 3650) Long.MAX_VALUE else base + TimeUnit.DAYS.toMillis(days.toLong())
        dao.updateStatus(id, UserEntity.STATUS_APPROVED, expiry)
    }

    suspend fun reject(id: String) = withContext(Dispatchers.IO) {
        val user = dao.getById(id) ?: error("User not found.")
        require(!user.isAdmin) { "Cannot reject administrator." }
        dao.updateStatus(id, UserEntity.STATUS_REJECTED, 0L)
    }

    suspend fun clearPending() = withContext(Dispatchers.IO) {
        dao.deletePending()
    }

    data class Stats(
        val total: Int,
        val active: Int,
        val pending: Int,
        val expired: Int,
    )

    private fun UserEntity.toUserModel(): User {
        return User(
            id = id,
            email = email,
            role = role,
            isApproved = isApproved,
            isAdmin = isAdmin,
            licenseStatus = licenseStatus,
            expiryDate = expiryDate,
            createdAt = createdAt,
            deviceId = deviceId
        )
    }
}
