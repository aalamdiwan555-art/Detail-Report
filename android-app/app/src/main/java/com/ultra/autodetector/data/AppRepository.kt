package com.ultra.autodetector.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    val state: Flow<AppState>
    suspend fun login(email: String, password: String): Result<Account>
    suspend fun register(email: String, password: String): Result<Account>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun logout()
    suspend fun refresh()
    suspend fun setPermissionState(state: PermissionState)
    suspend fun setDetectorState(running: Boolean, paused: Boolean = false)
    suspend fun refreshAdminData()
    suspend fun grantLicense(uid: String, days: Int?)
    suspend fun rejectUser(uid: String)
    suspend fun uploadTemplate(name: String, description: String, imageUri: Uri?): Result<DetectionTemplate>
    suspend fun deleteTemplate(templateId: String)
}