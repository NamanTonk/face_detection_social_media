package com.photosocialapp.presentation.viewmodel

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photosocialapp.data.local.dao.FaceClusterDao
import com.photosocialapp.data.local.entity.Converters
import com.photosocialapp.domain.model.FaceClusterModel
import com.photosocialapp.domain.model.ImageModel
import com.photosocialapp.domain.usecase.GetImagesWithFacesUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ImagesUiState(
    val images: List<ImageModel> = emptyList(),
    val faceClusters: List<FaceClusterModel> = emptyList(),
    val isLoading: Boolean = false
)

class ImageViewModel(
    private val getImagesWithFacesUseCase: GetImagesWithFacesUseCase,
    private val faceClusterDao: FaceClusterDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImagesUiState(isLoading = true))
    val uiState: StateFlow<ImagesUiState> = _uiState.asStateFlow()

    fun fetchData(){
        loadImages()
        loadFaceClusters()
    }

    private fun loadImages() {
        getImagesWithFacesUseCase()
            .onStart { _uiState.update { it.copy(isLoading = true) } }
            .onEach { images ->
                _uiState.update { currentState ->
                    currentState.copy(
                        images = images,
                        isLoading = false
                    )
                }
            }
            .catch { error ->
                _uiState.update { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadFaceClusters() {
        faceClusterDao.getAllClusters()
            .map { clusters ->
                clusters.map { entity ->
                    FaceClusterModel(
                        clusterId = entity.clusterId,
                        faceImage = Converters().toBitmap(entity.faceImage)
                    )
                }
            }
            .onEach { faceClusters ->
                _uiState.update { currentState ->
                    currentState.copy(faceClusters = faceClusters)
                }
            }
            .launchIn(viewModelScope)
    }

    fun syncImage() {
        getImagesWithFacesUseCase.syncLocalImages().launchIn(viewModelScope)
    }
}
