package com.ultra.autodetector.data

import android.content.Context
import android.net.Uri
import android.util.Patterns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class LocalDemoRepository(context: Context) : AppRepository {
    private val preferences = context.getSharedPreferences("ultra_local_demo", Context.MODE_PRIVATE)
    private val savedEmail = preferences.getString("email", "").orEmpty()
    private val _state = MutableStateFlow(
        AppState(
            account = if (preferences.getBoolean("signed_in", false)) {
                if (savedEmail.equals(demoAdmin.email, ignoreCase = true)) demoAdmin
                else demoUser.copy(email = savedEmail.ifBlank { demoUser.email })
            } else null,
            adminUsers = if (savedEmail.equals(demoAdmin.email, ignoreCase = true)) sampleUsers else emptyList(),
            templates = sampleTemplates,
        ),
    )
    override val state: StateFlow<AppState> = _state.asStateFlow()

    override suspend fun login(email: String, password: String): Result<Account> {
        if (!isValidEmail(email) || password.length < 8) {
            return Result.failure(IllegalArgumentException("Enter a valid email and a password with at least 8 characters."))
        }
        val account = if (email.equals("admin@local.demo", ignoreCase = true)) {
            if (password != "UltraAdmin!26") {
                return Result.failure(IllegalArgumentException("Incorrect email or password."))
            }
            demoAdmin
        } else {
            if (password != "ActiveUser!26") {
                return Result.failure(IllegalArgumentException("Incorrect email or password."))
            }
            demoUser.copy(email = email)
        }
        preferences.edit().putBoolean("signed_in", true).putString("email", account.email).apply()
        _state.value = _state.value.copy(
            account = account,
            adminUsers = if (account.isAdmin) sampleUsers else emptyList(),
            message = "Welcome back",
        )
        return Result.success(account)
    }

    override suspend fun register(email: String, password: String): Result<Account> {
        if (!isValidEmail(email) || password.length < 8) {
            return Result.failure(IllegalArgumentException("Use a valid email and a password with at least 8 characters."))
        }
        val account = Account(
            uid = "local-${UUID.randomUUID()}",
            email = email.trim(),
            status = AccountStatus.PENDING,
        )
        preferences.edit().putBoolean("signed_in", true).putString("email", account.email).apply()
        _state.value = _state.value.copy(account = account, adminUsers = emptyList(), message = "Account created. Waiting for approval.")
        return Result.success(account)
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Result<Unit> = Result.failure(
        IllegalStateException("The legacy demo repository does not support password changes."),
    )

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        if (!isValidEmail(email)) return Result.failure(IllegalArgumentException("Enter a valid email address first."))
        _state.value = _state.value.copy(message = "Demo mode: password reset email simulated.")
        return Result.success(Unit)
    }

    private fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    override suspend fun logout() {
        preferences.edit().putBoolean("signed_in", false).apply()
        _state.value = _state.value.copy(
            account = null,
            adminUsers = emptyList(),
            isDetectorRunning = false,
            isDetectorPaused = false,
        )
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

    override suspend fun refreshAdminData() {
        if (_state.value.account?.isAdmin == true) {
            _state.value = _state.value.copy(adminUsers = _state.value.adminUsers.ifEmpty { sampleUsers })
        }
    }

    override suspend fun grantLicense(uid: String, days: Int?) {
        val existing = _state.value.adminUsers.firstOrNull { it.uid == uid }?.expiresAtMillis
        val base = maxOf(System.currentTimeMillis(), existing ?: 0L)
        val expires = days?.let {
            require(it > 0) { "License duration must be positive." }
            val increment = it.toLong() * 86_400_000L
            if (Long.MAX_VALUE - base < increment) Long.MAX_VALUE else base + increment
        } ?: Long.MAX_VALUE
        _state.value = _state.value.copy(
            message = "License updated",
            adminUsers = _state.value.adminUsers.map {
                if (it.uid == uid) it.copy(status = AccountStatus.ACTIVE, expiresAtMillis = expires) else it
            },
            account = _state.value.account?.takeIf { it.uid == uid }?.copy(
                status = AccountStatus.ACTIVE,
                expiresAtMillis = expires,
            ) ?: _state.value.account,
        )
    }

    override suspend fun rejectUser(uid: String) {
        _state.value = _state.value.copy(
            message = "User rejected",
            adminUsers = _state.value.adminUsers.map {
                if (it.uid == uid) it.copy(status = AccountStatus.REJECTED) else it
            },
        )
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
        private val sampleUsers = listOf(
            Account(
                uid = "local-pending-user",
                email = "pending@local.demo",
                status = AccountStatus.PENDING,
            ),
            Account(
                uid = "local-active-user",
                email = "active@local.demo",
                status = AccountStatus.ACTIVE,
                expiresAtMillis = System.currentTimeMillis() + 2 * 86_400_000L,
            ),
        )
    }
}