package com.ultra.autodetector.data.repository

import android.content.Context
import android.os.Build
import android.util.Patterns
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.EncryptedPrefsManager
import com.ultra.autodetector.data.model.LicenseStatus
import com.ultra.autodetector.data.model.User
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val prefs = EncryptedPrefsManager(context)

    suspend fun currentUser(): User? = withContext(Dispatchers.IO) {
        val uid = prefs.getSessionUid() ?: return@withContext null
        database.userDao().findByUid(uid)
    }

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        validate(email, password)
        withContext(Dispatchers.IO) {
            val user = database.userDao().findByEmail(email.trim())
                ?: error("Incorrect email or password.")
            require(verifyPassword(password, user.passwordHash)) { "Incorrect email or password." }
            val loggedIn = user.copy(lastLoginAt = System.currentTimeMillis())
            database.userDao().update(loggedIn)
            prefs.setSessionUid(loggedIn.uid)
            loggedIn
        }
    }

    suspend fun register(email: String, password: String): Result<User> = runCatching {
        validate(email, password)
        withContext(Dispatchers.IO) {
            require(database.userDao().findByEmail(email.trim()) == null) {
                "An account with this email already exists."
            }
            val user = User(
                uid = "user-${UUID.randomUUID()}",
                email = email.trim(),
                passwordHash = hashPassword(password),
                deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
            database.userDao().insert(user)
            prefs.setSessionUid(user.uid)
            user
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) { prefs.setSessionUid(null) }

    suspend fun changePassword(current: String, next: String): Result<Unit> = runCatching {
        require(next.length >= 8) { "New password must be at least 8 characters." }
        val user = currentUser() ?: error("Sign in before changing your password.")
        require(verifyPassword(current, user.passwordHash)) { "Current password is incorrect." }
        withContext(Dispatchers.IO) {
            database.userDao().update(user.copy(passwordHash = hashPassword(next)))
        }
    }

    fun isAccessibilityGranted() = prefs.isAccessibilityGranted()
    fun setAccessibilityGranted(value: Boolean) = prefs.setAccessibilityGranted(value)
    fun isOverlayGranted() = prefs.isOverlayGranted()
    fun setOverlayGranted(value: Boolean) = prefs.setOverlayGranted(value)

    private fun validate(email: String, password: String) {
        require(Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
        require(password.length >= 8) { "Password must be at least 8 characters." }
    }

    private fun hashPassword(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return "pbkdf2$${Base64.getEncoder().encodeToString(salt)}$${Base64.getEncoder().encodeToString(derived)}"
    }

    private fun verifyPassword(password: String, stored: String): Boolean {
        if (!stored.startsWith("pbkdf2$")) {
            return MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8)),
                stored.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            )
        }
        val parts = stored.split('$')
        if (parts.size != 3) return false
        val salt = Base64.getDecoder().decode(parts[1])
        val expected = Base64.getDecoder().decode(parts[2])
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, expected.size * 8)
        val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return MessageDigest.isEqual(actual, expected)
    }

    companion object {
        // The original prompt supplied exposed credentials. The app deliberately
        // does not embed them; provision a real administrator through a trusted
        // setup path before production use.
        const val LOCAL_ADMIN_EMAIL = "admin@local.demo"
        const val LOCAL_ADMIN_UID = "local-admin"
        const val LOCAL_ADMIN_PASSWORD_HASH =
            "2f441b3a48a433f4931311b899bf5e9931a9e3127622c2f50a5ed0a0f209a723"
        const val LOCAL_ADMIN_PASSWORD_NOTE = "Use the development credential documented in android-app/README.md."
        const val ADMIN_ROLE = "admin"

        suspend fun seedAdmin(context: Context) {
            val db = AppDatabase.getInstance(context)
            withContext(Dispatchers.IO) {
                if (db.userDao().findByEmail(LOCAL_ADMIN_EMAIL) == null) {
                    db.userDao().insert(
                        User(
                            uid = LOCAL_ADMIN_UID,
                            email = LOCAL_ADMIN_EMAIL,
                            passwordHash = LOCAL_ADMIN_PASSWORD_HASH,
                            role = ADMIN_ROLE,
                            status = LicenseStatus.APPROVED.wireValue,
                            expirationTimestamp = Long.MAX_VALUE,
                            deviceInfo = "Local administrator",
                        ),
                    )
                }
            }
        }
    }
}