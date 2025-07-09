package com.photosocialapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.photosocialapp.data.local.dao.DetectedImageDao
import com.photosocialapp.data.local.entity.DetectedImageEntity

@Database(entities = [DetectedImageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun detectedImageDao(): DetectedImageDao

    companion object {
        private const val DATABASE_NAME = "photo_social_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
