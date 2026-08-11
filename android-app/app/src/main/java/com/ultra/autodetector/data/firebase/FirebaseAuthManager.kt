package com.ultra.autodetector.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.ultra.autodetector.data.model.User
import kotlinx.coroutines.tasks.await

/**
 * Small authentication boundary used by production integrations.
 *
 * Administrator access is derived from a trusted Firebase custom claim. The
 * app never compares an email address or password to decide whether a user is
 * an administrator.
 */
class FirebaseAuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestoreManager: FirestoreManager = FirestoreManager(),
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isLoggedIn: Boolean
        get() = currentUser != null

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        require(email.isNotBlank()) { "Email is required." }
        require(password.isNotEmpty()) { "Password is required." }
        val firebaseUser = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Authentication failed.")
        firestoreManager.updateLastLogin(firebaseUser.uid)
        firestoreManager.getUser(firebaseUser.uid)
            ?: error("Your account profile is not available yet.")
    }

    suspend fun register(email: String, password: String, deviceInfo: String): Result<User> = runCatching {
        require(email.isNotBlank()) { "Email is required." }
        val firebaseUser = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Registration failed.")
        val user = User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: email.trim(),
            deviceInfo = deviceInfo,
        )
        firestoreManager.createUser(user)
        user
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        require(email.isNotBlank()) { "Enter your email address first." }
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun logout() {
        auth.signOut()
    }

    /**
     * Forces a token refresh before reading claims. This avoids stale role
     * state after an administrator is granted or revoked access.
     */
    suspend fun hasAdminClaim(forceRefresh: Boolean = true): Boolean = runCatching {
        val user = currentUser ?: return false
        user.getIdToken(forceRefresh).await()?.claims?.get("admin") == true
    }.getOrDefault(false)
}