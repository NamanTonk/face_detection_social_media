package com.photosocialapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detected_images")
data class DetectedImageEntity(
    @PrimaryKey
    val uri: String,
    val timestamp: Long = System.currentTimeMillis()
)
