package com.photosocialapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photosocialapp.domain.model.ImageModel
import com.photosocialapp.domain.usecase.GetImagesWithFacesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ImageViewModel(
    private val getImagesWithFacesUseCase: GetImagesWithFacesUseCase
) : ViewModel() {

    private val _images = MutableStateFlow<List<ImageModel>>(emptyList())
    val images: StateFlow<List<ImageModel>> = _images

    init {
        loadImages()
    }

    private fun loadImages() {
        getImagesWithFacesUseCase()
            .onEach { images ->
                _images.value = images
            }
            .launchIn(viewModelScope)
    }
}
