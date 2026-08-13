package com.ultra.autodetector.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlin.LazyThreadSafetyMode

@Database(
    entities = [UserEntity::class, NoticeEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun noticeDao(): NoticeDao

    companion object {
        private const val DATABASE_NAME = "ultra_auto_detector.db"
        @Volatile private var applicationContext: Context? = null
        private val instance = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            createDatabase(requireNotNull(applicationContext))
        }

        fun getInstance(context: Context): AppDatabase {
            applicationContext = context.applicationContext
            return try {
                instance.value
            } catch (error: Exception) {
                throw IllegalStateException("Unable to open local authentication database", error)
            }
        }

        private fun createDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext
            return try {
                Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).fallbackToDestructiveMigration().build()
            } catch (firstFailure: Exception) {
                // A partially restored/corrupt local database should not take
                // down the login activity. The schema is intentionally local
                // and destructive migration is already the app's policy.
                appContext.deleteDatabase(DATABASE_NAME)
                runCatching {
                    Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DATABASE_NAME,
                    ).fallbackToDestructiveMigration().build()
                }.getOrElse { throw firstFailure }
            }
    }
}