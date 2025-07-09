package com.photosocialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.photosocialapp.data.local.entity.DetectedImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedImageDao {
    @Query("SELECT * FROM detected_images ORDER BY timestamp DESC")
    fun getAllDetectedImages(): Flow<List<DetectedImageEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM detected_images WHERE uri = :uri)")
    suspend fun isImageDetected(uri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedImage(image: DetectedImageEntity)
}
