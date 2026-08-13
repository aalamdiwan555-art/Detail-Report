package com.ultra.autodetector.data.repository

import android.content.Context
import android.os.Build
import android.util.Patterns
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.EncryptedPrefsManager
import com.ultra.autodetector.data.local.UserEntity
import com.ultra.autodetector.data.model.User
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val users = database.userDao()
    private val prefs = EncryptedPrefsManager(appContext)

    suspend fun signup(email: String, password: String): Result<User> = runCatching {
        val normalizedEmail = validate(email, password)
        require(!AdminConfig.isReservedEmail(normalizedEmail)) {
            "This email is reserved for administrator."
        }
        withContext(Dispatchers.IO) {
            require(users.getByEmail(normalizedEmail) == null) {
                "An account with this email already exists."
            }
            val user = UserEntity(
                id = "user-${UUID.randomUUID()}",
                email = normalizedEmail,
                passwordHash = AdminConfig.hashPass(password),
                isAdmin = false,
                licenseStatus = UserEntity.STATUS_PENDING,
                expiryDate = System.currentTimeMillis() + TRIAL_MILLIS,
                deviceId = deviceId(),
            )
            users.insert(user)
            saveSession(user)
            user
        }
    }

    suspend fun register(email: String, password: String): Result<User> =
        signup(email, password)

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        val normalizedEmail = validate(email, password)
        withContext(Dispatchers.IO) {
            var user = users.getByEmail(normalizedEmail)
            if (user == null && AdminConfig.matches(normalizedEmail, password)) {
                user = UserEntity(
                    id = ADMIN_ID,
                    email = AdminConfig.ADMIN_EMAIL,
                    passwordHash = AdminConfig.hashPass(password),
                    isAdmin = true,
                    licenseStatus = UserEntity.STATUS_APPROVED,
                    expiryDate = Long.MAX_VALUE,
                    deviceId = "administrator",
                )
                users.insert(user)
            }
            val account = requireNotNull(user) { "Incorrect email or password." }
            require(
                account.passwordHash == AdminConfig.hashPass(password) ||
                    AdminConfig.matches(normalizedEmail, password),
            ) {
                "Incorrect email or password."
            }
            saveSession(account)
            account
        }
    }

    suspend fun currentUser(): User? = withContext(Dispatchers.IO) {
        val localPrefs = appContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val localEmail = localPrefs.getString("email", null)
        if (localEmail != null) {
            users.getByEmail(localEmail)?.also(::saveSession)?.let { return@withContext it }
        }
        val id = prefs.getSessionUid() ?: return@withContext null
        users.getById(id)?.also(::saveSession)
    }

    fun isLoggedIn(): Boolean =
        prefs.getSessionUid() != null ||
            appContext.getSharedPreferences("auth", Context.MODE_PRIVATE).contains("email")

    suspend fun logout() = withContext(Dispatchers.IO) {
        prefs.clearAll()
        appContext.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun remainingLabel(user: User): String = user.remainingLabel()
    fun hasActiveLicense(user: User): Boolean = user.hasActiveLicense()

    private fun validate(email: String, password: String): String {
        val normalized = email.trim().lowercase()
        require(Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            "Enter a valid email address."
        }
        require(password.length >= 4) { "Password must be at least 4 characters." }
        return normalized
    }

    private fun saveSession(user: User) {
        prefs.setSessionUid(user.id)
        prefs.saveCurrentUserJson(
            JSONObject()
                .put("id", user.id)
                .put("email", user.email)
                .put("isAdmin", user.isAdmin)
                .put("licenseStatus", user.licenseStatus)
                .put("expiryDate", user.expiryDate)
                .toString(),
        )
        // Also save to simple prefs for MainActivity
        appContext.getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
            .putString("email", user.email)
            .putBoolean("isAdmin", user.isAdmin)
            .putString("passwordHash", user.passwordHash)
            .apply()
    }

    private fun deviceId(): String =
        "${Build.MANUFACTURER}-${Build.MODEL}-${Build.VERSION.SDK_INT}"

    companion object {
        private const val ADMIN_ID = "local-admin"
        private const val TRIAL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
