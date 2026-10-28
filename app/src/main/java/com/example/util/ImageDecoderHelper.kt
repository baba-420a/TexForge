package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max

object ImageDecoderHelper {

    /**
     * Creates a temporary file in cache/camera and returns a FileProvider URI for TakePicture contract.
     */
    fun createCameraImageUri(context: Context): Uri {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val tempFile = File.createTempFile("texforge_capture_", ".jpg", cameraDir)
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, tempFile)
    }

    /**
     * Memory-safe decode of an image Uri, correctly oriented via EXIF and downscaled so that
     * max(width, height) <= maxDimension.
     */
    fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        // Step 1: Decode bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val origWidth = options.outWidth
        val origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        // Step 2: Read EXIF orientation
        val exifOrientation = readExifOrientation(context, uri)

        // Step 3: Compute inSampleSize
        val sampleSize = ImageCropHelper.calculateInSampleSize(origWidth, origHeight, maxDimension)

        // Step 4: Decode with subsampling
        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // Step 5: Apply EXIF rotation/matrix
        val rotated = applyExifOrientation(decoded, exifOrientation)

        // Step 6: Exact downscale if longest side still exceeds maxDimension
        val maxSide = max(rotated.width, rotated.height)
        return if (maxSide > maxDimension) {
            val (targetW, targetH) = ImageCropHelper.calculateScaledDimensions(
                rotated.width,
                rotated.height,
                maxDimension
            )
            val scaled = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
            if (scaled != rotated && !rotated.isRecycled) {
                rotated.recycle()
            }
            scaled
        } else {
            rotated
        }
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }

        return try {
            val transformed = Bitmap.createBitmap(
                source, 0, 0, source.width, source.height, matrix, true
            )
            if (transformed != source && !source.isRecycled) {
                source.recycle()
            }
            transformed
        } catch (e: Exception) {
            source
        }
    }
}
