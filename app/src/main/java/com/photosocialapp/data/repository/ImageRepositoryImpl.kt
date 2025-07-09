package com.photosocialapp.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.photosocialapp.domain.model.ImageModel
import com.photosocialapp.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ImageRepositoryImpl(
    private val context: Context
) : ImageRepository {
    override fun getImagesWithFaces(): Flow<List<ImageModel>> = flow {
        val imageList = mutableListOf<ImageModel>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)

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
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val hasFaces = suspendCoroutine { continuation ->
                            detector.process(image)
                                .addOnSuccessListener { faces ->
                                    continuation.resume(faces.isNotEmpty())
                                }
                                .addOnFailureListener {
                                    continuation.resume(false)
                                }
                        }

                        if (hasFaces) {
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
    }.flowOn(Dispatchers.IO)}
