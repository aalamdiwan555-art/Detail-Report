package com.ultra.autodetector.data.repository

import android.content.Context
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.UserEntity
import com.ultra.autodetector.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            }
        }

    suspend fun stats(): Stats = withContext(Dispatchers.IO) {
        Stats(
            total = dao.count(),
            active = dao.countActive(),
            pending = dao.countPending(),
            expired = dao.countExpired(),
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
        val base = maxOf(System.currentTimeMillis(), user.expiryDate)
        dao.update(
            user.copy(
                licenseStatus = UserEntity.STATUS_APPROVED,
                expiryDate = base + days * DAY_MILLIS,
            ),
        )
    }

    suspend fun setExpiry(id: String, expiryDate: Long) = withContext(Dispatchers.IO) {
        val user = dao.getById(id) ?: error("User not found.")
        dao.update(user.copy(licenseStatus = UserEntity.STATUS_APPROVED, expiryDate = expiryDate))
    }

    suspend fun reject(id: String) = withContext(Dispatchers.IO) {
        val user = dao.getById(id) ?: error("User not found.")
        dao.update(user.copy(licenseStatus = UserEntity.STATUS_REJECTED, expiryDate = 0L))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.deleteById(id) }
    suspend fun clearPending() = withContext(Dispatchers.IO) { dao.deletePending() }

    data class Stats(val total: Int, val active: Int, val pending: Int, val expired: Int)

    companion object {
        private const val DAY_MILLIS = 86_400_000L
    }
}