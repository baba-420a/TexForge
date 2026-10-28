package com.example.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure Kotlin functions for image crop math and resolution scaling.
 * Fully unit-testable without Android emulator or hardware.
 */
object ImageCropHelper {

    /**
     * Calculates the power-of-two inSampleSize to ensure decoded image dimensions
     * do not exceed target max dimension while conserving memory.
     */
    fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, maxDimension: Int): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || maxDimension <= 0) return 1
        var inSampleSize = 1
        val maxSrc = max(srcWidth, srcHeight)
        while ((maxSrc / (inSampleSize * 2)) >= maxDimension) {
            inSampleSize *= 2
        }
        return max(1, inSampleSize)
    }

    /**
     * Calculates target scaled dimensions keeping aspect ratio where max dimension <= maxDimension.
     */
    fun calculateScaledDimensions(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return Pair(1, 1)
        val maxSide = max(width, height)
        if (maxSide <= maxDimension) {
            return Pair(width, height)
        }
        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val scaledWidth = max(1, (width * scale).roundToInt())
        val scaledHeight = max(1, (height * scale).roundToInt())
        return Pair(scaledWidth, scaledHeight)
    }

    /**
     * Calculates the source bitmap crop rectangle from Compose pan and pinch-zoom coordinates.
     *
     * @param imageWidth Source bitmap width
     * @param imageHeight Source bitmap height
     * @param viewportSize Viewport square side length in pixels
     * @param scale Current zoom scale (>= 1.0)
     * @param offsetX Horizontal pan offset in pixels
     * @param offsetY Vertical pan offset in pixels
     * @return Source pixel Rect to crop into a 1:1 square
     */
    fun calculateCropRect(
        imageWidth: Int,
        imageHeight: Int,
        viewportSize: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Rect {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportSize <= 0f) {
            return Rect(0, 0, max(1, imageWidth), max(1, imageHeight))
        }

        // Base fit-scale to fill the square viewport
        val baseScale = max(viewportSize / imageWidth, viewportSize / imageHeight)
        val totalScale = max(baseScale * max(1f, scale), 0.0001f)

        // Viewport center in image coordinates
        val displayedWidth = imageWidth * totalScale
        val displayedHeight = imageHeight * totalScale

        // Center of viewport relative to displayed image top-left
        val vpCenterXInDisplayed = (viewportSize / 2f) - offsetX - ((viewportSize - displayedWidth) / 2f)
        val vpCenterYInDisplayed = (viewportSize / 2f) - offsetY - ((viewportSize - displayedHeight) / 2f)

        // Center in source bitmap pixels
        val srcCenterX = vpCenterXInDisplayed / totalScale
        val srcCenterY = vpCenterYInDisplayed / totalScale

        // Source crop square size
        val cropSideInSrc = viewportSize / totalScale

        val left = (srcCenterX - cropSideInSrc / 2f).roundToInt()
        val top = (srcCenterY - cropSideInSrc / 2f).roundToInt()
        val side = cropSideInSrc.roundToInt().coerceAtLeast(1)

        // Clamp to bitmap bounds
        val clampedLeft = left.coerceIn(0, max(0, imageWidth - side))
        val clampedTop = top.coerceIn(0, max(0, imageHeight - side))
        val clampedSide = min(side, min(imageWidth - clampedLeft, imageHeight - clampedTop)).coerceAtLeast(1)

        return Rect(clampedLeft, clampedTop, clampedLeft + clampedSide, clampedTop + clampedSide)
    }

    /**
     * Crops a 1:1 square region from source bitmap and scales to the desired target size (<= quality max).
     */
    fun cropAndScale(source: Bitmap, cropRect: Rect, targetSize: Int): Bitmap {
        val safeLeft = cropRect.left.coerceIn(0, source.width - 1)
        val safeTop = cropRect.top.coerceIn(0, source.height - 1)
        val safeWidth = cropRect.width().coerceIn(1, source.width - safeLeft)
        val safeHeight = cropRect.height().coerceIn(1, source.height - safeTop)

        val cropped = Bitmap.createBitmap(source, safeLeft, safeTop, safeWidth, safeHeight)
        val finalSize = if (targetSize > 0) targetSize else max(safeWidth, safeHeight)

        return if (cropped.width != finalSize || cropped.height != finalSize) {
            val scaled = Bitmap.createScaledBitmap(cropped, finalSize, finalSize, true)
            if (scaled != cropped && !cropped.isRecycled) {
                cropped.recycle()
            }
            scaled
        } else {
            cropped
        }
    }
}
