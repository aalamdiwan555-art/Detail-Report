package com.ultra.autodetector.data

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
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
        val account = loadAccount(
            firebaseUser.uid,
            firebaseUser.email.orEmpty(),
            firestore.collection("users").document(firebaseUser.uid).get().await().data.orEmpty(),
            isAdmin = hasAdminClaim(forceRefresh = true),
        )
        _state.value = _state.value.copy(account = account)
        refresh()
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

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        require(email.isNotBlank()) { "Enter your email address first." }
        auth.sendPasswordResetEmail(email.trim()).await()
        _state.value = _state.value.copy(message = "If that account exists, a reset email is on its way.")
    }

    override suspend fun logout() {
        auth.signOut()
        _state.value = AppState()
    }

    override suspend fun refresh() {
        val current = auth.currentUser ?: return
        val account = loadAccount(
            current.uid,
            current.email.orEmpty(),
            firestore.collection("users").document(current.uid).get().await().data.orEmpty(),
            isAdmin = hasAdminClaim(),
        )
        _state.value = _state.value.copy(
            account = account,
            templates = loadTemplates(),
        )
        if (account.isAdmin) refreshAdminData()
    }

    override suspend fun setPermissionState(state: PermissionState) {
        _state.value = _state.value.copy(permissionState = state)
    }

    override suspend fun setDetectorState(running: Boolean, paused: Boolean) {
        _state.value = _state.value.copy(isDetectorRunning = running, isDetectorPaused = paused)
    }

    override suspend fun refreshAdminData() {
        val current = _state.value.account ?: return
        if (!current.isAdmin) return
        val currentIsAdmin = hasAdminClaim()
        if (!currentIsAdmin) return
        val users = firestore.collection("users").get().await().documents
            .map { document ->
                loadAccount(
                    uid = document.id,
                    fallbackEmail = document.getString("email").orEmpty(),
                    data = document.data.orEmpty(),
                    isAdmin = document.id == current.uid && currentIsAdmin,
                )
            }
            .filterNot { it.uid == current.uid }
        _state.value = _state.value.copy(adminUsers = users)
    }

    override suspend fun grantLicense(uid: String, days: Int?) {
        require(_state.value.account?.isAdmin == true) { "Administrator access required." }
        val target = firestore.collection("users").document(uid).get().await()
        val currentExpiration = (target.get("expirationTimestamp") as? Number)?.toLong() ?: 0L
        val base = maxOf(System.currentTimeMillis(), currentExpiration)
        val expires = days?.let { addDaysWithoutOverflow(base, it) } ?: Long.MAX_VALUE
        val batch = firestore.batch()
        batch.set(
            firestore.collection("users").document(uid),
            mapOf(
                "status" to "approved",
                "expirationTimestamp" to expires,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
        batch.set(
            firestore.collection("adminLogs").document(),
            mapOf(
                "action" to "grant_license",
                "targetUid" to uid,
                "days" to days,
                "newExpirationTimestamp" to expires,
                "actorUid" to auth.currentUser?.uid,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
        refresh()
    }

    override suspend fun rejectUser(uid: String) {
        require(_state.value.account?.isAdmin == true) { "Administrator access required." }
        val batch = firestore.batch()
        batch.set(
            firestore.collection("users").document(uid),
            mapOf("status" to "rejected", "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
        batch.set(
            firestore.collection("adminLogs").document(),
            mapOf(
                "action" to "reject_user",
                "targetUid" to uid,
                "actorUid" to auth.currentUser?.uid,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
        refresh()
    }

    override suspend fun uploadTemplate(name: String, description: String, imageUri: Uri?): Result<DetectionTemplate> = runCatching {
        require(name.isNotBlank()) { "Template name is required." }
        requireNotNull(imageUri) { "Choose an image first." }
        val id = UUID.randomUUID().toString()
        val reference = storage.reference.child("templates/$id.png")
        try {
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
                    "createdAt" to FieldValue.serverTimestamp(),
                    "createdBy" to auth.currentUser?.uid,
                ),
            ).await()
            _state.value = _state.value.copy(message = "Template uploaded")
            template
        } catch (error: Exception) {
            runCatching { reference.delete().await() }
            throw IllegalStateException("Template upload could not be completed.", error)
        }
    }

    override suspend fun deleteTemplate(templateId: String) {
        require(_state.value.account?.isAdmin == true) { "Administrator access required." }
        firestore.collection("templates").document(templateId).delete().await()
        runCatching { storage.reference.child("templates/$templateId.png").delete().await() }
        refresh()
    }

    private fun loadAccount(
        uid: String,
        fallbackEmail: String,
        data: Map<String, Any?>,
        isAdmin: Boolean,
    ): Account {
        val status = when (data["status"] as? String) {
            "approved" -> AccountStatus.ACTIVE
            "rejected" -> AccountStatus.REJECTED
            "expired" -> AccountStatus.EXPIRED
            else -> AccountStatus.PENDING
        }
        val expires = (data["expirationTimestamp"] as? Number)?.toLong()
        return Account(uid, data["email"] as? String ?: fallbackEmail, isAdmin, status, expires)
    }

    private suspend fun hasAdminClaim(forceRefresh: Boolean = false): Boolean = runCatching {
        auth.currentUser?.getIdToken(forceRefresh)?.await()?.claims?.get("admin") == true
    }.getOrDefault(false)

    private suspend fun loadTemplates(): List<DetectionTemplate> =
        firestore.collection("templates").get().await().documents.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            DetectionTemplate(
                id = document.id,
                name = data["name"] as? String ?: return@mapNotNull null,
                description = data["description"] as? String ?: "",
                confidenceThreshold = (data["confidenceThreshold"] as? Number)?.toFloat() ?: 0.85f,
                isActive = data["isActive"] as? Boolean ?: true,
                downloadUrl = data["downloadUrl"] as? String ?: "",
            )
        }

    companion object {
        private fun addDaysWithoutOverflow(baseMillis: Long, days: Int): Long {
            require(days > 0) { "License duration must be positive." }
            val increment = days.toLong() * 86_400_000L
            return if (Long.MAX_VALUE - baseMillis < increment) Long.MAX_VALUE
            else baseMillis + increment
        }

        fun isConfigured(context: Context): Boolean =
            runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }
}