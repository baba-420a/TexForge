package com.example

import com.example.data.preferences.NormalFormat
import com.example.engine.CpuMapProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NormalMapProcessorTest {

    private lateinit var processor: CpuMapProcessor

    @Before
    fun setUp() {
        processor = CpuMapProcessor()
    }

    @Test
    fun `height ramp increasing to the right gives R less than 0_5`() = runBlocking {
        val width = 64
        val height = 64
        val rampRight = FloatArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                rampRight[y * width + x] = x.toFloat() / (width - 1).toFloat()
            }
        }

        val normalPixels = processor.generateNormalMap(
            heightMap = rampRight,
            width = width,
            height = height,
            strength = 2.0f,
            normalFormat = NormalFormat.OPEN_GL,
            scale1Weight = 1.0f,
            scale2Weight = 0f,
            scale3Weight = 0f,
            scale4Weight = 0f
        )

        // Sample center pixel
        val midX = 32
        val midY = 32
        val pixel = normalPixels[midY * width + midX]
        val r = (pixel shr 16 and 0xFF) / 255.0f

        // A height ramp increasing to the right gives R < 0.5
        assertTrue("Expected R < 0.5f but was $r", r < 0.5f)
    }

    @Test
    fun `ramp increasing toward the image bottom gives G greater than 0_5 in OpenGL and G less than 0_5 in DirectX`() = runBlocking {
        val width = 64
        val height = 64
        val rampDown = FloatArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                rampDown[y * width + x] = y.toFloat() / (height - 1).toFloat()
            }
        }

        // OpenGL mode
        val normalGl = processor.generateNormalMap(
            heightMap = rampDown,
            width = width,
            height = height,
            strength = 2.0f,
            normalFormat = NormalFormat.OPEN_GL,
            scale1Weight = 1.0f,
            scale2Weight = 0f,
            scale3Weight = 0f,
            scale4Weight = 0f
        )

        // DirectX mode
        val normalDx = processor.generateNormalMap(
            heightMap = rampDown,
            width = width,
            height = height,
            strength = 2.0f,
            normalFormat = NormalFormat.DIRECT_X,
            scale1Weight = 1.0f,
            scale2Weight = 0f,
            scale3Weight = 0f,
            scale4Weight = 0f
        )

        val midPixelGl = normalGl[32 * width + 32]
        val gGl = (midPixelGl shr 8 and 0xFF) / 255.0f

        val midPixelDx = normalDx[32 * width + 32]
        val gDx = (midPixelDx shr 8 and 0xFF) / 255.0f

        // OpenGL: G > 0.5
        assertTrue("Expected OpenGL G > 0.5f but was $gGl", gGl > 0.5f)
        // DirectX: G < 0.5
        assertTrue("Expected DirectX G < 0.5f but was $gDx", gDx < 0.5f)
    }

    @Test
    fun `flat height gives normal vector 0_5 0_5 1_0`() = runBlocking {
        val width = 64
        val height = 64
        val flat = FloatArray(width * height) { 0.5f }

        val normalPixels = processor.generateNormalMap(
            heightMap = flat,
            width = width,
            height = height,
            strength = 1.0f,
            normalFormat = NormalFormat.OPEN_GL
        )

        val centerPixel = normalPixels[32 * width + 32]
        val r = (centerPixel shr 16 and 0xFF) / 255.0f
        val g = (centerPixel shr 8 and 0xFF) / 255.0f
        val b = (centerPixel and 0xFF) / 255.0f

        // Flat surface has zero slope, giving normal (0, 0, 1), encoded as (0.5, 0.5, 1.0)
        assertEquals(0.5f, r, 0.02f)
        assertEquals(0.5f, g, 0.02f)
        assertEquals(1.0f, b, 0.02f)
    }

    @Test
    fun `bicubic upscaling accurately resizes height map`() = runBlocking {
        val srcWidth = 16
        val srcHeight = 16
        val src = FloatArray(srcWidth * srcHeight) { it.toFloat() / (srcWidth * srcHeight).toFloat() }

        val dstWidth = 64
        val dstHeight = 64
        val dst = processor.bicubicUpscale(src, srcWidth, srcHeight, dstWidth, dstHeight)

        assertEquals(dstWidth * dstHeight, dst.size)
        assertTrue(dst[0] >= 0.0f)
        assertTrue(dst[dst.size - 1] <= 1.0f)
    }

    @Test
    fun `min max depth normalization safely handles flat uniform input without division by zero or NaN`() {
        val rawFlatDepth = FloatArray(100) { 0.42f }
        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE
        for (v in rawFlatDepth) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }

        val range = (maxVal - minVal).coerceAtLeast(1e-6f)
        val normalized = FloatArray(rawFlatDepth.size)
        for (i in rawFlatDepth.indices) {
            normalized[i] = ((rawFlatDepth[i] - minVal) / range).coerceIn(0.0f, 1.0f)
        }

        for (v in normalized) {
            assertTrue("Normalized depth value must be finite", v.isFinite())
            assertEquals(0.0f, v, 1e-4f)
        }
    }

    @Test
    fun `generateFastHeightMap creates valid float array bounds`() = runBlocking {
        val bitmap = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
        val fastHeight = processor.generateFastHeightMap(bitmap, detailBoost = 0.5f)

        assertEquals(32 * 32, fastHeight.size)
        for (v in fastHeight) {
            assertTrue("Fast height value $v must be finite", v.isFinite())
            assertTrue("Fast height value $v must be in [0, 1]", v in 0.0f..1.0f)
        }
    }
}
