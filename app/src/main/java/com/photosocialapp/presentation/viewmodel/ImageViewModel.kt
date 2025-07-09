package com.photosocialapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photosocialapp.domain.model.ImageModel
import com.photosocialapp.domain.usecase.GetImagesWithFacesUseCase
import kotlinx.coroutines.flow.*

data class ImagesUiState(
    val images: List<ImageModel> = emptyList(),
    val isLoading: Boolean = false
)

class ImageViewModel(
    private val getImagesWithFacesUseCase: GetImagesWithFacesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImagesUiState(isLoading = true))
    val uiState: StateFlow<ImagesUiState> = _uiState.asStateFlow()

    init {
        loadImages()
    }

    private fun loadImages() {
        getImagesWithFacesUseCase()
            .onStart { _uiState.value = ImagesUiState(isLoading = true) }
            .onEach { images ->
                _uiState.value = ImagesUiState(
                    images = images,
                    isLoading = false
                )
            }
            .catch { error ->
                _uiState.value = ImagesUiState(isLoading = false)
            }
            .launchIn(viewModelScope)
    }
}
