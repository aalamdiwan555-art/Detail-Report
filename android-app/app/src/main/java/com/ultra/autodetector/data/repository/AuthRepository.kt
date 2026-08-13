package com.ultra.autodetector.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.util.Patterns
import androidx.core.content.edit
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.EncryptedPrefsManager
import com.ultra.autodetector.data.local.UserEntity
import com.ultra.autodetector.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class AuthRepository(context: Context) {
    companion object {
        private const val TAG = "AuthRepository"
        private const val ADMIN_ID = "local-admin"
        private const val PREFS_AUTH = "auth_v2"
        private const val PREFS_FALLBACK = "ultra_fallback_prefs_v2"

        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LAST_LOGIN = "last_login"
        private const val KEY_PASSWORD_HASH = "password_hash"
    }

    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.getInstance(appContext) }
    private val userDao by lazy { database.userDao() }

    private val encryptedPrefs: EncryptedPrefsManager? by lazy {
        runCatching { EncryptedPrefsManager(appContext) }.getOrNull()
    }

    private val fallbackPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
    }

    private val authPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
    }

    // ==================== SESSION MANAGEMENT ====================

    private fun saveSession(user: UserEntity) {
        try {
            // 1. Encrypted prefs
            encryptedPrefs?.let { prefs ->
                try {
                    prefs.setSessionUid(user.id)
                    prefs.saveCurrentUserJson(
                        JSONObject().apply {
                            put("id", user.id)
                            put("email", user.email)
                            put("isAdmin", user.isAdmin)
                            put("licenseStatus", user.licenseStatus)
                            put("expiryDate", user.expiryDate)
                            put("deviceId", user.deviceId)
                            put("passwordHash", user.passwordHash)
                        }.toString()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Encrypted prefs save failed", e)
                }
            }

            // 2. Fallback prefs
            fallbackPrefs.edit(commit = true) {
                putString(KEY_SESSION_ID, user.id)
                putString(KEY_EMAIL, user.email)
                putBoolean(KEY_IS_ADMIN, user.isAdmin)
                putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            }

            // 3. Auth prefs (fast sync)
            authPrefs.edit(commit = true) {
                putString(KEY_USER_ID, user.id)
                putString(KEY_EMAIL, user.email)
                putBoolean(KEY_IS_ADMIN, user.isAdmin)
                putString(KEY_PASSWORD_HASH, user.passwordHash)
                putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            }

            Log.i(TAG, "Session saved for: ${user.email}")
        } catch (e: Exception) {
            Log.e(TAG, "Session save failed", e)
            throw e
        }
    }

    private fun clearSession(): Boolean {
        var cleared = true
        try {
            try { encryptedPrefs?.clearAll() } catch (e: Exception) { cleared = false }
            try { fallbackPrefs.edit(commit = true) { clear() } } catch (e: Exception) { cleared = false }
            try { authPrefs.edit(commit = true) { clear() } } catch (e: Exception) { cleared = false }

            if (cleared) Log.i(TAG, "Session cleared")
            else Log.e(TAG, "Session clear partial failure")
            return cleared
        } catch (e: Exception) {
            Log.e(TAG, "Session clear failed", e)
            return false
        }
    }

    private fun getSessionId(): String? {
        try { encryptedPrefs?.getSessionUid()?.let { return it } } catch (e: Exception) {}
        try { fallbackPrefs.getString(KEY_SESSION_ID, null)?.let { return it } } catch (e: Exception) {}
        try { authPrefs.getString(KEY_USER_ID, null)?.let { return it } } catch (e: Exception) {}
        return null
    }

    // ==================== AUTH METHODS ====================

    suspend fun signup(email: String, password: String): Result<User> = runCatching {
        val normalizedEmail = validateEmail(email)
        val hashedPassword = AdminConfig.hashPass(password)

        require(!AdminConfig.isReservedEmail(normalizedEmail)) {
            "This email is reserved for administrator."
        }

        withContext(Dispatchers.IO) {
            require(userDao.getByEmail(normalizedEmail) == null) {
                "An account with this email already exists."
            }

            val userEntity = UserEntity(
                id = "user-${UUID.randomUUID()}",
                email = normalizedEmail,
                passwordHash = hashedPassword,
                isAdmin = false,
                licenseStatus = UserEntity.STATUS_PENDING,
                expiryDate = 0L,
                deviceId = generateDeviceId()
            )

            userDao.insert(userEntity)

            val inserted = userDao.getByEmail(normalizedEmail)
                ?: throw IllegalStateException("User insertion failed")

            require(inserted.passwordHash == hashedPassword) {
                "Password hash storage verification failed"
            }

            val userModel = inserted.toUserModel()
            saveSession(inserted)

            Log.i(TAG, "Signup successful: $normalizedEmail")
            userModel
        }
    }

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        val normalizedEmail = validateEmail(email)
        val inputHash = AdminConfig.hashPass(password)

        withContext(Dispatchers.IO) {
            var userEntity: UserEntity? = userDao.getByEmail(normalizedEmail)

            // Admin login
            if (userEntity == null && AdminConfig.matches(normalizedEmail, password)) {
                userEntity = UserEntity(
                    id = ADMIN_ID,
                    email = AdminConfig.ADMIN_EMAIL,
                    passwordHash = AdminConfig.hashPass(password),
                    isAdmin = true,
                    licenseStatus = UserEntity.STATUS_APPROVED,
                    expiryDate = Long.MAX_VALUE,
                    deviceId = "administrator"
                )
                userDao.insert(userEntity)
                Log.i(TAG, "Admin account created")
            }

            val account = requireNotNull(userEntity) { "Incorrect email or password." }

            val isPasswordValid = account.passwordHash == inputHash || 
                (account.isAdmin && AdminConfig.matches(normalizedEmail, password))

            require(isPasswordValid) { "Incorrect email or password." }

            userDao.update(account)

            val userModel = account.toUserModel()
            saveSession(account)

            Log.i(TAG, "Login successful: $normalizedEmail")
            userModel
        }
    }

    suspend fun currentUser(): User? = withContext(Dispatchers.IO) {
        try {
            val cachedEmail = authPrefs.getString(KEY_EMAIL, null)
            if (cachedEmail != null) {
                userDao.getByEmail(cachedEmail)?.let { return@withContext it.toUserModel() }
            }

            val sessionId = getSessionId() ?: return@withContext null
            userDao.getById(sessionId)?.let { entity ->
                authPrefs.edit(commit = true) {
                    putString(KEY_EMAIL, entity.email)
                    putBoolean(KEY_IS_ADMIN, entity.isAdmin)
                }
                return@withContext entity.toUserModel()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving current user", e)
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return try {
            authPrefs.getString(KEY_EMAIL, null) != null &&
            authPrefs.getString(KEY_USER_ID, null) != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        clearSession()
    }

    // ==================== HELPERS ====================

    private fun validateEmail(email: String): String {
        val normalized = email.trim().lowercase()
        require(Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            "Enter a valid email address."
        }
        return normalized
    }

    private fun generateDeviceId(): String {
        return "${Build.MANUFACTURER}-${Build.MODEL}-${Build.VERSION.SDK_INT}-${UUID.randomUUID().toString().take(8)}"
    }

    fun remainingLabel(user: User): String = user.remainingLabel()
    fun hasActiveLicense(user: User): Boolean = user.hasActiveLicense()

    private fun UserEntity.toUserModel(): User {
        return User(
            id = id,
            email = email,
            isAdmin = isAdmin,
            licenseStatus = licenseStatus,
            expiryDate = expiryDate,
            createdAt = createdAt,
            deviceId = deviceId
        )
    }
}
