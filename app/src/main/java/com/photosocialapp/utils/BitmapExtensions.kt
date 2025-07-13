package com.photosocialapp.utils

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Converts a Bitmap into a unique byte array signature for content-based comparison.
 * This is useful for deduplication, as it allows comparing the actual pixel data
 * rather than just object references.
 *
 * @return A [List] of [Byte] representing the unique signature of the Bitmap's pixel data.
 */
fun Bitmap.getSignature(): List<Byte> {
    val buffer = ByteBuffer.allocate(this.byteCount)
    this.copyPixelsToBuffer(buffer)
    return buffer.array().toList()
}
