package com.lumiformtest.form.data.local

import android.graphics.BitmapFactory
import java.io.File
import javax.inject.Inject

private const val VALIDATION_MAX_EDGE_PX = 256

internal fun interface ImageFileValidator {
    fun isValid(file: File): Boolean
}

internal class BitmapImageFileValidator @Inject constructor() : ImageFileValidator {
    override fun isValid(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        // Check a small decode before publishing the original, without allocating its full bitmap.
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > VALIDATION_MAX_EDGE_PX) inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return false
        bitmap.recycle()
        return true
    }
}
