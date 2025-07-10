package com.photosocialapp.data.repository

import android.content.ContentUris
import android.provider.MediaStore
import com.photosocialapp.domain.model.ImageModel
import com.photosocialapp.domain.repository.ImageRepository
import com.photosocialapp.domain.usecase.FaceDetectorUseCase
import com.photosocialapp.domain.usecase.ImageFetchFromGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

/**
 * Implementation of ImageRepository that handles image processing and face detection
 * using ML Kit and local Room database caching
 */
class ImageRepositoryImpl(
    private val imageFetchFromGallery: ImageFetchFromGallery,
    private val faceCheckUseCase: FaceDetectorUseCase
) : ImageRepository {

    override fun getImagesWithFaces(): Flow<List<ImageModel>> = flow {
        val imageList = mutableListOf<ImageModel>()
        // Query media store for all images
        val cursor = imageFetchFromGallery()
        cursor?.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
              val uri =   ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn))
                faceCheckUseCase.invoke(uri, imageList)?.let { newList ->
                    emit(newList)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
