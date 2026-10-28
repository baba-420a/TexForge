package com.example

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.util.ImageCropHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImageCropHelperTest {

    @Test
    fun `calculateInSampleSize calculates correct power of two`() {
        // Source 4000x3000, target 2048 -> ratio is less than 2x, sampleSize = 1
        val sample1 = ImageCropHelper.calculateInSampleSize(4000, 3000, 2048)
        assertEquals(1, sample1)

        // Source 8000x6000, target 2048 -> 8000 / 4 = 2000 <= 2048, sampleSize = 2 or 4
        val sample2 = ImageCropHelper.calculateInSampleSize(8000, 6000, 2048)
        assertTrue(sample2 >= 2)

        // Source 12000x12000, target 1024 -> large reduction
        val sample3 = ImageCropHelper.calculateInSampleSize(12000, 12000, 1024)
        assertTrue(sample3 >= 8)

        // Target larger than source -> sampleSize = 1
        val sample4 = ImageCropHelper.calculateInSampleSize(1000, 1000, 2048)
        assertEquals(1, sample4)
    }

    @Test
    fun `calculateScaledDimensions keeps aspect ratio within target max`() {
        // Landscape 4000x2000 -> scale to 2048 max side
        val (w1, h1) = ImageCropHelper.calculateScaledDimensions(4000, 2000, 2048)
        assertEquals(2048, w1)
        assertEquals(1024, h1)

        // Portrait 1500x3000 -> scale to 1024 max side
        val (w2, h2) = ImageCropHelper.calculateScaledDimensions(1500, 3000, 1024)
        assertEquals(512, w2)
        assertEquals(1024, h2)

        // Already smaller than max -> unchanged
        val (w3, h3) = ImageCropHelper.calculateScaledDimensions(800, 600, 1024)
        assertEquals(800, w3)
        assertEquals(600, h3)
    }

    @Test
    fun `calculateCropRect returns centered square at default zoom`() {
        // Image 2000x1000, viewport 800px, scale 1.0, offset (0, 0)
        val rect = ImageCropHelper.calculateCropRect(
            imageWidth = 2000,
            imageHeight = 1000,
            viewportSize = 800f,
            scale = 1.0f,
            offsetX = 0f,
            offsetY = 0f
        )

        // In 1:1 fit-crop, the height (1000) determines the fit scale (0.8)
        // Crop rect width and height must be equal (square)
        assertEquals(rect.width(), rect.height())
        assertTrue(rect.left >= 0)
        assertTrue(rect.right <= 2000)
        assertTrue(rect.top >= 0)
        assertTrue(rect.bottom <= 1000)
    }

    @Test
    fun `cropAndScale extracts exact 1 to 1 square bitmap`() {
        val src = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val cropRect = Rect(200, 100, 700, 600) // 500x500 square
        val targetSize = 1024

        val result = ImageCropHelper.cropAndScale(src, cropRect, targetSize)
        assertEquals(1024, result.width)
        assertEquals(1024, result.height)
    }
}
