package com.ultra.autodetector.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ultra.autodetector.data.AccountStatus
import com.ultra.autodetector.data.DetectionTemplate
import com.ultra.autodetector.data.PermissionState

internal data class LocalUserRecord(
    val uid: String,
    val email: String,
    val passwordHash: String,
    val isAdmin: Boolean,
    val status: AccountStatus,
    val expiresAtMillis: Long?,
)

/**
 * Private, on-device database for local mode.
 *
 * This database is intentionally not a replacement for a server database:
 * Android's application sandbox protects it on the device, but it does not
 * provide synchronization, shared users, or administrator trust across devices.
 */
class LocalDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                uid TEXT PRIMARY KEY NOT NULL,
                email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                password_hash TEXT NOT NULL,
                is_admin INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                expires_at_millis INTEGER,
                created_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE templates (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                confidence_threshold REAL NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                download_url TEXT NOT NULL DEFAULT '',
                created_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE app_state (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE admin_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                actor_uid TEXT NOT NULL,
                action TEXT NOT NULL,
                target_uid TEXT,
                details TEXT,
                created_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        seedData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Version 1 seeded a blank admin password. Replace it during
            // upgrade so existing local installs do not retain the bypass.
            db.update(
                "users",
                ContentValues().apply { put("password_hash", ADMIN_PASSWORD_HASH) },
                "uid = ?",
                arrayOf("local-admin"),
            )
        }
    }

    internal fun findUserByEmail(email: String): LocalUserRecord? =
        readableDatabase.query(
            "users",
            USER_COLUMNS,
            "email = ?",
            arrayOf(email.trim()),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toUser() else null }

    internal fun findUserByUid(uid: String): LocalUserRecord? =
        readableDatabase.query(
            "users",
            USER_COLUMNS,
            "uid = ?",
            arrayOf(uid),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toUser() else null }

    internal fun listUsers(): List<LocalUserRecord> =
        readableDatabase.query(
            "users",
            USER_COLUMNS,
            null,
            null,
            null,
            null,
            "created_at_millis ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toUser())
            }
        }

    internal fun saveUser(user: LocalUserRecord) {
        writableDatabase.insertWithOnConflict(
            "users",
            null,
            ContentValues().apply {
                put("uid", user.uid)
                put("email", user.email)
                put("password_hash", user.passwordHash)
                put("is_admin", if (user.isAdmin) 1 else 0)
                put("status", user.status.name)
                put("expires_at_millis", user.expiresAtMillis)
                put("created_at_millis", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    internal fun updateUserLicense(uid: String, status: AccountStatus, expiresAtMillis: Long?) {
        writableDatabase.update(
            "users",
            ContentValues().apply {
                put("status", status.name)
                put("expires_at_millis", expiresAtMillis)
            },
            "uid = ?",
            arrayOf(uid),
        )
    }

    fun saveTemplate(template: DetectionTemplate) {
        writableDatabase.insertWithOnConflict(
            "templates",
            null,
            ContentValues().apply {
                put("id", template.id)
                put("name", template.name)
                put("description", template.description)
                put("confidence_threshold", template.confidenceThreshold)
                put("is_active", if (template.isActive) 1 else 0)
                put("download_url", template.downloadUrl)
                put("created_at_millis", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun listTemplates(): List<DetectionTemplate> =
        readableDatabase.query(
            "templates",
            TEMPLATE_COLUMNS,
            null,
            null,
            null,
            null,
            "created_at_millis ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DetectionTemplate(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                            confidenceThreshold = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence_threshold")),
                            isActive = cursor.getInt(cursor.getColumnIndexOrThrow("is_active")) == 1,
                            downloadUrl = cursor.getString(cursor.getColumnIndexOrThrow("download_url")),
                        ),
                    )
                }
            }
        }

    fun deleteTemplate(id: String): String? {
        val downloadUrl = readableDatabase.query(
            "templates",
            arrayOf("download_url"),
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        writableDatabase.delete("templates", "id = ?", arrayOf(id))
        return downloadUrl
    }

    fun getSessionEmail(): String? = getStateValue("session_email")

    fun setSessionEmail(email: String?) {
        setStateValue("session_email", email)
    }

    fun getPermissionState(): PermissionState =
        PermissionState(
            accessibility = getStateValue("permission_accessibility") == "true",
            overlay = getStateValue("permission_overlay") == "true",
            screenCapture = getStateValue("permission_screen_capture") == "true",
        )

    fun savePermissionState(state: PermissionState) {
        setStateValue("permission_accessibility", state.accessibility.toString())
        setStateValue("permission_overlay", state.overlay.toString())
        setStateValue("permission_screen_capture", state.screenCapture.toString())
    }

    fun addAdminLog(actorUid: String, action: String, targetUid: String?, details: String? = null) {
        writableDatabase.insert(
            "admin_logs",
            null,
            ContentValues().apply {
                put("actor_uid", actorUid)
                put("action", action)
                put("target_uid", targetUid)
                put("details", details)
                put("created_at_millis", System.currentTimeMillis())
            },
        )
    }

    private fun getStateValue(key: String): String? =
        readableDatabase.query(
            "app_state",
            arrayOf("value"),
            "key = ?",
            arrayOf(key),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun setStateValue(key: String, value: String?) {
        writableDatabase.insertWithOnConflict(
            "app_state",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun seedData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val admin = ContentValues().apply {
            put("uid", "local-admin")
            put("email", "admin@local.demo")
            put("password_hash", ADMIN_PASSWORD_HASH)
            put("is_admin", 1)
            put("status", AccountStatus.ACTIVE.name)
            put("expires_at_millis", Long.MAX_VALUE)
            put("created_at_millis", now)
        }
        db.insert("users", null, admin)

        insertSeedUser(
            db = db,
            uid = "local-pending-user",
            email = "pending@local.demo",
            status = AccountStatus.PENDING,
            expiresAtMillis = null,
            createdAtMillis = now + 1,
        )
        insertSeedUser(
            db = db,
            uid = "local-active-user",
            email = "active@local.demo",
            status = AccountStatus.ACTIVE,
            expiresAtMillis = now + 2 * DAY_MILLIS,
            createdAtMillis = now + 2,
        )

        insertSeedTemplate(
            db = db,
            id = "template-primary",
            name = "Primary target",
            description = "Main target image used by the detector.",
            confidenceThreshold = 0.85f,
            createdAtMillis = now,
        )
        insertSeedTemplate(
            db = db,
            id = "template-secondary",
            name = "Secondary target",
            description = "Optional secondary target for testing.",
            confidenceThreshold = 0.9f,
            createdAtMillis = now + 1,
        )
    }

    private fun insertSeedUser(
        db: SQLiteDatabase,
        uid: String,
        email: String,
        status: AccountStatus,
        expiresAtMillis: Long?,
        createdAtMillis: Long,
    ) {
        db.insert(
            "users",
            null,
            ContentValues().apply {
                put("uid", uid)
                put("email", email)
                put("password_hash", "")
                put("is_admin", 0)
                put("status", status.name)
                put("expires_at_millis", expiresAtMillis)
                put("created_at_millis", createdAtMillis)
            },
        )
    }

    private fun insertSeedTemplate(
        db: SQLiteDatabase,
        id: String,
        name: String,
        description: String,
        confidenceThreshold: Float,
        createdAtMillis: Long,
    ) {
        db.insert(
            "templates",
            null,
            ContentValues().apply {
                put("id", id)
                put("name", name)
                put("description", description)
                put("confidence_threshold", confidenceThreshold)
                put("is_active", 1)
                put("download_url", "")
                put("created_at_millis", createdAtMillis)
            },
        )
    }

    private fun Cursor.toUser(): LocalUserRecord =
        LocalUserRecord(
            uid = getString(getColumnIndexOrThrow("uid")),
            email = getString(getColumnIndexOrThrow("email")),
            passwordHash = getString(getColumnIndexOrThrow("password_hash")),
            isAdmin = getInt(getColumnIndexOrThrow("is_admin")) == 1,
            status = runCatching {
                AccountStatus.valueOf(getString(getColumnIndexOrThrow("status")))
            }.getOrDefault(AccountStatus.PENDING),
            expiresAtMillis = getLongOrNull("expires_at_millis"),
        )

    private fun Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    companion object {
        private const val DATABASE_NAME = "ultra_auto_detector.db"
        private const val DATABASE_VERSION = 2
        private const val DAY_MILLIS = 86_400_000L
        /**
         * Hash for the local demo administrator password documented in README.
         * This account is only for local development and is not a production
         * authentication mechanism.
         */
        const val ADMIN_PASSWORD_HASH =
            "2f441b3a48a433f4931311b899bf5e9931a9e3127622c2f50a5ed0a0f209a723"
        private val USER_COLUMNS = arrayOf(
            "uid",
            "email",
            "password_hash",
            "is_admin",
            "status",
            "expires_at_millis",
        )
        private val TEMPLATE_COLUMNS = arrayOf(
            "id",
            "name",
            "description",
            "confidence_threshold",
            "is_active",
            "download_url",
        )
    }
}