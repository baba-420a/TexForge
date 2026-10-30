package com.example

import android.graphics.Bitmap
import com.example.engine.CpuMapProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaskProcessorTest {

    private val processor = CpuMapProcessor()

    @Test
    fun inpaintHeightMap_eliminatesDeepHoleAndRestoresBoundaryHeight() = runBlocking {
        val width = 32
        val height = 32
        val total = width * height
        val heightMap = FloatArray(total) { 0.85f }
        val mask = BooleanArray(total)

        // Create a deep cavity hole (e.g., deep shadow or pit) in the center [12..19, 12..19]
        for (y in 12..19) {
            for (x in 12..19) {
                val idx = y * width + x
                heightMap[idx] = 0.02f // deep cavity
                mask[idx] = true       // masked blemish to exclude
            }
        }

        val inpainted = processor.inpaintHeightMap(heightMap, mask, width, height, iterations = 30)

        // Check that center pixel is no longer a deep cavity and has been smoothly harmonized
        val centerIdx = 15 * width + 15
        assertTrue("Inpainted center must be restored near boundary 0.85, was ${inpainted[centerIdx]}",
            inpainted[centerIdx] > 0.70f)

        // Verify unmasked pixels remain unchanged
        assertEquals(0.85f, inpainted[0], 0.001f)
        assertEquals(0.85f, inpainted[width * height - 1], 0.001f)

        // Verify no NaNs or Infinities
        for (i in 0 until total) {
            assertFalse("Value must not be NaN at $i", inpainted[i].isNaN())
            assertTrue("Value must be within [0, 1] at $i", inpainted[i] in 0.0f..1.0f)
        }
    }

    @Test
    fun inpaintHeightMap_smoothsGradientRampAcrossMaskedRegion() = runBlocking {
        val width = 30
        val height = 20
        val total = width * height
        val heightMap = FloatArray(total)
        val mask = BooleanArray(total)

        // Horizontal ramp from 0.1 to 0.9
        for (y in 0 until height) {
            for (x in 0 until width) {
                heightMap[y * width + x] = 0.1f + 0.8f * (x.toFloat() / (width - 1))
            }
        }

        // Mask a central band x in 12..16 with corrupt spikes (e.g. 0.0f or 1.0f)
        for (y in 5..15) {
            for (x in 12..16) {
                val idx = y * width + x
                heightMap[idx] = 0.0f
                mask[idx] = true
            }
        }

        val inpainted = processor.inpaintHeightMap(heightMap, mask, width, height, iterations = 32)

        // Verify monotonic gradient along the center row across the inpainted zone
        val y = 10
        for (x in 10 until 18) {
            val left = inpainted[y * width + x]
            val right = inpainted[y * width + x + 1]
            assertTrue("Expected gradient to monotonically increase: $left <= $right at x=$x", left <= right + 0.01f)
        }
    }

    @Test
    fun inpaintHeightMap_handlesEmptyOrNullMaskGracefully() = runBlocking {
        val width = 16
        val height = 16
        val heightMap = FloatArray(width * height) { 0.5f }
        val emptyMask = BooleanArray(width * height) { false }

        val result1 = processor.inpaintHeightMap(heightMap, emptyMask, width, height)
        assertEquals(0.5f, result1[0], 0.0001f)

        val result2 = processor.applyExclusionMask(heightMap, null, width, height)
        assertEquals(0.5f, result2[0], 0.0001f)
    }

    @Test
    fun applyExclusionMask_withBitmapMaskInpaintsRedStrokes() = runBlocking {
        val width = 24
        val height = 24
        val heightMap = FloatArray(width * height) { 0.75f }

        // Corrupt center with severe shadow void
        for (y in 8..14) {
            for (x in 8..14) {
                heightMap[y * width + x] = 0.05f
            }
        }

        // Create mask bitmap with red semi-transparent pixels #FF6B6B
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val redMaskColor = (0x73 shl 24) or (0xFF shl 16) or (0x6B shl 8) or 0x6B
        for (y in 8..14) {
            for (x in 8..14) {
                maskBitmap.setPixel(x, y, redMaskColor)
            }
        }

        val inpainted = processor.applyExclusionMask(heightMap, maskBitmap, width, height, iterations = 24)

        // Center pixel should be harmonized smoothly
        val centerVal = inpainted[11 * width + 11]
        assertTrue("Inpainted center should be > 0.60, was $centerVal", centerVal > 0.60f)
    }
}
