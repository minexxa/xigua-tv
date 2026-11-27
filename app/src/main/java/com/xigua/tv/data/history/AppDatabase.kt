package com.xigua.tv.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WatchHistory::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xigua_tv.db",
                ).fallbackToDestructiveMigration() // 全新安装策略：版本变更直接重建，不写迁移代码
                    .build().also { INSTANCE = it }
            }
        }
    }
}
