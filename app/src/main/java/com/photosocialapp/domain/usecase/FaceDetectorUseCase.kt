package com.photosocialapp.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.photosocialapp.data.local.dao.DetectedImageDao
import com.photosocialapp.data.local.entity.DetectedImageEntity
import com.photosocialapp.domain.model.ImageModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FaceDetectorUseCase(private val context: Context, private val detectedImageDao: DetectedImageDao) {
    // Initialize ML Kit face detector with fast performance mode


    /**
     * Processes a single image: checks cache or performs face detection
     * Returns updated image list if a face is found
     */
     suspend operator fun invoke(uri: Uri, imageList: MutableList<ImageModel>): List<ImageModel>? {
        return try {
            // Check cache first
            val cachedImage = detectedImageDao.getDetectedImage(uri.toString())
            if (cachedImage != null) {
                if (cachedImage.hadFace) {
                    imageList.add(ImageModel(uri.toString()))
                    return imageList.toList()
                }
                return null
            }

            // Process new image if not in cache
            Log.d("ImageRepository", "Processing START WITH: $uri")
            val hasFaces = hasFaceAvailableInImage(uri)

            // Cache result and return updated list if faces found
            detectedImageDao.insertDetectedImage(DetectedImageEntity(uri.toString(), hadFace = hasFaces))
            if (hasFaces) {
                imageList.add(ImageModel(uri.toString()))
                return imageList.toList()
            }
            null
        } catch (e: Exception) {
            Log.e("ImageRepository", "Error processing image: $uri", e)
            null
        }
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    /**
     * Loads bitmap from URI and performs face detection
     * Returns true if faces are found in the image
     */
    private  suspend  fun hasFaceAvailableInImage(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val hasFaces = detectFacesInBitmap(bitmap)
                    bitmap.recycle()
                    hasFaces
                } else false
            } ?: false
        } catch (e: Exception) {
            Log.e("ImageRepository", "Error loading bitmap: $uri", e)
            false
        }
    }
    /**
     * Uses ML Kit to detect faces in a bitmap
     * Returns true if any faces are detected
     */
    private suspend fun detectFacesInBitmap(bitmap: Bitmap): Boolean = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces ->
                continuation.resume(faces.isNotEmpty())
            }
            .addOnFailureListener { e ->
                Log.e("ImageRepository", "Face detection failed", e)
                continuation.resume(false)
            }
    }


}