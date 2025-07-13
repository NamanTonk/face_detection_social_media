package com.photosocialapp.domain.usecase

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.google.mlkit.vision.face.Face
import com.photosocialapp.data.local.dao.FaceClusterDao
import com.photosocialapp.data.local.entity.Converters
import com.photosocialapp.data.local.entity.FaceClusterEntity
import com.photosocialapp.domain.clustering.KMeansClusterer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.collections.set

class FaceEmbaddingGenrator(private  val faceClusterDao: FaceClusterDao, private val assetManager: AssetManager) {
    private val allFaceEmbeddings = mutableListOf<FloatArray>()
    private val faceData = mutableMapOf<FloatArray, Bitmap>()
    private var processedImageCount = 0
    private var interpreter: Interpreter?=null
    operator fun invoke(bitmap: Bitmap, faces: List<Face>) {
        // TF_LiteModel Load
        interpreter =  Interpreter(getFaceNetByteBuffer(assetManager, "facenet.tflite"))

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
        if (processedImageCount >= 1) {
            // Save clusters to database
            runBlocking(Dispatchers.Default) {
                val clusters = KMeansClusterer().performClustering(allFaceEmbeddings,faceData)
                saveClustersToDatabase(clusters)
            }
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
