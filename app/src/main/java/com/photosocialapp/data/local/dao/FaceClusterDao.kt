package com.photosocialapp.data.local.dao

import androidx.room.*
import com.photosocialapp.data.local.entity.FaceClusterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceClusterDao {
    @Query("SELECT * FROM face_clusters ORDER BY clusterId, timestamp DESC")
    fun getAllClusters(): Flow<List<FaceClusterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFace(face: FaceClusterEntity)

    @Query("DELETE FROM face_clusters")
    suspend fun clearAllClusters()

    @Query("SELECT EXISTS(SELECT * FROM face_clusters WHERE clusterId = :clusterId)")
    suspend fun isClusterExists(clusterId: Int): Boolean
}
