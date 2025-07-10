package com.photosocialapp.domain.repository

import com.photosocialapp.domain.model.ImageModel
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    fun getImagesWithFaces(): Flow<List<ImageModel>>

    /**
     * Syncs images by checking for faces in images where hadFace is false.
     * Updates the database if faces are found.
     * @return Flow of updated image list
     */
     fun syncImagesWithFaceDetection(): Flow<List<ImageModel>>
}
