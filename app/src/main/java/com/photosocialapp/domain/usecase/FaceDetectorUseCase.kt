package com.photosocialapp.domain.usecase

import android.content.ContentResolver
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
import androidx.core.net.toUri
import com.google.mlkit.vision.face.Face

class FaceDetectorUseCase(
    private val contentResolver: ContentResolver,
    private val detectedImageDao: DetectedImageDao,
    private val faceEmbeddingGenerator : FaceEmbeddingGenerator
) {
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
            }

            // Process new image if not in cache
            Log.d("ImageRepository", "Processing START WITH: $uri")
            val hasFaces = hasFaceAvailableInImage(uri)
            Log.d("HasFace", "$uri ====: $hasFaces")

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
            contentResolver.openInputStream(uri)?.use { inputStream ->
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
            .addOnSuccessListener { faces: List<Face> ->
                val hasFaces = faces.isNotEmpty()
                if (hasFaces) faceEmbeddingGenerator.invoke(bitmap,faces)
                continuation.resume(hasFaces)
            }
            .addOnFailureListener { e ->
                Log.e("ImageRepository", "Face detection failed", e)
                continuation.resume(false)
            }
    }

    /**
     * Processes all images that haven't been checked for faces yet (hadFace = false)
     * @return List of ImageModel for images where faces were found
     */
    suspend fun syncLocalDBImages(): List<ImageModel> {
        val imagesWithFaces = mutableListOf<ImageModel>()
        val unprocessedImages = detectedImageDao.getUnprocessedImages()
        Log.d("Update", "Unprocessed images: $unprocessedImages")
        unprocessedImages.forEach { entity ->
            val uri = entity.uri.toUri()
            val hasFaces = hasFaceAvailableInImage(uri)
            // If faces were found, add to the result list
            // Update the database with the new face detection result
            detectedImageDao.insertDetectedImage(entity.copy(hadFace = hasFaces))
            if (hasFaces) {
                imagesWithFaces.add(ImageModel(entity.uri))
            }
        }
        return imagesWithFaces
    }


}
