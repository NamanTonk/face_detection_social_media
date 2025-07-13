package com.photosocialapp.domain.clustering

import android.graphics.Bitmap
import com.photosocialapp.utils.getSignature
import org.apache.commons.math3.ml.clustering.Clusterable
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer
import javax.inject.Inject
import kotlin.math.min

private const val NUM_CLUSTERS = 5
private const val MAX_ITERATIONS = 100

/**
 * A K-Means clustering implementation that uses the Apache Commons Math library
 * to group face embeddings.
 *
 * This class takes a list of face embeddings, clusters them using KMeans++,
 * and returns a map of cluster IDs to the unique faces belonging to that cluster.
 */
class KMeansClusterer @Inject constructor() {

    /**
     * A wrapper class to make our FloatArray embeddings compatible with the
     * Apache Commons Math clustering library.
     */
    private class EmbeddingWrapper(private val embedding: FloatArray, val originalBitmap: Bitmap) : Clusterable {
        override fun getPoint(): DoubleArray {
            return embedding.map { it.toDouble() }.toDoubleArray()
        }
    }

    /**
     * Performs K-Means clustering on the given face embeddings using Apache Commons Math.
     *
     * @param allFaceEmbeddings A list of all face embeddings to cluster.
     * @param faceData A map linking embeddings to their corresponding original face bitmaps.
     * @return A map where the key is the cluster ID (Int) and the value is a list of **unique**
     *         Bitmaps representing the faces in that cluster.
     */
    fun performClustering(
        allFaceEmbeddings: List<FloatArray>,
        faceData: Map<FloatArray, Bitmap>
    ): Map<Int, List<Bitmap>> {

        if (allFaceEmbeddings.isEmpty()) {
            return emptyMap()
        }

        // Convert our embeddings into the format required by the library.
        val clusterInput = allFaceEmbeddings.mapNotNull { embedding ->
            faceData[embedding]?.let { bitmap ->
                EmbeddingWrapper(embedding, bitmap)
            }
        }

        if (clusterInput.isEmpty()) {
            return emptyMap()
        }

        // Do not try to create more clusters than there are items.
        val numClusters = min(NUM_CLUSTERS, clusterInput.size)
        if (numClusters == 0) return emptyMap()

        // Initialize the clusterer from the library.
        val clusterer = KMeansPlusPlusClusterer<EmbeddingWrapper>(
            numClusters,
            MAX_ITERATIONS
        )

        // Perform the clustering.
        val clusterResults = clusterer.cluster(clusterInput)

        // Process the results into the desired output format.
        val resultMap = mutableMapOf<Int, MutableList<Bitmap>>()
        clusterResults.forEachIndexed { clusterId, cluster ->
            val faceBitmaps = cluster.points.map { wrapper -> wrapper.originalBitmap }
            // Ensure we only store unique faces for each cluster.
            resultMap[clusterId] = faceBitmaps.distinctBy { it.getSignature() }.toMutableList()
        }

        return resultMap.filterValues { it.isNotEmpty() }
    }
}
