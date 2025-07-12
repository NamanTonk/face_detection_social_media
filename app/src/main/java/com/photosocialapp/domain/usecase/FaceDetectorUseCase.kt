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
import com.photosocialapp.data.local.dao.FaceClusterDao
import com.photosocialapp.data.local.entity.DetectedImageEntity
import com.photosocialapp.data.local.entity.FaceClusterEntity
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
import com.photosocialapp.data.local.entity.Converters
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceDetectorUseCase(
    private val context: Context,
    private val detectedImageDao: DetectedImageDao,
    private val faceClusterDao: FaceClusterDao
) {
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
    private val allFaceEmbeddings = mutableListOf<FloatArray>()
    private val faceData = mutableMapOf<FloatArray, Bitmap>()
    private var processedImageCount = 0
    private val NUM_CLUSTERS = 5
    private val MAX_ITERATIONS = 100
    private val clusters = mutableMapOf<Int, MutableList<FloatArray>>()

    private suspend fun detectFacesInBitmap(bitmap: Bitmap): Boolean = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces: List<Face> ->
                val hasFaces = faces.isNotEmpty()
                if (hasFaces) {
                    getFaceEmbedding(bitmap, faces)
                }
                continuation.resume(hasFaces)
            }
            .addOnFailureListener { e ->
                Log.e("ImageRepository", "Face detection failed", e)
                continuation.resume(false)
            }
    }

    private suspend fun saveClustersToDatabase(clusters: Map<Int, List<Bitmap>>) {
        clusters.forEach { (clusterId, faces) ->
            if (!faceClusterDao.isClusterExists(clusterId)) {
                    faces.forEach { faceBitmap ->
                        Log.d("CluserID--",">>>>>${faceClusterDao.isClusterExists(clusterId)}")
                        faceClusterDao.insertFace(  FaceClusterEntity(clusterId = clusterId, faceImage = Converters().fromBitmap(faceBitmap)))
                    }
            }
        }
    }

    private fun getFaceEmbedding(bitmap: Bitmap, faces: List<Face>) {
        // Process new faces
        for(face in faces) {
            val boundingBox = face.boundingBox
            val croppedFace = Bitmap.createBitmap(bitmap, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height())
            val resizedFace = croppedFace.scale(160, 160)
            val inputBuffer = bitmapToByteBuffer(resizedFace)
            val outputBuffer = Array(1) { FloatArray(128) }
            interpreter?.run(inputBuffer, outputBuffer)
            val embedding = outputBuffer[0]

            // Check if this face is significantly different from existing ones
            if (isNewFace(embedding)) {
                allFaceEmbeddings.add(embedding)
                faceData[embedding] = resizedFace
            }
        }
        processedImageCount++
        Log.d("FaceDetector", "Total unique faces: ${allFaceEmbeddings.size}")
         if (processedImageCount >= 3) {
            val clusters = performClustering()
            // Save clusters to database
             runBlocking {
                 saveClustersToDatabase(clusters)
             }
        }
    }

    private fun isNewFace(newEmbedding: FloatArray, similarityThreshold: Double = 0.6): Boolean {
        if (allFaceEmbeddings.isEmpty()) return true

        for (existing in allFaceEmbeddings) {
            val similarity = calculateCosineSimilarity(newEmbedding, existing)
            if (similarity > similarityThreshold) {
                return false // Face is too similar to an existing one
            }
        }
        return true
    }

    private fun calculateCosineSimilarity(a: FloatArray, b: FloatArray): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += (a[i] * b[i])
            normA += (a[i] * a[i])
            normB += (b[i] * b[i])
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))
    }

    private fun performClustering(): Map<Int, List<Bitmap>> {
        if (allFaceEmbeddings.isEmpty()) return emptyMap()

        // Initialize clusters with random centroids
        val centroids = allFaceEmbeddings.shuffled().take(NUM_CLUSTERS).map { it.clone() }.toMutableList()

        var iteration = 0
        var changed = true
        val finalClusters = mutableMapOf<Int, MutableList<Pair<FloatArray, Bitmap>>>()

        while (changed && iteration < MAX_ITERATIONS) {
            changed = false
            clusters.clear()
            finalClusters.clear()

            // Initialize empty clusters
            for (i in 0 until NUM_CLUSTERS) {
                clusters[i] = mutableListOf()
                finalClusters[i] = mutableListOf()
            }

            // Assign points to nearest centroid
            for (embedding in allFaceEmbeddings) {
                val nearestCentroid = findNearestCentroid(embedding, centroids)
                clusters[nearestCentroid]?.add(embedding)
                faceData[embedding]?.let { faceBitmap ->
                    finalClusters[nearestCentroid]?.add(embedding to faceBitmap)
                }
            }

            // Update centroids
            for (i in 0 until NUM_CLUSTERS) {
                val cluster = clusters[i] ?: continue
                if (cluster.isEmpty()) continue

                val newCentroid = calculateNewCentroid(cluster)
                if (!centroids[i].contentEquals(newCentroid)) {
                    centroids[i] = newCentroid
                    changed = true
                }
            }

            iteration++
        }

        // For each cluster, find the face closest to the centroid
        return finalClusters.mapValues { (clusterId, faceList) ->
            if (faceList.isEmpty()) emptyList()
            else {
                val centroid = centroids[clusterId] ?: return@mapValues emptyList()
                // Find the face embedding closest to the centroid
                val closestFace = faceList.minByOrNull { (embedding, _) ->
                    calculateEuclideanDistance(embedding, centroid)
                }
                // Return only the most representative face bitmap
                listOf(closestFace!!.second)
            }
        }.filterValues { it.isNotEmpty() }
    }

    fun getClusteredFaces(): Map<Int, List<Bitmap>> {
        return if (allFaceEmbeddings.size >= 3) {
            performClustering()
        } else {
            emptyMap()
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

    private fun findNearestCentroid(embedding: FloatArray, centroids: List<FloatArray>): Int {
        var nearestIndex = -1
        var minDistance = Double.MAX_VALUE

        for (i in centroids.indices) {
            val distance = calculateEuclideanDistance(embedding, centroids[i])
            if (distance < minDistance) {
                minDistance = distance
                nearestIndex = i
            }
        }

        return nearestIndex
    }

    private fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Double {
        var sum = 0.0
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return Math.sqrt(sum)
    }

    private fun calculateNewCentroid(cluster: List<FloatArray>): FloatArray {
        val centroid = FloatArray(cluster[0].size)
        for (embedding in cluster) {
            for (i in embedding.indices) {
                centroid[i] += embedding[i]
            }
        }
        for (i in centroid.indices) {
            centroid[i] /= cluster.size
        }
        return centroid
    }

}
