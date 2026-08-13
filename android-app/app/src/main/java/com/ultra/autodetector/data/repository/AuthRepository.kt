package com.ultra.autodetector.data.repository

import android.content.Context
import android.content.SharedPreferences
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
    private val database by lazy { AppDatabase.getInstance(appContext) }
    private val users by lazy { database.userDao() }
    
    // Constructed only when a session is read or written, never during the
    // first activity's view setup.
    private val prefs: EncryptedPrefsManager? by lazy {
        runCatching { EncryptedPrefsManager(appContext) }.getOrNull()
    }
    private val fallbackPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("ultra_fallback_prefs", Context.MODE_PRIVATE)
    }
    private val authPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
    }

    private fun getSessionUidSafe(): String? {
        return try {
            prefs?.getSessionUid() ?: fallbackPrefs.getString("session_uid", null)
        } catch (_: Exception) {
            fallbackPrefs.getString("session_uid", null)
        }
    }

    private fun setSessionUidSafe(id: String) {
        try {
            prefs?.setSessionUid(id)
        } catch (_: Exception) {}
        try {
            fallbackPrefs.edit().putString("session_uid", id).apply()
        } catch (_: Exception) {}
    }

    private fun clearSessionSafe() {
        try { prefs?.clearAll() } catch (_: Exception) {}
        try { fallbackPrefs.edit().clear().apply() } catch (_: Exception) {}
        try { authPrefs.edit().clear().apply() } catch (_: Exception) {}
    }

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
                expiryDate = 0L,
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
        try {
            val localEmail = authPrefs.getString("email", null)
            if (localEmail != null) {
                users.getByEmail(localEmail)?.also(::saveSession)?.let { return@withContext it }
            }
            val id = getSessionUidSafe() ?: return@withContext null
            users.getById(id)?.also(::saveSession)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return try {
            // authPrefs is intentionally plain and tiny; it is the fast
            // synchronous source used by the launch activity. Encrypted
            // preferences and Room are read from suspend/IO paths.
            authPrefs.getString("email", null) != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        clearSessionSafe()
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
        try {
            setSessionUidSafe(user.id)
            try {
                prefs?.saveCurrentUserJson(
                    JSONObject()
                        .put("id", user.id)
                        .put("email", user.email)
                        .put("isAdmin", user.isAdmin)
                        .put("licenseStatus", user.licenseStatus)
                        .put("expiryDate", user.expiryDate)
                        .toString(),
                )
            } catch (_: Exception) {}
            
            authPrefs.edit()
                .putString("email", user.email)
                .putBoolean("isAdmin", user.isAdmin)
                .putString("passwordHash", user.passwordHash)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deviceId(): String =
        "${Build.MANUFACTURER}-${Build.MODEL}-${Build.VERSION.SDK_INT}"

    companion object {
        private const val ADMIN_ID = "local-admin"
    }
}
