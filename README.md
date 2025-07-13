# PhotoSocialApp

An intelligent Android application that scans the user's photo gallery, detects faces, and groups similar faces together using machine learning.

## Features

- **Gallery Access**: Securely requests permissions to read images from the device's media store.
- **Face Detection**: Utilizes Google's ML Kit Vision API to accurately detect human faces in photos.
- **Face Embedding**: A TensorFlow Lite model processes each detected face to generate a unique numerical vector (embedding) that represents the face.
- **Face Clustering**: After generating embeddings for all detected faces, the app uses the powerful **K-Means++** algorithm from the `Apache Commons Math` library to analyze them. It groups the embeddings into distinct clusters, where each cluster represents a unique person. The final output is a list of all unique faces belonging to each cluster, effectively identifying and sorting every individual in the photo gallery.
- **Caching**: Uses a local Room database to cache face detection and processing results, preventing redundant work and speeding up subsequent launches.
- **Modern UI**: Built entirely with Jetpack Compose, providing a reactive and modern user interface.
- **Asynchronous Image Loading**: Efficiently loads and displays images using the Coil library.

## Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Asynchronous Operations**: Kotlin Coroutines & Flow
- **Database**: Room
- **Dependency Injection**: Manual (ViewModel Factories)
- **Machine Learning**:
  - **Google ML Kit**: Face Detection
  - **TensorFlow Lite**: Face Embedding Generation
  - **Apache Commons Math**: K-Means++ Clustering
- **Image Loading**: Coil

## How to Build and Run

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Let Gradle sync and download the required dependencies.
4.  Run the app on an Android emulator or a physical device.
