package com.photosocialapp.domain.model

import android.graphics.Bitmap

data class FaceClusterModel(
    val clusterId: Int,
    val faceImage: Bitmap
)
