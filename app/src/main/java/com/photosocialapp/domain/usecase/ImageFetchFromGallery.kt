package com.photosocialapp.domain.usecase

import android.content.Context
import android.provider.MediaStore

class ImageFetchFromGallery(private val context: Context) {
    /**
     * Queries the MediaStore for images sorted by date added
     */
    operator fun invoke() = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        ),
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )
}