package com.photosocialapp.domain.usecase

import android.graphics.Bitmap
import com.photosocialapp.domain.model.ImageModel
import com.photosocialapp.domain.repository.ImageRepository
import kotlinx.coroutines.flow.Flow

class GetImagesWithFacesUseCase(
    private val repository: ImageRepository
) {
    /**
     * Retrieves a [Flow] of [List] of [ImageModel] objects that are known to have faces.
     * This typically fetches images already processed and marked as having faces from the repository.
     */
    operator fun invoke(faceCategory: (Set<Bitmap>) -> Unit): Flow<List<ImageModel>> = repository.getImagesWithFaces( faceCategory)

    /**
     * Triggers a synchronization process for local images to detect faces.
     * Returns a [Flow] of [List] of [ImageModel] objects that were identified to have faces
     * during this synchronization process. This is useful for updating the local database
     * with the latest face detection results for all images.
     */
    fun syncLocalImages(): Flow<List<ImageModel>> = repository.syncImagesWithFaceDetection()
}
