package com.photosocialapp.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
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

/**
 * Implementation of ImageRepository that handles image processing and face detection
 * using ML Kit and local Room database caching
 */
class ImageRepositoryImpl(
    private val context: Context
) : ImageRepository {
    private val database = AppDatabase.getInstance(context)
    private val detectedImageDao = database.detectedImageDao()

    // Initialize ML Kit face detector with fast performance mode
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    override fun getImagesWithFaces(): Flow<List<ImageModel>> = flow {
        val imageList = mutableListOf<ImageModel>()

        // Query media store for all images
        val cursor = queryMediaStore()

        cursor?.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val uri = getImageUri(cursor.getLong(idColumn))
                processImage(uri, imageList)?.let { newList ->
                    emit(newList)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Queries the MediaStore for images sorted by date added
     */
    private fun queryMediaStore() = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        ),
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )

    /**
     * Creates a content URI for an image based on its ID
     */
    private fun getImageUri(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    /**
     * Processes a single image: checks cache or performs face detection
     * Returns updated image list if a face is found
     */
    private suspend fun processImage(uri: Uri, imageList: MutableList<ImageModel>): List<ImageModel>? {
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
            val hasFaces = loadBitmapAndDetectFace(uri)

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

    /**
     * Loads bitmap from URI and performs face detection
     * Returns true if faces are found in the image
     */
    private suspend fun loadBitmapAndDetectFace(uri: Uri): Boolean {
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
        detector.process(image).result?:false
//            .addOnSuccessListener { faces ->
//                continuation.resume(faces.isNotEmpty())
//            }
//            .addOnFailureListener { e ->
//                Log.e("ImageRepository", "Face detection failed", e)
//                continuation.resume(false)
//            }
    }
}
