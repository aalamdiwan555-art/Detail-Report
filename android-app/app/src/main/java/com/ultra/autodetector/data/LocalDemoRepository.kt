package com.ultra.autodetector.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class LocalDemoRepository(context: Context) : AppRepository {
    private val preferences = context.getSharedPreferences("ultra_local_demo", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        AppState(
            account = if (preferences.getBoolean("signed_in", false)) demoUser else null,
            templates = sampleTemplates,
        ),
    )
    override val state: StateFlow<AppState> = _state.asStateFlow()

    override suspend fun login(email: String, password: String): Result<Account> {
        if (email.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Enter a valid email and a password with at least 6 characters."))
        }
        val account = if (email.equals("admin@local.demo", ignoreCase = true)) {
            demoAdmin
        } else {
            demoUser.copy(email = email)
        }
        preferences.edit().putBoolean("signed_in", true).apply()
        _state.value = _state.value.copy(account = account, message = "Welcome back")
        return Result.success(account)
    }

    override suspend fun register(email: String, password: String): Result<Account> {
        if (email.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Use a valid email and a password with at least 6 characters."))
        }
        val account = demoUser.copy(email = email)
        preferences.edit().putBoolean("signed_in", true).apply()
        _state.value = _state.value.copy(account = account, message = "Account created. Waiting for approval.")
        return Result.success(account)
    }

    override suspend fun logout() {
        preferences.edit().putBoolean("signed_in", false).apply()
        _state.value = _state.value.copy(account = null, isDetectorRunning = false, isDetectorPaused = false)
    }

    override suspend fun refresh() {
        _state.value = _state.value.copy(message = null)
    }

    override suspend fun setPermissionState(state: PermissionState) {
        _state.value = _state.value.copy(permissionState = state)
    }

    override suspend fun setDetectorState(running: Boolean, paused: Boolean) {
        _state.value = _state.value.copy(isDetectorRunning = running, isDetectorPaused = paused)
    }

    override suspend fun grantLicense(uid: String, days: Int?) {
        val expires = days?.let { System.currentTimeMillis() + it * 86_400_000L } ?: Long.MAX_VALUE
        _state.value = _state.value.copy(
            message = "License updated",
            account = _state.value.account?.takeIf { it.uid == uid }?.copy(
                status = AccountStatus.ACTIVE,
                expiresAtMillis = expires,
            ) ?: _state.value.account,
        )
    }

    override suspend fun rejectUser(uid: String) {
        _state.value = _state.value.copy(message = "User rejected")
    }

    override suspend fun uploadTemplate(name: String, description: String, imageUri: Uri?): Result<DetectionTemplate> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Template name is required."))
        val template = DetectionTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim().ifBlank { "Local template" },
        )
        _state.value = _state.value.copy(templates = _state.value.templates + template, message = "Template added")
        return Result.success(template)
    }

    override suspend fun deleteTemplate(templateId: String) {
        _state.value = _state.value.copy(
            templates = _state.value.templates.filterNot { it.id == templateId },
            message = "Template removed",
        )
    }

    companion object {
        private val demoAdmin = Account(
            uid = "local-admin",
            email = "admin@local.demo",
            isAdmin = true,
            status = AccountStatus.ACTIVE,
            expiresAtMillis = Long.MAX_VALUE,
        )
        private val demoUser = Account(
            uid = "local-user",
            email = "operator@local.demo",
            status = AccountStatus.ACTIVE,
            expiresAtMillis = System.currentTimeMillis() + 3 * 86_400_000L,
        )
        private val sampleTemplates = listOf(
            DetectionTemplate("template-primary", "Primary target", "Main target image used by the detector."),
            DetectionTemplate("template-secondary", "Secondary target", "Optional secondary target for testing.", 0.9f),
        )
    }
}