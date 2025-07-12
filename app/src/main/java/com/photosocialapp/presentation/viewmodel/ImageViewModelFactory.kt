package com.photosocialapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.photosocialapp.domain.usecase.GetImagesWithFacesUseCase
import com.photosocialapp.data.local.dao.FaceClusterDao

class ImageViewModelFactory(
    private val getImagesWithFacesUseCase: GetImagesWithFacesUseCase,
    private val faceClusterDao: FaceClusterDao
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImageViewModel(getImagesWithFacesUseCase, faceClusterDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
