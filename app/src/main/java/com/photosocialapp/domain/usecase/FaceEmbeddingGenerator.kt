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
import kotlin.math.sqrt

/**
 * **Use Case: Generating Face Embeddings and Orchestrating Clustering**
 *
 * This class is a core component of the face recognition pipeline. Its primary responsibilities are:
 * 1.  **Embedding Generation**: To take raw pixel data of a detected face and, using a pre-trained
 *     TensorFlow Lite model (FaceNet), convert it into a numerical vector called an "embedding".
 *     This embedding is a compact, mathematical representation of the face's unique features.
 * 2.  **Uniqueness Filtering**: To compare a new face embedding against all previously processed ones.
 *     It uses cosine similarity to ensure that only genuinely new and distinct faces are added to
 *     the processing queue, preventing redundant work on near-duplicate images.
 * 3.  **Clustering Orchestration**: After processing a batch of images, it triggers the
 *     `KMeansClusterer` to group all the unique face embeddings into clusters, effectively
 *     identifying distinct people across the photo gallery.
 * 4.  **Persistence**: To save the results of the clustering (the cluster ID and a representative
 *     face image for each cluster) into the local Room database for later retrieval.
 *
 * @property faceClusterDao Data Access Object for storing and retrieving face cluster information.
 * @property assetManager Used to load the TensorFlow Lite model from the app's assets directory.
 */
class FaceEmbeddingGenerator(
    private val faceClusterDao: FaceClusterDao,
    private val assetManager: AssetManager
) {

    // --- State Properties ---
    private val allFaceEmbeddings = mutableListOf<FloatArray>()
    private val faceData = mutableMapOf<FloatArray, Bitmap>()
    private var processedImageCount = 0

    // --- TFLite Interpreter ---
    // The interpreter is initialized once in the constructor for efficiency.
    // Re-loading the model from assets on every invocation would be very slow.
    private val interpreter: Interpreter

    // --- Model and Image Constants ---
    companion object {
        private const val MODEL_FILE = "facenet_512_int_quantized.tflite" // The name of the TFLite model file in assets
        private const val INPUT_IMAGE_SIZE = 160 // The width and height of the model's expected input image
        private const val EMBEDDING_SIZE = 512 // The size of the output embedding vector from the model
        private const val SIMILARITY_THRESHOLD = 0.6 // Threshold for cosine similarity to consider faces "the same"
    }

    init {
        // Load the TFLite model from assets and initialize the interpreter.
        val modelBuffer = loadModelFile(assetManager, MODEL_FILE)
        interpreter = Interpreter(modelBuffer)
    }

    /**
     * Main entry point of the use case. Processes a list of detected faces from a single bitmap.
     *
     * @param bitmap The source image from which faces were detected.
     * @param faces A list of [Face] objects detected by ML Kit in the bitmap.
     */
    operator fun invoke(bitmap: Bitmap, faces: List<Face>) {
        // Process each face found in the image
        for (face in faces) {
            // Step 1: Crop the face from the larger image using the bounding box from ML Kit.
            val boundingBox = face.boundingBox
            val croppedFace = Bitmap.createBitmap(
                bitmap,
                boundingBox.left,
                boundingBox.top,
                boundingBox.width(),
                boundingBox.height()
            )

            // Step 2: Resize the cropped face to the required input size of the model (160x160).
            val resizedFace = croppedFace.scale(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)

            // Step 3: Convert the resized bitmap into a ByteBuffer and generate the embedding.
            val inputBuffer = bitmapToByteBuffer(resizedFace)
            val outputBuffer = Array(1) { FloatArray(EMBEDDING_SIZE) }
            interpreter.run(inputBuffer, outputBuffer)
            val embedding = outputBuffer[0]

            // Step 4: Check if this face is genuinely new or a duplicate of one we've already seen.
            if (isNewFace(embedding)) {
                allFaceEmbeddings.add(embedding)
                faceData[embedding] = resizedFace
            }
        }

        processedImageCount++
        Log.d("FaceEmbeddingGenerator", "Total unique faces found so far: ${allFaceEmbeddings.size}")

        // Note: This logic triggers clustering after every image containing a new face.
        // For larger datasets, it would be more efficient to trigger this manually
        // or on a periodic background job rather than on every single image.
        if (processedImageCount >= 1) { // This condition seems to trigger clustering frequently.
            runBlocking(Dispatchers.Default) {
                val clusters = KMeansClusterer().performClustering(allFaceEmbeddings, faceData)
                saveClustersToDatabase(clusters)
            }
        }
    }

    /**
     * Saves the final clusters into the Room database.
     * It checks if a cluster already exists to avoid duplicate entries.
     * @param clusters A map where the key is the cluster ID and the value is a list of face bitmaps.
     */
    private suspend fun saveClustersToDatabase(clusters: Map<Int, List<Bitmap>>) {
        clusters.forEach { (clusterId, faces) ->
            // Only insert if this cluster ID hasn't been saved before.
            if (!faceClusterDao.isClusterExists(clusterId)) {
                faces.forEach { faceBitmap ->
                    Log.d("FaceEmbeddingGenerator", "Saving new face for cluster ID: $clusterId")
                    val entity = FaceClusterEntity(
                        clusterId = clusterId,
                        faceImage = Converters().fromBitmap(faceBitmap)
                    )
                    faceClusterDao.insertFace(entity)
                }
            }
        }
    }

    /**
     * Checks if a new face embedding is distinct enough from already stored embeddings.
     * @param newEmbedding The embedding of the newly detected face.
     * @return `true` if the face is considered new, `false` if it's too similar to an existing one.
     */
    private fun isNewFace(newEmbedding: FloatArray): Boolean {
        if (allFaceEmbeddings.isEmpty()) return true

        // Compare the new embedding with all existing ones using cosine similarity.
        for (existingEmbedding in allFaceEmbeddings) {
            val similarity = calculateCosineSimilarity(newEmbedding, existingEmbedding)
            if (similarity > SIMILARITY_THRESHOLD) {
                return false // Found a very similar face, so this one is not new.
            }
        }
        return true // No similar faces found.
    }

    /**
     * Calculates the cosine similarity between two vectors (embeddings).
     * The result ranges from -1 (completely different) to 1 (identical).
     * A value greater than the threshold suggests the faces are of the same person.
     * @return The cosine similarity as a Double.
     */
    private fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Double {
        val dotProduct = vec1.zip(vec2).sumOf { (a, b) -> (a * b).toDouble() }
        val normA = sqrt(vec1.sumOf { (it * it).toDouble() })
        val normB = sqrt(vec2.sumOf { (it * it).toDouble() })
        return dotProduct / (normA * normB)
    }

    /**
     * Loads the TFLite model from the assets folder into a MappedByteBuffer.
     * This is the required format for the TensorFlow Lite Interpreter.
     */
    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Converts a Bitmap into a ByteBuffer in the format required by the FaceNet model.
     * This involves:
     * - Resizing to the model's input dimensions (160x160).
     * - Normalizing the pixel values from [0, 255] to a range of [-1, 1].
     * - Arranging the pixel data (R, G, B) into a flat buffer.
     * @param bitmap The input bitmap, expected to be 160x160 pixels.
     * @return A ByteBuffer containing the normalized image data.
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4) // 1 image * 160x160 * 3 channels * 4 bytes/float
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val imageMean = 127.5f
        val imageStd = 127.5f

        // Iterate through each pixel and convert it to a normalized float.
        for (pixelValue in pixels) {
            // The model expects pixel values to be normalized from [0, 255] to [-1, 1].
            byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) - imageMean) / imageStd) // Red
            byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) - imageMean) / imageStd)  // Green
            byteBuffer.putFloat(((pixelValue and 0xFF) - imageMean) / imageStd)          // Blue
        }
        return byteBuffer
    }
}
