package com.photosocialapp.domain.usecase

import com.photosocialapp.domain.model.ImageModel
import com.photosocialapp.domain.repository.ImageRepository
import kotlinx.coroutines.flow.Flow

class GetImagesWithFacesUseCase(
    private val repository: ImageRepository
) {
    operator fun invoke(): Flow<List<ImageModel>> = repository.getImagesWithFaces()
}
