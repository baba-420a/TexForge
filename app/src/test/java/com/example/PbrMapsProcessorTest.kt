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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PbrMapsProcessorTest {

    private lateinit var processor: CpuMapProcessor

    @Before
    fun setUp() {
        processor = CpuMapProcessor()
    }

    @Test
    fun `roughness map reflects local variance`() = runBlocking {
        // Uniform bitmap: 0 variance
        val uniform = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val canvas1 = android.graphics.Canvas(uniform)
        canvas1.drawColor(Color.GRAY)

        val roughUniform = processor.generateRoughnessMap(uniform, baseRoughness = 0.5f, contrast = 1.0f)
        val midUniform = roughUniform[16 * 32 + 16]

        // Checkerboard noisy bitmap: high local variance
        val noisy = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                val color = if ((x + y) % 2 == 0) Color.BLACK else Color.WHITE
                noisy.setPixel(x, y, color)
            }
        }

        val roughNoisy = processor.generateRoughnessMap(noisy, baseRoughness = 0.5f, contrast = 1.0f)
        val midNoisy = roughNoisy[16 * 32 + 16]

        // Noisy texture must produce higher roughness than flat uniform surface
        assertTrue("Noisy roughness ($midNoisy) should be greater than uniform ($midUniform)", midNoisy > midUniform)
    }

    @Test
    fun `ambient occlusion detects cavities and leaves flat surfaces white`() = runBlocking {
        val size = 32
        val flat = FloatArray(size * size) { 0.5f }

        val aoFlat = processor.generateAoMap(flat, size, size, radius = 8, intensity = 1.5f)
        // Flat surface has zero occlusion, giving AO = 1.0
        assertEquals(1.0f, aoFlat[16 * size + 16], 0.05f)

        // Height map with deep crevice in center
        val crevice = FloatArray(size * size) { 0.8f }
        for (dy in -2..2) {
            for (dx in -2..2) {
                crevice[(16 + dy) * size + (16 + dx)] = 0.0f
            }
        }
        val aoCrevice = processor.generateAoMap(crevice, size, size, radius = 8, intensity = 2.0f)

        // Center pixel should have significant occlusion (AO < 1.0)
        assertTrue("Crevice center AO should be occluded (< 1.0) but was ${aoCrevice[16 * size + 16]}",
            aoCrevice[16 * size + 16] < 0.95f)
    }

    @Test
    fun `curvature map gives 0_5 for flat, greater for peaks, less for valleys`() = runBlocking {
        val size = 32
        val flat = FloatArray(size * size) { 0.5f }
        val curFlat = processor.generateCurvatureMap(flat, size, size, contrast = 1.0f)

        // Flat surface has zero Laplacian, giving 0.5
        assertEquals(0.5f, curFlat[16 * size + 16], 0.02f)

        // Peak at center
        val peak = FloatArray(size * size) { 0.2f }
        peak[16 * size + 16] = 0.9f
        val curPeak = processor.generateCurvatureMap(peak, size, size, contrast = 1.0f)

        // Convex peak has negative Laplacian -> curvature > 0.5
        assertTrue("Peak curvature should be > 0.5 but was ${curPeak[16 * size + 16]}", curPeak[16 * size + 16] > 0.5f)

        // Valley at center
        val valley = FloatArray(size * size) { 0.8f }
        valley[16 * size + 16] = 0.1f
        val curValley = processor.generateCurvatureMap(valley, size, size, contrast = 1.0f)

        // Concave valley has positive Laplacian -> curvature < 0.5
        assertTrue("Valley curvature should be < 0.5 but was ${curValley[16 * size + 16]}", curValley[16 * size + 16] < 0.5f)
    }

    @Test
    fun `metallic map is mostly black for dielectrics and isolates bright highlights`() = runBlocking {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        // Set mostly diffuse wood/stone color (RGB: 120, 80, 50 -> dark/mid)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.rgb(120, 80, 50))
        // Add a specular metal highlight (RGB: 250, 250, 250)
        bitmap.setPixel(16, 16, Color.rgb(250, 250, 250))

        val metallic = processor.generateMetallicMap(bitmap, threshold = 0.85f, boost = 1.0f)

        // Diffuse area must be zero (mostly black)
        assertEquals(0.0f, metallic[0], 0.001f)
        // Specular highlight must be metallic > 0
        assertTrue("Specular pixel should be metallic (> 0.0) but was ${metallic[16 * 32 + 16]}",
            metallic[16 * 32 + 16] > 0.2f)
    }

    @Test
    fun `procedural perlin noise produces bounded continuous values`() = runBlocking {
        val size = 32
        val noise = processor.generatePerlinNoiseMap(size, size, scale = 8.0f, octaves = 3, intensity = 1.0f)

        assertEquals(size * size, noise.size)
        for (v in noise) {
            assertTrue("Noise value $v out of [0, 1] range", v in 0.0f..1.0f)
        }
    }
}
