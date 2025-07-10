# PhotoSocialApp

PhotoSocialApp is a modern Android application built to demonstrate a scalable and well-structured codebase using the latest Android development technologies. The app's core functionality is to scan the user's local image gallery, intelligently detect which photos contain human faces using on-device machine learning, and display these photos in a reactive UI.

## Features

- **Image Gallery Access**: Fetches images efficiently from the device's MediaStore.
- **On-Device Face Detection**: Integrates Google's ML Kit Vision API to accurately and privately detect human faces in images.
- **Local Caching Database**: Uses a Room database to cache face detection results. This prevents reprocessing the same images, ensuring fast load times and efficient resource use.
- **Reactive UI**: The entire user interface is built with Jetpack Compose, allowing it to react seamlessly to data changes from the underlying layers.
- **Background Synchronization**: A "sync" feature allows for the background processing of newly added or previously unchecked images, ensuring the collection is always up-to-date.

## Architecture & Tech Stack

This project follows the principles of **Clean Architecture**, separating concerns into distinct `data`, `domain`, and `presentation` layers. This promotes a modular, testable, and maintainable codebase.

- **Language**: 100% [Kotlin](https://kotlinlang.org/)
- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) for building the declarative user interface.
- **Architecture**: MVVM (Model-View-ViewModel) with Clean Architecture principles.
- **Asynchronous Programming**: [Kotlin Coroutines & Flow](https://kotlinlang.org/docs/coroutines-guide.html) for managing background threads and handling reactive data streams.
- **Database**: [Room](https://developer.android.com/jetpack/androidx/releases/room) for robust, local persistence of face detection data.
- **Machine Learning**: [Google ML Kit (Face Detection)](https://developers.google.com/ml-kit/vision/face-detection) for on-device ML capabilities.
- **Lifecycle Management**: AndroidX ViewModel and Lifecycle components to manage UI-related data in a lifecycle-conscious way.

### Project Structure

```
app/src/main/java/com/photosocialapp/
│
├── data/
│   ├── local/              # Room database components (DAO, Entity, Database)
│   └── repository/         # Implementation of the repository interfaces from the domain layer
│
├── domain/
│   ├── model/              # Core data models used throughout the app
│   ├── repository/         # Repository interfaces defining data access contracts
│   └── usecase/            # Business logic and interactors for specific features
│
└── presentation/
    ├── viewmodel/          # ViewModels that hold and manage UI-related data
    └── ui/                 # Jetpack Compose UI screens and components
```

## How to Build

1.  Clone the repository:
    ```bash
    git clone https://github.com/your-username/PhotoSocialApp.git
    ```
2.  Open the project in Android Studio.
3.  Build and run on an emulator or a physical device.

The app will require permission to access the device's storage to read photos.

---
*This README was generated with assistance from an AI tool.*
