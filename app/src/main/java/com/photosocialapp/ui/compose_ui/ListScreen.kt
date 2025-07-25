package com.photosocialapp.ui.compose_ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.photosocialapp.R
import com.photosocialapp.data.local.AppDatabase
import com.photosocialapp.data.repository.ImageRepositoryImpl
import com.photosocialapp.domain.usecase.FaceDetectorUseCase
import com.photosocialapp.domain.usecase.GetImagesWithFacesUseCase
import com.photosocialapp.domain.usecase.ImageFetchFromGallery
import com.photosocialapp.presentation.viewmodel.ImageViewModel
import com.photosocialapp.presentation.viewmodel.ImageViewModelFactory
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import com.photosocialapp.domain.usecase.FaceEmbeddingGenerator


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ListScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val permissionState = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )
    val viewModel: ImageViewModel = viewModel(
        factory = ImageViewModelFactory(
            GetImagesWithFacesUseCase(
                ImageRepositoryImpl(
                    ImageFetchFromGallery(context),
                    FaceDetectorUseCase(
                        context.contentResolver,
                        db.detectedImageDao(),
                        faceEmbeddingGenerator = FaceEmbeddingGenerator(db.faceClusterDao(),context.assets)
                    )
                )
            ),
            db.faceClusterDao()
        )
    )
    when (permissionState.status) {
        is PermissionStatus.Granted -> {
            LaunchedEffect(Unit) {viewModel.fetchData() }
            // Permission is granted, show the image grid
            Scaffold(topBar = {
                TopAppBar(title = {Text("Faces Images")}, // Or your desired title
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary, // Set background to primary color
                        titleContentColor = MaterialTheme.colorScheme.onPrimary, // Set title text color
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary // Set action icon color
                    ), actions = {Icon(modifier = Modifier.clickable{
                           viewModel.syncImage()
                    }, painter = painterResource(
                    R.drawable.sync_image), contentDescription = null)})
            }) { padding->
                ImageGridContent(Modifier.padding(padding),viewModel)}

        }
        is PermissionStatus.Denied -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val textToShow = if ((permissionState.status as PermissionStatus.Denied).shouldShowRationale) {
                    "The photos permission is required to show your images with faces. Please grant the permission."
                } else {
                    "Photos permission required. Please grant the permission from settings."
                }
                Text(
                    text = textToShow,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Request permission")
                }
            }
        }
    }
}


@Composable
private fun ImageGridContent(modifier: Modifier = Modifier, viewModel: ImageViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // Horizontal scrollable list of face clusters
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(uiState.faceClusters) { faceCluster ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(faceCluster.faceImage)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Face cluster ${faceCluster.clusterId}",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Existing grid content
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(3),
                    verticalItemSpacing = 4.dp,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(uiState.images) { index, image ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(image.uri)
                                .crossfade(true)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (index % 2 == 0) (200..300).random().dp else (100..180).random().dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}
