package com.photosocialapp.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.photosocialapp.data.local.AppDatabase
import com.photosocialapp.data.local.entity.DetectedImageEntity
import com.photosocialapp.domain.model.ImageModel
import com.photosocialapp.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ImageRepositoryImpl(
    private val context: Context
) : ImageRepository {
    private val database = AppDatabase.getInstance(context)
    private val detectedImageDao = database.detectedImageDao()

    override fun getImagesWithFaces(): Flow<List<ImageModel>> = flow {
        val imageList = mutableListOf<ImageModel>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                try {
                    // Check if image is already in cache
                    if (detectedImageDao.isImageDetected(uri.toString())) {
                        imageList.add(ImageModel(uri.toString()))
                        emit(imageList.toList())
                        continue
                    }

                    // If not in cache, process with ML Kit
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val hasFaces = suspendCoroutine<Boolean> { continuation ->
                            detector.process(image)
                                .addOnSuccessListener { faces ->
                                    continuation.resume(faces.isNotEmpty())
                                }
                                .addOnFailureListener {
                                    continuation.resume(false)
                                }
                        }

                        if (hasFaces) {
                            // Cache the result
                            detectedImageDao.insertDetectedImage(DetectedImageEntity(uri.toString()))
                            imageList.add(ImageModel(uri.toString()))
                            emit(imageList.toList())
                        }
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    continue
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
