package com.ultra.autodetector.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ultra.autodetector.data.model.Template

@Database(
    entities = [UserEntity::class, NoticeEntity::class, Template::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun noticeDao(): NoticeDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ultra_auto_detector.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}