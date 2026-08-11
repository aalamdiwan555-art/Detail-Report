package com.ultra.autodetector.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ultra.autodetector.data.model.Template
import com.ultra.autodetector.data.model.User
import com.ultra.autodetector.util.Constants
import kotlinx.coroutines.tasks.await

/**
 * Firestore data boundary. Authorization is enforced again by Firestore
 * rules; these checks only make UI mistakes fail early.
 */
class FirestoreManager(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val users = firestore.collection(Constants.COLLECTION_USERS)
    private val templates = firestore.collection(Constants.COLLECTION_TEMPLATES)
    private val adminLogs = firestore.collection(Constants.COLLECTION_ADMIN_LOGS)

    suspend fun createUser(user: User) {
        users.document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "email" to user.email,
                "role" to Constants.ROLE_USER,
                "status" to Constants.STATUS_PENDING,
                "expirationTimestamp" to null,
            ),
        ).await()
    }

    suspend fun getUser(uid: String): User? {
        val document = users.document(uid).get().await()
        return document.data?.let { mapUser(document.id, it) }
    }

    suspend fun listUsers(): List<User> =
        users.get().await().documents.mapNotNull { document ->
            document.data?.let { mapUser(document.id, it) }
        }

    suspend fun updateLastLogin(uid: String) {
        users.document(uid).set(
            mapOf("lastLoginAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun grantLicense(uid: String, days: Int?) {
        requireAdmin()
        val document = users.document(uid).get().await()
        val currentExpiration = (document.get("expirationTimestamp") as? Number)?.toLong() ?: 0L
        val base = maxOf(System.currentTimeMillis(), currentExpiration)
        val expiration = days?.let { base + it * Constants.ONE_DAY_MS } ?: Constants.LIFETIME_MS
        val batch = firestore.batch()
        batch.set(
            users.document(uid),
            mapOf(
                "status" to Constants.STATUS_APPROVED,
                "expirationTimestamp" to expiration,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
        batch.set(
            adminLogs.document(),
            mapOf(
                "action" to "grant_license",
                "targetUid" to uid,
                "days" to days,
                "newExpirationTimestamp" to expiration,
                "actorUid" to auth.currentUser?.uid,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
    }

    suspend fun rejectUser(uid: String) {
        requireAdmin()
        val batch = firestore.batch()
        batch.set(
            users.document(uid),
            mapOf("status" to Constants.STATUS_REJECTED, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
        batch.set(
            adminLogs.document(),
            mapOf(
                "action" to "reject_user",
                "targetUid" to uid,
                "actorUid" to auth.currentUser?.uid,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
    }

    suspend fun listTemplates(): List<Template> =
        templates.get().await().documents.mapNotNull { document ->
            document.data?.let { mapTemplate(document.id, it) }
        }

    suspend fun createTemplate(template: Template, createdBy: String): Template {
        requireAdmin()
        templates.document(template.templateId).set(
            mapOf(
                "templateId" to template.templateId,
                "name" to template.name,
                "description" to template.description,
                "confidenceThreshold" to template.confidenceThreshold.coerceIn(0.0, 1.0),
                "isActive" to template.isActive,
                "downloadUrl" to template.downloadUrl,
                "createdAt" to FieldValue.serverTimestamp(),
                "createdBy" to createdBy,
            ),
        ).await()
        return template
    }

    suspend fun deleteTemplate(templateId: String) {
        requireAdmin()
        templates.document(templateId).delete().await()
    }

    private suspend fun requireAdmin() {
        val user = auth.currentUser ?: error("Authentication required.")
        val isAdmin = user.getIdToken(false).await()?.claims?.get("admin") == true
        require(isAdmin) { "Administrator access required." }
    }

    private fun mapUser(uid: String, data: Map<String, Any?>): User =
        User(
            uid = uid,
            email = data["email"] as? String ?: "",
            role = data["role"] as? String ?: Constants.ROLE_USER,
            status = data["status"] as? String ?: Constants.STATUS_PENDING,
            expirationTimestamp = (data["expirationTimestamp"] as? Number)?.toLong(),
            createdAt = data["createdAt"] as? Timestamp,
            lastLoginAt = data["lastLoginAt"] as? Timestamp,
            deviceInfo = data["deviceInfo"] as? String ?: "",
        )

    private fun mapTemplate(id: String, data: Map<String, Any?>): Template =
        Template(
            templateId = data["templateId"] as? String ?: id,
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            confidenceThreshold = (data["confidenceThreshold"] as? Number)?.toDouble() ?: Constants.CONFIDENCE_THRESHOLD,
            isActive = data["isActive"] as? Boolean ?: true,
            downloadUrl = data["downloadUrl"] as? String ?: "",
            createdAt = data["createdAt"] as? Timestamp,
        )
}