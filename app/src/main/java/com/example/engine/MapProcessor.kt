package com.example.engine

import android.graphics.Bitmap
import com.example.data.preferences.NormalFormat

/**
 * Interface abstracting PBR map processing algorithms.
 * Allows pure-Kotlin CPU implementation with coroutines or future GPU/RenderScript/Vulkan implementation.
 */
interface MapProcessor {

    /**
     * Resizes a grayscale float height/depth map using bicubic interpolation.
     */
    suspend fun bicubicUpscale(
        src: FloatArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): FloatArray

    /**
     * Extracts high-pass luminance detail from RGB source bitmap.
     */
    suspend fun extractHighPassLuminance(
        source: Bitmap,
        blurRadius: Int = 8
    ): FloatArray

    /**
     * Blends upscaled AI depth with high-pass filtered surface luminance.
     */
    suspend fun blendHeightMap(
        depth: FloatArray,
        highPass: FloatArray,
        detailBoost: Float,
        width: Int,
        height: Int
    ): FloatArray

    /**
     * Generates a fast, non-AI height map directly from multi-scale luminance frequency band-pass blurs.
     */
    suspend fun generateFastHeightMap(
        source: Bitmap,
        detailBoost: Float = 0.5f
    ): FloatArray

    /**
     * Generates a normal map from a height map using multi-scale gradients (fine, medium, coarse).
     */
    suspend fun generateNormalMap(
        heightMap: FloatArray,
        width: Int,
        height: Int,
        strength: Float = 1.0f,
        normalFormat: NormalFormat = NormalFormat.OPEN_GL,
        invertHeight: Boolean = false,
        blurScale: Float = 0.0f,
        scale1Weight: Float = 1.0f,
        scale2Weight: Float = 0.75f,
        scale3Weight: Float = 0.5f,
        scale4Weight: Float = 0.25f
    ): IntArray

    /**
     * Generates a Roughness map based on local contrast and luminance variance.
     */
    suspend fun generateRoughnessMap(
        source: Bitmap,
        baseRoughness: Float = 0.5f,
        contrast: Float = 1.0f
    ): FloatArray

    /**
     * Generates an Ambient Occlusion (AO) map using cavity detection on the height map.
     */
    suspend fun generateAoMap(
        heightMap: FloatArray,
        width: Int,
        height: Int,
        radius: Int = 12,
        intensity: Float = 1.5f
    ): FloatArray

    /**
     * Generates a Curvature map using the second derivative (discrete Laplacian) of the height map.
     */
    suspend fun generateCurvatureMap(
        heightMap: FloatArray,
        width: Int,
        height: Int,
        contrast: Float = 1.5f
    ): FloatArray

    /**
     * Generates a Metallic map via saturation and brightness thresholding (mostly black by default).
     */
    suspend fun generateMetallicMap(
        source: Bitmap,
        threshold: Float = 0.85f,
        boost: Float = 1.0f
    ): FloatArray

    /**
     * Generates an unlit Albedo map by de-lighting the source image using heavily blurred luminance.
     */
    suspend fun generateAlbedoMap(
        source: Bitmap,
        deLightStrength: Float = 0.7f
    ): IntArray

    /**
     * Generates a procedural fractal/Perlin noise map.
     */
    suspend fun generatePerlinNoiseMap(
        width: Int,
        height: Int,
        scale: Float = 8.0f,
        octaves: Int = 4,
        intensity: Float = 1.0f
    ): FloatArray

    /**
     * Optionally blends procedural noise into a height map.
     */
    suspend fun blendNoiseIntoHeight(
        heightMap: FloatArray,
        noiseMap: FloatArray,
        blendWeight: Float
    ): FloatArray

    /**
     * Converts a grayscale FloatArray [0.0, 1.0] into an ARGB_8888 Bitmap for display.
     */
    suspend fun floatMapToGrayscaleBitmap(
        map: FloatArray,
        width: Int,
        height: Int
    ): Bitmap

    /**
     * Converts an IntArray of ARGB pixels into an ARGB_8888 Bitmap.
     */
    suspend fun pixelArrayToBitmap(
        pixels: IntArray,
        width: Int,
        height: Int
    ): Bitmap

    /**
     * Synthesizes a seamless tileable Bitmap using cross-offset boundary blending.
     */
    suspend fun makeSeamlessBitmap(
        source: Bitmap,
        blendMarginRatio: Float = 0.20f
    ): Bitmap

    /**
     * Synthesizes a seamless tileable grayscale FloatArray map.
     */
    suspend fun makeSeamlessFloatMap(
        map: FloatArray,
        width: Int,
        height: Int,
        blendMarginRatio: Float = 0.20f
    ): FloatArray

    /**
     * Measures true seam discontinuity and continuity metrics between opposite borders.
     */
    suspend fun analyzeSeamContinuity(
        source: Bitmap
    ): SeamAnalysisResult

    /**
     * Applies a blemish exclusion mask to a height map, inpainting masked regions via
     * harmonic Dirichlet diffusion from boundary values to prevent deep cavity depth warping.
     */
    suspend fun applyExclusionMask(
        heightMap: FloatArray,
        maskBitmap: Bitmap?,
        width: Int,
        height: Int,
        iterations: Int = 24
    ): FloatArray

    /**
     * Inpaints masked areas of a height map using iterative harmonic relaxation.
     */
    suspend fun inpaintHeightMap(
        heightMap: FloatArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        iterations: Int = 24
    ): FloatArray

    /**
     * Creates a 2x2 repeat composite Bitmap for instant visual tiling inspection.
     */
    suspend fun generate2x2TiledBitmap(
        source: Bitmap
    ): Bitmap
}

/**
 * Real analytical metrics for texture seam discontinuity and boundary continuity.
 */
data class SeamAnalysisResult(
    val seamDiscontinuityScore: Float,
    val continuityPercent: Float,
    val horizontalSeamError: Float,
    val verticalSeamError: Float,
    val maxEdgeDiscontinuity: Float,
    val isSeamless: Boolean
)
