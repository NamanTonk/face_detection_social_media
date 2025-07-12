package com.photosocialapp.domain.usecase

import android.content.Context
import android.content.res.AssetManager
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
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import androidx.core.graphics.scale
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceDetectorUseCase(private val context: Context, private val detectedImageDao: DetectedImageDao) {
    // Initialize ML Kit face detector with fast performance mode

    /**
     * Processes a single image: checks cache or performs face detection
     * Returns updated image list if a face is found
     */
    private var interpreter: Interpreter?=null
     suspend operator fun invoke(uri: Uri, imageList: MutableList<ImageModel>): List<ImageModel>? {
        return try {
            // TF_LiteModel Load
            interpreter =  Interpreter(getFaceNetByteBuffer(context.assets, "facenet.tflite"))
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
    val allFaceEmbeddings = mutableListOf<FloatArray>()
    val faceData = mutableMapOf<FloatArray, Bitmap>() // Optional: to link embedding back to a face image
    var processedImageCount = 0

    private suspend fun detectFacesInBitmap(bitmap: Bitmap): Boolean = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces: List<Face> ->
                if(faces.isNotEmpty()) getFaceEmbedding(bitmap,faces)
                continuation.resume(faces.isNotEmpty())
            }
            .addOnFailureListener { e ->
                Log.e("ImageRepository", "Face detection failed", e)
                continuation.resume(false)
            }
    }

    private  fun getFaceEmbedding(bitmap:Bitmap, faces: List<Face>) {
        for(face in faces){
            val boundingBox = face.boundingBox
            val croppedFace =  Bitmap.createBitmap(bitmap, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height())
            val resizedFace = croppedFace.scale(160, 160)
            val inputBuffer = bitmapToByteBuffer(resizedFace)
            val outputBuffer = Array(1) { FloatArray(128) }
             interpreter?.run(inputBuffer,outputBuffer)
            val embedding = outputBuffer[0]
            allFaceEmbeddings.add(embedding)
            faceData[embedding] = croppedFace
        }
        processedImageCount++
        println("Embadding Genrating.... ${allFaceEmbeddings.size}")
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

    private fun getFaceNetByteBuffer(assetManager: AssetManager, modelPath: String) : MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 160 // The width and height of the model's input
        val numBytesPerChannel = 4 // Float size
        val imageMean = 127.5f
        val imageStd = 127.5f

        // Allocate a ByteBuffer for the image data.
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * numBytesPerChannel)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Iterate over the pixels and convert them to floating-point values.
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val `val` = intValues[pixel++]
                // Normalize the pixel values from [0, 255] to [-1, 1].
                byteBuffer.putFloat((((`val` shr 16) and 0xFF) - imageMean) / imageStd) // Red
                byteBuffer.putFloat((((`val` shr 8) and 0xFF) - imageMean) / imageStd)  // Green
                byteBuffer.putFloat(((`val` and 0xFF) - imageMean) / imageStd)          // Blue
            }
        }
        return byteBuffer
    }

}