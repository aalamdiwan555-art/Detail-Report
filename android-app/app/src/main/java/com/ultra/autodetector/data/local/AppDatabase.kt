package com.ultra.autodetector.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, NoticeEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun noticeDao(): NoticeDao

    companion object {
        private const val DATABASE_NAME = "ultra_auto_detector_v2.db"
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createDatabase(context: Context): AppDatabase {
            return try {
                Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
            } catch (e: Exception) {
                // CRITICAL FIX: If database is corrupted, delete and recreate
                context.deleteDatabase(DATABASE_NAME)
                Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }
    }
}
