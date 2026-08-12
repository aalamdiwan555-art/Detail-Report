package com.ultra.autodetector.data.repository

import android.content.Context
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.model.LicenseStatus
import com.ultra.autodetector.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).userDao()

    suspend fun listUsers(): List<User> = withContext(Dispatchers.IO) { dao.listAll() }

    suspend fun grant(uid: String, days: Int?) = withContext(Dispatchers.IO) {
        val user = dao.findByUid(uid) ?: error("User not found.")
        val base = maxOf(System.currentTimeMillis(), user.expirationTimestamp ?: 0L)
        val expiration = days?.let { base + it.toLong() * 86_400_000L } ?: Long.MAX_VALUE
        dao.update(user.copy(status = LicenseStatus.APPROVED.wireValue, expirationTimestamp = expiration))
    }

    suspend fun reject(uid: String) = withContext(Dispatchers.IO) {
        val user = dao.findByUid(uid) ?: error("User not found.")
        dao.update(user.copy(status = LicenseStatus.REJECTED.wireValue, expirationTimestamp = null))
    }
}