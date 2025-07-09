package com.photosocialapp.domain.repository

import com.photosocialapp.domain.model.ImageModel
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    fun getImagesWithFaces(): Flow<List<ImageModel>>
}
