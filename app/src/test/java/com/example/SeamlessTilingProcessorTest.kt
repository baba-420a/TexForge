package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.engine.CpuMapProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SeamlessTilingProcessorTest {

    private lateinit var processor: CpuMapProcessor

    @Before
    fun setUp() {
        processor = CpuMapProcessor()
    }

    @Test
    fun `makeSeamlessBitmap dramatically reduces edge discontinuity on gradient image`() = runBlocking {
        val size = 64
        val rawBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // Create high edge disparity: left edge is dark (x=0), right edge is bright (x=size-1)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val lum = ((x.toFloat() / (size - 1)) * 255).toInt().coerceIn(0, 255)
                rawBitmap.setPixel(x, y, Color.rgb(lum, lum, lum))
            }
        }

        val rawAnalysis = processor.analyzeSeamContinuity(rawBitmap)
        // Raw gradient has huge disparity between x=0 (black) and x=63 (white)
        assertTrue(
            "Raw gradient discontinuity (${rawAnalysis.seamDiscontinuityScore}) should be high",
            rawAnalysis.seamDiscontinuityScore > 0.4f
        )

        // Apply seamless synthesis
        val seamlessBitmap = processor.makeSeamlessBitmap(rawBitmap, blendMarginRatio = 0.25f)
        val seamlessAnalysis = processor.analyzeSeamContinuity(seamlessBitmap)

        // Seam discontinuity must drop significantly
        assertTrue(
            "Seamless discontinuity (${seamlessAnalysis.seamDiscontinuityScore}) must be significantly lower than raw (${rawAnalysis.seamDiscontinuityScore})",
            seamlessAnalysis.seamDiscontinuityScore < rawAnalysis.seamDiscontinuityScore * 0.3f
        )
        assertTrue(
            "Seamless continuity percentage (${seamlessAnalysis.continuityPercent}%) must be high",
            seamlessAnalysis.continuityPercent >= 88.0f
        )
    }

    @Test
    fun `makeSeamlessFloatMap matches boundary opposite pixels`() = runBlocking {
        val size = 64
        val rawMap = FloatArray(size * size)

        // 2D linear gradient: f(x, y) = 0.5 * (x / 63) + 0.5 * (y / 63)
        for (y in 0 until size) {
            for (x in 0 until size) {
                rawMap[y * size + x] = 0.5f * (x.toFloat() / (size - 1)) + 0.5f * (y.toFloat() / (size - 1))
            }
        }

        val seamlessMap = processor.makeSeamlessFloatMap(rawMap, size, size, blendMarginRatio = 0.25f)

        // Check horizontal boundary: left column (x=0) vs right column (x=size-1)
        var maxHorizontalBoundaryDiff = 0f
        for (y in 0 until size) {
            val left = seamlessMap[y * size]
            val right = seamlessMap[y * size + (size - 1)]
            val diff = abs(left - right)
            if (diff > maxHorizontalBoundaryDiff) {
                maxHorizontalBoundaryDiff = diff
            }
        }

        // Check vertical boundary: top row (y=0) vs bottom row (y=size-1)
        var maxVerticalBoundaryDiff = 0f
        for (x in 0 until size) {
            val top = seamlessMap[x]
            val bottom = seamlessMap[(size - 1) * size + x]
            val diff = abs(top - bottom)
            if (diff > maxVerticalBoundaryDiff) {
                maxVerticalBoundaryDiff = diff
            }
        }

        // Boundaries must wrap smoothly with minimal delta
        assertTrue(
            "Max horizontal boundary delta ($maxHorizontalBoundaryDiff) should be small (< 0.15)",
            maxHorizontalBoundaryDiff < 0.15f
        )
        assertTrue(
            "Max vertical boundary delta ($maxVerticalBoundaryDiff) should be small (< 0.15)",
            maxVerticalBoundaryDiff < 0.15f
        )
    }

    @Test
    fun `generate2x2TiledBitmap doubles dimensions and tiles correctly`() = runBlocking {
        val size = 32
        val source = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        source.setPixel(size - 1, size - 1, Color.BLUE)

        val tiled = processor.generate2x2TiledBitmap(source)

        assertEquals("Width must be doubled", size * 2, tiled.width)
        assertEquals("Height must be doubled", size * 2, tiled.height)

        // Check 4 corners of all 4 quadrants
        assertEquals(Color.RED, tiled.getPixel(0, 0))
        assertEquals(Color.RED, tiled.getPixel(size, 0))
        assertEquals(Color.RED, tiled.getPixel(0, size))
        assertEquals(Color.RED, tiled.getPixel(size, size))

        assertEquals(Color.BLUE, tiled.getPixel(size - 1, size - 1))
        assertEquals(Color.BLUE, tiled.getPixel(size * 2 - 1, size - 1))
        assertEquals(Color.BLUE, tiled.getPixel(size - 1, size * 2 - 1))
        assertEquals(Color.BLUE, tiled.getPixel(size * 2 - 1, size * 2 - 1))
    }
}
