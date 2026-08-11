package com.ultra.autodetector.data

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Production boundary for Firebase. It is selected only when Firebase has a
 * valid google-services configuration; local demo mode remains available for
 * development and previews without cloud credentials.
 */
class FirebaseRepository(private val context: Context) : AppRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val _state = MutableStateFlow(AppState())
    override val state: Flow<AppState> = _state

    override suspend fun login(email: String, password: String): Result<Account> = runCatching {
        val firebaseUser = auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Authentication failed")
        val account = loadAccount(firebaseUser.uid, firebaseUser.email.orEmpty())
        _state.value = _state.value.copy(account = account)
        account
    }

    override suspend fun register(email: String, password: String): Result<Account> = runCatching {
        val firebaseUser = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Registration failed")
        val account = Account(firebaseUser.uid, email, status = AccountStatus.PENDING)
        firestore.collection("users").document(account.uid).set(
            mapOf(
                "uid" to account.uid,
                "email" to account.email,
                "role" to "user",
                "status" to "pending",
                "expirationTimestamp" to null,
            ),
        ).await()
        _state.value = _state.value.copy(account = account, message = "Account created. Waiting for approval.")
        account
    }

    override suspend fun logout() {
        auth.signOut()
        _state.value = AppState()
    }

    override suspend fun refresh() {
        val current = auth.currentUser ?: return
        _state.value = _state.value.copy(account = loadAccount(current.uid, current.email.orEmpty()))
    }

    override suspend fun setPermissionState(state: PermissionState) {
        _state.value = _state.value.copy(permissionState = state)
    }

    override suspend fun setDetectorState(running: Boolean, paused: Boolean) {
        _state.value = _state.value.copy(isDetectorRunning = running, isDetectorPaused = paused)
    }

    override suspend fun grantLicense(uid: String, days: Int?) {
        val expires = days?.let { System.currentTimeMillis() + it * 86_400_000L } ?: Long.MAX_VALUE
        firestore.collection("users").document(uid).set(
            mapOf("status" to "approved", "expirationTimestamp" to expires),
            SetOptions.merge(),
        ).await()
        refresh()
    }

    override suspend fun rejectUser(uid: String) {
        firestore.collection("users").document(uid).set(
            mapOf("status" to "rejected"),
            SetOptions.merge(),
        ).await()
        refresh()
    }

    override suspend fun uploadTemplate(name: String, description: String, imageUri: Uri?): Result<DetectionTemplate> = runCatching {
        require(name.isNotBlank()) { "Template name is required." }
        requireNotNull(imageUri) { "Choose an image first." }
        val id = UUID.randomUUID().toString()
        val reference = storage.reference.child("templates/$id.png")
        reference.putFile(imageUri).await()
        val url = reference.downloadUrl.await().toString()
        val template = DetectionTemplate(id, name.trim(), description.trim(), downloadUrl = url)
        firestore.collection("templates").document(id).set(
            mapOf(
                "templateId" to template.id,
                "name" to template.name,
                "description" to template.description,
                "confidenceThreshold" to template.confidenceThreshold,
                "isActive" to template.isActive,
                "downloadUrl" to template.downloadUrl,
            ),
        ).await()
        _state.value = _state.value.copy(message = "Template uploaded")
        template
    }

    override suspend fun deleteTemplate(templateId: String) {
        firestore.collection("templates").document(templateId).delete().await()
        runCatching { storage.reference.child("templates/$templateId.png").delete().await() }
        refresh()
    }

    private suspend fun loadAccount(uid: String, fallbackEmail: String): Account {
        val snapshot = firestore.collection("users").document(uid).get().await()
        val data = snapshot.data.orEmpty()
        val role = data["role"] as? String
        val status = when (data["status"] as? String) {
            "approved" -> AccountStatus.ACTIVE
            "rejected" -> AccountStatus.REJECTED
            "expired" -> AccountStatus.EXPIRED
            else -> AccountStatus.PENDING
        }
        val expires = (data["expirationTimestamp"] as? Number)?.toLong()
        return Account(uid, data["email"] as? String ?: fallbackEmail, role == "admin", status, expires)
    }

    companion object {
        fun isConfigured(context: Context): Boolean =
            runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }
}