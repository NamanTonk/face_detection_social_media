package com.photosocialapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.photosocialapp.data.repository.ImageRepositoryImpl
import com.photosocialapp.domain.usecase.GetImagesWithFacesUseCase
import com.photosocialapp.presentation.viewmodel.ImageViewModel
import com.photosocialapp.presentation.viewmodel.ImageViewModelFactory
import com.photosocialapp.ui.theme.PhotoSocialAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoSocialAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StrategicGrid(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StrategicGrid(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val viewModel: ImageViewModel = viewModel(
        factory = ImageViewModelFactory(
            GetImagesWithFacesUseCase(
                ImageRepositoryImpl(context)
            )
        )
    )

    val images by viewModel.images.collectAsStateWithLifecycle()

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(3),
        verticalItemSpacing = 4.dp,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(images) { image ->
            AsyncImage(
                model = image.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
