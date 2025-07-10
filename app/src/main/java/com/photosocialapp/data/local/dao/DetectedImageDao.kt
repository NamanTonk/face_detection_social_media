package com.photosocialapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.photosocialapp.data.local.entity.DetectedImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedImageDao {
    @Query("SELECT * FROM detected_images WHERE hadFace = 1 ORDER BY timestamp DESC")
    fun getAllDetectedImages(): Flow<List<DetectedImageEntity>>

    @Query("SELECT * FROM detected_images WHERE uri = :uri LIMIT 1")
    suspend fun getDetectedImage(uri: String): DetectedImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedImage(image: DetectedImageEntity)
}
