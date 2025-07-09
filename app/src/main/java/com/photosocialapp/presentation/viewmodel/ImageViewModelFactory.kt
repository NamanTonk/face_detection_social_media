package com.photosocialapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.photosocialapp.domain.usecase.GetImagesWithFacesUseCase

class ImageViewModelFactory(
    private val getImagesWithFacesUseCase: GetImagesWithFacesUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImageViewModel(getImagesWithFacesUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
