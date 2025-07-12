package com.photosocialapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.photosocialapp.data.local.dao.DetectedImageDao
import com.photosocialapp.data.local.dao.FaceClusterDao
import com.photosocialapp.data.local.entity.Converters
import com.photosocialapp.data.local.entity.DetectedImageEntity
import com.photosocialapp.data.local.entity.FaceClusterEntity

@Database(
    entities = [
        DetectedImageEntity::class,
        FaceClusterEntity::class
    ],
    version = 2
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun detectedImageDao(): DetectedImageDao
    abstract fun faceClusterDao(): FaceClusterDao
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
                )
                .fallbackToDestructiveMigration() // Handle version changes
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
