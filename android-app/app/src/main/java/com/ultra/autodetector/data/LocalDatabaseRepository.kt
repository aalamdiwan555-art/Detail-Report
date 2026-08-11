package com.ultra.autodetector.data

import android.content.Context
import android.net.Uri
import com.ultra.autodetector.data.local.LocalDatabase
import com.ultra.autodetector.data.local.LocalUserRecord
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * App repository backed by SQLite on the Android device.
 *
 * This is the default repository when the app is used without a cloud
 * backend. It keeps the existing demo behavior while making users, licenses,
 * templates, and session state survive app restarts.
 */
class LocalDatabaseRepository(context: Context) : AppRepository {
    private val appContext = context.applicationContext
    private val database = LocalDatabase(appContext)
    private val templateDirectory = File(appContext.filesDir, "templates").apply { mkdirs() }
    private val _state = MutableStateFlow(loadInitialState())

    override val state: StateFlow<AppState> = _state.asStateFlow()

    override suspend fun login(email: String, password: String): Result<Account> = runCatching {
        require(email.isNotBlank() && password.length >= 6) {
            "Enter a valid email and a password with at least 6 characters."
        }

        val record = withContext(Dispatchers.IO) {
            val normalizedEmail = email.trim()
            val existing = database.findUserByEmail(normalizedEmail)
            when {
                normalizedEmail.equals(ADMIN_EMAIL, ignoreCase = true) -> {
                    val admin = database.findUserByEmail(ADMIN_EMAIL) ?: adminRecord().also {
                        database.saveUser(it)
                    }
                    require(admin.passwordHash == hashPassword(password)) {
                        "Incorrect email or password."
                    }
                    admin
                }
                existing != null -> {
                    require(
                        existing.passwordHash.isBlank() ||
                            existing.passwordHash == hashPassword(password),
                    ) { "Incorrect email or password." }
                    existing
                }
                else -> {
                    val newRecord = LocalUserRecord(
                        uid = "local-${UUID.randomUUID()}",
                        email = normalizedEmail,
                        passwordHash = hashPassword(password),
                        isAdmin = false,
                        status = AccountStatus.ACTIVE,
                        expiresAtMillis = System.currentTimeMillis() + THREE_DAYS_MILLIS,
                    )
                    database.saveUser(newRecord)
                    newRecord
                }
            }
            database.setSessionEmail(record.email)
            record
        }
        val account = record.toAccount()
        _state.value = loadState(account).copy(message = "Welcome back")
        account
    }

    override suspend fun register(email: String, password: String): Result<Account> = runCatching {
        require(email.isNotBlank() && password.length >= 6) {
            "Use a valid email and a password with at least 6 characters."
        }

        val account = withContext(Dispatchers.IO) {
            val normalizedEmail = email.trim()
            require(database.findUserByEmail(normalizedEmail) == null) {
                "An account with this email already exists."
            }
            val record = LocalUserRecord(
                uid = "local-${UUID.randomUUID()}",
                email = normalizedEmail,
                passwordHash = hashPassword(password),
                isAdmin = false,
                status = AccountStatus.PENDING,
                expiresAtMillis = null,
            )
            database.saveUser(record)
            database.setSessionEmail(record.email)
            record.toAccount()
        }
        _state.value = loadState(account).copy(message = "Account created. Waiting for approval.")
        account
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        require(email.isNotBlank()) { "Enter your email address first." }
        _state.value = _state.value.copy(message = "Local mode: password reset is not available without an email service.")
    }

    override suspend fun logout() {
        withContext(Dispatchers.IO) { database.setSessionEmail(null) }
        _state.value = _state.value.copy(
            account = null,
            adminUsers = emptyList(),
            isDetectorRunning = false,
            isDetectorPaused = false,
            message = null,
        )
    }

    override suspend fun refresh() {
        val account = withContext(Dispatchers.IO) {
            database.getSessionEmail()?.let { database.findUserByEmail(it)?.toAccount() }
        }
        _state.value = loadState(account).copy(message = null)
    }

    override suspend fun setPermissionState(state: PermissionState) {
        withContext(Dispatchers.IO) { database.savePermissionState(state) }
        _state.value = _state.value.copy(permissionState = state)
    }

    override suspend fun setDetectorState(running: Boolean, paused: Boolean) {
        _state.value = _state.value.copy(isDetectorRunning = running, isDetectorPaused = paused)
    }

    override suspend fun refreshAdminData() {
        val current = _state.value.account ?: return
        require(current.isAdmin) { "Administrator access required." }
        val users = withContext(Dispatchers.IO) { database.listUsers().map { it.toAccount() } }
        _state.value = _state.value.copy(adminUsers = users, templates = database.listTemplates())
    }

    override suspend fun grantLicense(uid: String, days: Int?) {
        val admin = requireAdmin()
        val user = withContext(Dispatchers.IO) { database.findUserByUid(uid) }
            ?: error("User not found.")
        val base = maxOf(System.currentTimeMillis(), user.expiresAtMillis ?: 0L)
        val expires = days?.let {
            require(it > 0) { "License duration must be positive." }
            val increment = it.toLong() * DAY_MILLIS
            if (Long.MAX_VALUE - base < increment) Long.MAX_VALUE else base + increment
        } ?: Long.MAX_VALUE
        withContext(Dispatchers.IO) {
            database.updateUserLicense(uid, AccountStatus.ACTIVE, expires)
            database.addAdminLog(admin.uid, "grant_license", uid, "days=$days")
        }
        refreshAdminData()
        _state.value = _state.value.copy(message = "License updated")
    }

    override suspend fun rejectUser(uid: String) {
        val admin = requireAdmin()
        withContext(Dispatchers.IO) {
            database.updateUserLicense(uid, AccountStatus.REJECTED, null)
            database.addAdminLog(admin.uid, "reject_user", uid)
        }
        refreshAdminData()
        _state.value = _state.value.copy(message = "User rejected")
    }

    override suspend fun uploadTemplate(
        name: String,
        description: String,
        imageUri: Uri?,
    ): Result<DetectionTemplate> = runCatching {
        requireAdmin()
        require(name.isNotBlank()) { "Template name is required." }

        val id = UUID.randomUUID().toString()
        val localPath = imageUri?.let { copyTemplateImage(id, it) }.orEmpty()
        val template = DetectionTemplate(
            id = id,
            name = name.trim(),
            description = description.trim().ifBlank { "Local template" },
            downloadUrl = localPath,
        )
        withContext(Dispatchers.IO) { database.saveTemplate(template) }
        refreshAdminData()
        _state.value = _state.value.copy(message = "Template added")
        template
    }

    override suspend fun deleteTemplate(templateId: String) {
        requireAdmin()
        val path = withContext(Dispatchers.IO) { database.deleteTemplate(templateId) }
        path?.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        refreshAdminData()
        _state.value = _state.value.copy(message = "Template removed")
    }

    private fun requireAdmin(): Account =
        _state.value.account?.takeIf { it.isAdmin } ?: error("Administrator access required.")

    private fun loadInitialState(): AppState {
        val account = database.getSessionEmail()
            ?.let { database.findUserByEmail(it)?.toAccount() }
        return loadState(account)
    }

    private fun loadState(account: Account?): AppState =
        AppState(
            account = account,
            adminUsers = if (account?.isAdmin == true) {
                database.listUsers().map { it.toAccount() }
            } else {
                emptyList()
            },
            templates = database.listTemplates(),
            permissionState = database.getPermissionState(),
        )

    private suspend fun copyTemplateImage(id: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val destination = File(templateDirectory, "$id.png")
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read the selected template image." }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        destination.absolutePath
    }

    private fun LocalUserRecord.toAccount() = Account(
        uid = uid,
        email = email,
        isAdmin = isAdmin,
        status = status,
        expiresAtMillis = expiresAtMillis,
    )

    private fun adminRecord() = LocalUserRecord(
        uid = "local-admin",
        email = ADMIN_EMAIL,
        passwordHash = LocalDatabase.ADMIN_PASSWORD_HASH,
        isAdmin = true,
        status = AccountStatus.ACTIVE,
        expiresAtMillis = Long.MAX_VALUE,
    )

    private fun hashPassword(password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val ADMIN_EMAIL = "admin@local.demo"
        private const val DAY_MILLIS = 86_400_000L
        private const val THREE_DAYS_MILLIS = 3 * DAY_MILLIS
    }
}