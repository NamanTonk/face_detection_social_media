package com.photosocialapp.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

class ImageFetchFromGallery(private val context: Context) {
    /**
     * Queries the MediaStore for the latest 50 images sorted by date added
     */
    operator fun invoke(page: Int = 0)  = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            ),

            Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Images.Media.DATE_ADDED)
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, 50)
                putInt(ContentResolver.QUERY_ARG_OFFSET, page * 50) // page = 0,1,2...
            },null
        )
    }else throw Exception("SDK version should be greater than 26")
}