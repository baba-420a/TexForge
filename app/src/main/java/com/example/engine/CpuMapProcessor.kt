package com.example.engine

import android.graphics.Bitmap
import com.example.data.preferences.NormalFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CpuMapProcessor : MapProcessor {

    override suspend fun bicubicUpscale(
        src: FloatArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): FloatArray = withContext(Dispatchers.Default) {
        if (srcWidth == dstWidth && srcHeight == dstHeight) {
            return@withContext src.copyOf()
        }

        val dst = FloatArray(dstWidth * dstHeight)
        val xRatio = srcWidth.toFloat() / dstWidth.toFloat()
        val yRatio = srcHeight.toFloat() / dstHeight.toFloat()

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerChunk = (dstHeight + numCores - 1) / numCores

        coroutineScope {
            (0 until numCores).map { chunkIndex ->
                async {
                    val startY = chunkIndex * rowsPerChunk
                    val endY = min(dstHeight, startY + rowsPerChunk)
                    val p = FloatArray(4)

                    for (y in startY until endY) {
                        val srcY = (y + 0.5f) * yRatio - 0.5f
                        val yFloor = floor(srcY).toInt()
                        val yFrac = srcY - yFloor

                        val dstRowOffset = y * dstWidth

                        for (x in 0 until dstWidth) {
                            val srcX = (x + 0.5f) * xRatio - 0.5f
                            val xFloor = floor(srcX).toInt()
                            val xFrac = srcX - xFloor

                            for (m in -1..2) {
                                val sampleY = (yFloor + m).coerceIn(0, srcHeight - 1)
                                val srcRowOffset = sampleY * srcWidth

                                val v0 = src[srcRowOffset + (xFloor - 1).coerceIn(0, srcWidth - 1)]
                                val v1 = src[srcRowOffset + xFloor.coerceIn(0, srcWidth - 1)]
                                val v2 = src[srcRowOffset + (xFloor + 1).coerceIn(0, srcWidth - 1)]
                                val v3 = src[srcRowOffset + (xFloor + 2).coerceIn(0, srcWidth - 1)]

                                p[m + 1] = cubicHermite(v0, v1, v2, v3, xFrac)
                            }

                            val interpolated = cubicHermite(p[0], p[1], p[2], p[3], yFrac)
                            dst[dstRowOffset + x] = interpolated.coerceIn(0.0f, 1.0f)
                        }
                    }
                }
            }.awaitAll()
        }

        dst
    }

    private fun cubicHermite(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val c0 = b
        val c1 = 0.5f * (c - a)
        val c2 = a - 2.5f * b + 2.0f * c - 0.5f * d
        val c3 = 0.5f * (d - a) + 1.5f * (b - c)
        return ((c3 * t + c2) * t + c1) * t + c0
    }

    override suspend fun extractHighPassLuminance(
        source: Bitmap,
        blurRadius: Int
    ): FloatArray = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val lum = FloatArray(totalPixels)
        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255.0f
            val g = (p shr 8 and 0xFF) / 255.0f
            val b = (p and 0xFF) / 255.0f
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val blurred = fastBoxBlurGrayscale(lum, width, height, blurRadius.coerceAtLeast(2))

        val highPass = FloatArray(totalPixels)
        for (i in 0 until totalPixels) {
            highPass[i] = lum[i] - blurred[i]
        }
        highPass
    }

    override suspend fun blendHeightMap(
        depth: FloatArray,
        highPass: FloatArray,
        detailBoost: Float,
        width: Int,
        height: Int
    ): FloatArray = withContext(Dispatchers.Default) {
        val total = width * height
        val result = FloatArray(total)
        val boost = detailBoost.coerceIn(0.0f, 1.0f)

        for (i in 0 until total) {
            val d = depth[i]
            val hp = highPass[i]
            val blended = d + hp * boost * 0.5f
            result[i] = blended.coerceIn(0.0f, 1.0f)
        }
        result
    }

    override suspend fun generateFastHeightMap(
        source: Bitmap,
        detailBoost: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val lum = FloatArray(total)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255.0f
            val g = (p shr 8 and 0xFF) / 255.0f
            val b = (p and 0xFF) / 255.0f
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Multi-scale band-pass blurs for Fast (No AI) mode
        val coarseBlur = fastBoxBlurGrayscale(lum, width, height, radius = 24)
        val fineBlur = fastBoxBlurGrayscale(lum, width, height, radius = 4)

        val boost = detailBoost.coerceIn(0.0f, 2.0f)
        val rawHeight = FloatArray(total)

        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE

        for (i in 0 until total) {
            val macro = coarseBlur[i]
            val detail = lum[i] - fineBlur[i]
            val h = macro + detail * boost * 0.75f
            rawHeight[i] = h
            if (h < minVal) minVal = h
            if (h > maxVal) maxVal = h
        }

        val range = (maxVal - minVal).coerceAtLeast(1e-6f)
        val result = FloatArray(total)
        for (i in 0 until total) {
            result[i] = ((rawHeight[i] - minVal) / range).coerceIn(0.0f, 1.0f)
        }
        result
    }

    override suspend fun generateNormalMap(
        heightMap: FloatArray,
        width: Int,
        height: Int,
        strength: Float,
        normalFormat: NormalFormat,
        invertHeight: Boolean,
        blurScale: Float,
        scale1Weight: Float,
        scale2Weight: Float,
        scale3Weight: Float,
        scale4Weight: Float
    ): IntArray = withContext(Dispatchers.Default) {
        val totalPixels = width * height
        val normalPixels = IntArray(totalPixels)

        val effectiveHeight = if (invertHeight) {
            FloatArray(totalPixels) { 1.0f - heightMap[it] }
        } else {
            heightMap
        }

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerChunk = (height + numCores - 1) / numCores

        coroutineScope {
            (0 until numCores).map { chunkIndex ->
                async {
                    val startY = chunkIndex * rowsPerChunk
                    val endY = min(height, startY + rowsPerChunk)

                    for (y in startY until endY) {
                        val rowOffset = y * width

                        for (x in 0 until width) {
                            var totalDx = 0.0f
                            var totalDy = 0.0f

                            if (scale1Weight > 0.0f) {
                                val (dx, dy) = computeScharrGradient(effectiveHeight, width, height, x, y, step = 1)
                                totalDx += dx * scale1Weight
                                totalDy += dy * scale1Weight
                            }
                            if (scale2Weight > 0.0f) {
                                val (dx, dy) = computeScharrGradient(effectiveHeight, width, height, x, y, step = 2)
                                totalDx += dx * scale2Weight
                                totalDy += dy * scale2Weight
                            }
                            if (scale3Weight > 0.0f) {
                                val (dx, dy) = computeScharrGradient(effectiveHeight, width, height, x, y, step = 4)
                                totalDx += dx * scale3Weight
                                totalDy += dy * scale3Weight
                            }
                            if (scale4Weight > 0.0f) {
                                val (dx, dy) = computeScharrGradient(effectiveHeight, width, height, x, y, step = 8)
                                totalDx += dx * scale4Weight
                                totalDy += dy * scale4Weight
                            }

                            val nx = -totalDx * strength
                            val ny = totalDy * strength
                            val nz = 1.0f

                            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
                            val normX = nx / len
                            val normY = ny / len
                            val normZ = nz / len

                            val r = ((normX * 0.5f + 0.5f) * 255.0f).roundToInt().coerceIn(0, 255)
                            val greenVal = if (normalFormat == NormalFormat.OPEN_GL) normY else -normY
                            val g = ((greenVal * 0.5f + 0.5f) * 255.0f).roundToInt().coerceIn(0, 255)
                            val b = ((normZ * 0.5f + 0.5f) * 255.0f).roundToInt().coerceIn(0, 255)

                            normalPixels[rowOffset + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                }
            }.awaitAll()
        }

        normalPixels
    }

    override suspend fun generateRoughnessMap(
        source: Bitmap,
        baseRoughness: Float,
        contrast: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val lum = FloatArray(total)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Local variance computation using radius = 3
        val roughness = FloatArray(total)
        val radius = 3
        val windowArea = (2 * radius + 1) * (2 * radius + 1)

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerChunk = (height + numCores - 1) / numCores

        coroutineScope {
            (0 until numCores).map { chunkIndex ->
                async {
                    val startY = chunkIndex * rowsPerChunk
                    val endY = min(height, startY + rowsPerChunk)

                    for (y in startY until endY) {
                        val rowOffset = y * width
                        for (x in 0 until width) {
                            var sum = 0f
                            var sumSq = 0f

                            for (dy in -radius..radius) {
                                val sy = (y + dy).coerceIn(0, height - 1) * width
                                for (dx in -radius..radius) {
                                    val sx = (x + dx).coerceIn(0, width - 1)
                                    val v = lum[sy + sx]
                                    sum += v
                                    sumSq += v * v
                                }
                            }

                            val mean = sum / windowArea
                            val variance = max(0f, (sumSq / windowArea) - (mean * mean))
                            val stdDev = sqrt(variance)

                            // Roughness: base + stdDev scaled by contrast
                            val valNorm = baseRoughness + (stdDev * 3.5f - 0.2f) * contrast
                            roughness[rowOffset + x] = valNorm.coerceIn(0.0f, 1.0f)
                        }
                    }
                }
            }.awaitAll()
        }

        roughness
    }

    override suspend fun generateAoMap(
        heightMap: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        intensity: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val total = width * height
        val rawAo = FloatArray(total)
        val effectiveRadius = radius.coerceIn(2, 32)

        // Radial sampling offsets (8 directions, 3 sample distances)
        val sampleDirections = arrayOf(
            Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
            Pair(1, 1), Pair(-1, -1), Pair(1, -1), Pair(-1, 1)
        )
        val distances = intArrayOf(
            max(1, effectiveRadius / 4),
            max(2, effectiveRadius / 2),
            effectiveRadius
        )

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerChunk = (height + numCores - 1) / numCores

        coroutineScope {
            (0 until numCores).map { chunkIndex ->
                async {
                    val startY = chunkIndex * rowsPerChunk
                    val endY = min(height, startY + rowsPerChunk)

                    for (y in startY until endY) {
                        val rowOffset = y * width
                        for (x in 0 until width) {
                            val hCenter = heightMap[rowOffset + x]
                            var totalOcclusion = 0.0f

                            for (dist in distances) {
                                for (dir in sampleDirections) {
                                    val nx = (x + dir.first * dist).coerceIn(0, width - 1)
                                    val ny = (y + dir.second * dist).coerceIn(0, height - 1)
                                    val neighborH = heightMap[ny * width + nx]
                                    val diff = neighborH - hCenter
                                    if (diff > 0.0f) {
                                        totalOcclusion += diff / dist
                                    }
                                }
                            }

                            // Cavity occlusion to AO [0.0 (dark) .. 1.0 (exposed)]
                            val normalizedOcc = (totalOcclusion / (distances.size * sampleDirections.size)) * intensity * 4.0f
                            rawAo[rowOffset + x] = (1.0f - normalizedOcc).coerceIn(0.0f, 1.0f)
                        }
                    }
                }
            }.awaitAll()
        }

        // Smooth AO slightly with fast box blur
        fastBoxBlurGrayscale(rawAo, width, height, radius = 2)
    }

    override suspend fun generateCurvatureMap(
        heightMap: FloatArray,
        width: Int,
        height: Int,
        contrast: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val total = width * height
        val curvature = FloatArray(total)

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerChunk = (height + numCores - 1) / numCores

        coroutineScope {
            (0 until numCores).map { chunkIndex ->
                async {
                    val startY = chunkIndex * rowsPerChunk
                    val endY = min(height, startY + rowsPerChunk)

                    for (y in startY until endY) {
                        val rowMid = y * width
                        val rowUp = (y - 1).coerceIn(0, height - 1) * width
                        val rowDown = (y + 1).coerceIn(0, height - 1) * width

                        for (x in 0 until width) {
                            val xLeft = (x - 1).coerceIn(0, width - 1)
                            val xRight = (x + 1).coerceIn(0, width - 1)

                            val center = heightMap[rowMid + x]
                            val left = heightMap[rowMid + xLeft]
                            val right = heightMap[rowMid + xRight]
                            val up = heightMap[rowUp + x]
                            val down = heightMap[rowDown + x]

                            // Discrete Laplacian operator: d^2h/dx^2 + d^2h/dy^2
                            val laplacian = (left + right + up + down) - 4.0f * center

                            // Convex (peaks) -> laplacian < 0 -> brighter (> 0.5)
                            // Concave (grooves) -> laplacian > 0 -> darker (< 0.5)
                            // Flat -> laplacian = 0 -> 0.5
                            val curved = 0.5f - laplacian * 4.0f * contrast
                            curvature[rowMid + x] = curved.coerceIn(0.0f, 1.0f)
                        }
                    }
                }
            }.awaitAll()
        }

        curvature
    }

    override suspend fun generateMetallicMap(
        source: Bitmap,
        threshold: Float,
        boost: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val metallic = FloatArray(total)
        val thresh = threshold.coerceIn(0.1f, 1.0f)

        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val saturation = if (maxC > 0f) (maxC - minC) / maxC else 0f
            val brightness = maxC

            // Metals are either highly reflective specular (high brightness) or have specific metallic reflection
            // Dielectrics have 0 metallic (mostly black by default)
            if (brightness > thresh) {
                val excess = (brightness - thresh) / (1.0f - thresh).coerceAtLeast(1e-4f)
                val satFactor = (1.0f - saturation * 0.6f).coerceIn(0.2f, 1.0f)
                val m = excess * satFactor * boost
                metallic[i] = m.coerceIn(0.0f, 1.0f)
            } else {
                metallic[i] = 0.0f
            }
        }

        metallic
    }

    override suspend fun generateAlbedoMap(
        source: Bitmap,
        deLightStrength: Float
    ): IntArray = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val lum = FloatArray(total)
        var totalLum = 0.0
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            lum[i] = l
            totalLum += l
        }
        val avgLum = (totalLum / total).toFloat().coerceIn(0.2f, 0.8f)

        // Heavily blurred luminance to capture baked lighting gradients (radius = 32)
        val blurredLum = fastBoxBlurGrayscale(lum, width, height, radius = 32)

        val albedoPixels = IntArray(total)
        val strength = deLightStrength.coerceIn(0.0f, 1.0f)

        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF)
            val g = (p shr 8 and 0xFF)
            val b = (p and 0xFF)

            val ambient = blurredLum[i] + 0.08f
            val correctionRatio = (avgLum / ambient).coerceIn(0.4f, 2.2f)
            val blendedFactor = 1.0f + (correctionRatio - 1.0f) * strength

            val newR = (r * blendedFactor).roundToInt().coerceIn(0, 255)
            val newG = (g * blendedFactor).roundToInt().coerceIn(0, 255)
            val newB = (b * blendedFactor).roundToInt().coerceIn(0, 255)

            albedoPixels[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        albedoPixels
    }

    override suspend fun generatePerlinNoiseMap(
        width: Int,
        height: Int,
        scale: Float,
        octaves: Int,
        intensity: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val total = width * height
        val noise = FloatArray(total)
        val baseFreq = scale.coerceIn(1.0f, 64.0f) / width.toFloat()
        val numOctaves = octaves.coerceIn(1, 6)

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerChunk = (height + numCores - 1) / numCores

        coroutineScope {
            (0 until numCores).map { chunkIndex ->
                async {
                    val startY = chunkIndex * rowsPerChunk
                    val endY = min(height, startY + rowsPerChunk)

                    for (y in startY until endY) {
                        val rowOffset = y * width
                        for (x in 0 until width) {
                            var value = 0.0f
                            var freq = baseFreq
                            var amp = 1.0f
                            var maxAmp = 0.0f

                            for (o in 0 until numOctaves) {
                                value += perlin2D(x * freq, y * freq) * amp
                                maxAmp += amp
                                amp *= 0.5f
                                freq *= 2.0f
                            }

                            // Normalize to [0.0, 1.0] and apply intensity
                            val normalized = (value / maxAmp) * 0.5f + 0.5f
                            val shaped = 0.5f + (normalized - 0.5f) * intensity
                            noise[rowOffset + x] = shaped.coerceIn(0.0f, 1.0f)
                        }
                    }
                }
            }.awaitAll()
        }

        noise
    }

    override suspend fun blendNoiseIntoHeight(
        heightMap: FloatArray,
        noiseMap: FloatArray,
        blendWeight: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val total = heightMap.size
        val result = FloatArray(total)
        val weight = blendWeight.coerceIn(0.0f, 1.0f)

        for (i in 0 until total) {
            val h = heightMap[i]
            val n = noiseMap[i]
            val perturbed = h + (n - 0.5f) * weight * 0.35f
            result[i] = perturbed.coerceIn(0.0f, 1.0f)
        }
        result
    }

    private fun perlin2D(x: Float, y: Float): Float {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)

        val u = fade(xf)
        val v = fade(yf)

        val aa = p[p[xi] + yi]
        val ab = p[p[xi] + yi + 1]
        val ba = p[p[xi + 1] + yi]
        val bb = p[p[xi + 1] + yi + 1]

        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1f, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1f), grad(bb, xf - 1f, yf - 1f), u)
        return lerp(x1, x2, v)
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
    private fun grad(hash: Int, x: Float, y: Float): Float {
        return when (hash and 3) {
            0 -> x + y
            1 -> -x + y
            2 -> x - y
            else -> -x - y
        }
    }

    // Permutation table for Perlin noise
    private val p: IntArray = run {
        val permutation = intArrayOf(
            151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,
            8,99,37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,
            35,11,32,57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,74,165,71,
            134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,
            55,46,245,40,244,102,143,54,65,25,63,161,1,216,80,73,209,76,132,187,208,89,
            18,169,200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,52,217,226,
            250,124,123,5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,
            189,28,42,223,183,170,213,119,248,152,2,44,154,163,70,221,153,101,155,167,43,
            172,9,129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,218,246,97,
            228,251,34,242,193,238,210,144,12,191,179,162,241,81,51,145,235,249,14,239,
            107,49,192,214,31,181,199,106,157,184,84,204,176,115,121,50,45,127,4,150,254,
            138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
        )
        val doubleP = IntArray(512)
        for (i in 0 until 256) {
            doubleP[i] = permutation[i]
            doubleP[256 + i] = permutation[i]
        }
        doubleP
    }

    private fun computeScharrGradient(
        h: FloatArray,
        w: Int,
        height: Int,
        x: Int,
        y: Int,
        step: Int
    ): Pair<Float, Float> {
        val x0 = (x - step).coerceIn(0, w - 1)
        val x1 = (x + step).coerceIn(0, w - 1)
        val y0 = (y - step).coerceIn(0, height - 1)
        val y1 = (y + step).coerceIn(0, height - 1)

        val row0 = y0 * w
        val rowMid = y * w
        val row1 = y1 * w

        val p00 = h[row0 + x0]; val p02 = h[row0 + x1]
        val p10 = h[rowMid + x0]; val p12 = h[rowMid + x1]
        val p20 = h[row1 + x0]; val p22 = h[row1 + x1]
        val p01 = h[row0 + x]
        val p21 = h[row1 + x]

        val dx = (3f * (p02 - p00) + 10f * (p12 - p10) + 3f * (p22 - p20)) / 32f
        val dy = (3f * (p20 - p00) + 10f * (p21 - p01) + 3f * (p22 - p02)) / 32f

        return Pair(dx, dy)
    }

    private fun fastBoxBlurGrayscale(
        src: FloatArray,
        width: Int,
        height: Int,
        radius: Int
    ): FloatArray {
        val total = width * height
        val temp = FloatArray(total)
        val dst = FloatArray(total)
        val r = radius.coerceAtLeast(1)

        // Horizontal pass
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var sum = 0.0f
                val minX = max(0, x - r)
                val maxX = min(width - 1, x + r)
                for (k in minX..maxX) {
                    sum += src[row + k]
                }
                temp[row + x] = sum / (maxX - minX + 1)
            }
        }

        // Vertical pass
        for (x in 0 until width) {
            for (y in 0 until height) {
                var sum = 0.0f
                val minY = max(0, y - r)
                val maxY = min(height - 1, y + r)
                for (k in minY..maxY) {
                    sum += temp[k * width + x]
                }
                dst[y * width + x] = sum / (maxY - minY + 1)
            }
        }

        return dst
    }

    override suspend fun floatMapToGrayscaleBitmap(
        map: FloatArray,
        width: Int,
        height: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val total = width * height
        val pixels = IntArray(total)

        for (i in 0 until total) {
            val v = (map[i].coerceIn(0.0f, 1.0f) * 255.0f).roundToInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    }

    override suspend fun pixelArrayToBitmap(
        pixels: IntArray,
        width: Int,
        height: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    }

    override suspend fun makeSeamlessBitmap(
        source: Bitmap,
        blendMarginRatio: Float
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val r = FloatArray(total)
        val g = FloatArray(total)
        val b = FloatArray(total)
        val a = IntArray(total)

        for (i in 0 until total) {
            val p = pixels[i]
            a[i] = (p ushr 24) and 0xFF
            r[i] = ((p ushr 16) and 0xFF).toFloat()
            g[i] = ((p ushr 8) and 0xFF).toFloat()
            b[i] = (p and 0xFF).toFloat()
        }

        val marginX = (width * blendMarginRatio.coerceIn(0.10f, 0.50f)).roundToInt().coerceIn(4, width / 2)
        val marginY = (height * blendMarginRatio.coerceIn(0.10f, 0.50f)).roundToInt().coerceIn(4, height / 2)

        // Pass 1: Horizontal boundary reconciliation across left & right margins
        for (y in 0 until height) {
            val rowOffset = y * width
            val rightIdx = rowOffset + width - 1
            val leftIdx = rowOffset
            val deltaR = r[rightIdx] - r[leftIdx]
            val deltaG = g[rightIdx] - g[leftIdx]
            val deltaB = b[rightIdx] - b[leftIdx]

            for (x in 0 until marginX) {
                val t = 0.5f * (1.0f - Math.cos(Math.PI * x / (2.0 * marginX))).toFloat()
                val factor = 0.5f - t
                val idx = rowOffset + x
                r[idx] += factor * deltaR
                g[idx] += factor * deltaG
                b[idx] += factor * deltaB
            }
            for (x in 0 until marginX) {
                val xR = width - marginX + x
                val t = 0.5f * (1.0f - Math.cos(Math.PI * (x + marginX) / (2.0 * marginX))).toFloat()
                val factor = t - 0.5f
                val idx = rowOffset + xR
                r[idx] -= factor * deltaR
                g[idx] -= factor * deltaG
                b[idx] -= factor * deltaB
            }
        }

        // Pass 2: Vertical boundary reconciliation across top & bottom margins
        for (x in 0 until width) {
            val bottomIdx = (height - 1) * width + x
            val topIdx = x
            val deltaR = r[bottomIdx] - r[topIdx]
            val deltaG = g[bottomIdx] - g[topIdx]
            val deltaB = b[bottomIdx] - b[topIdx]

            for (y in 0 until marginY) {
                val t = 0.5f * (1.0f - Math.cos(Math.PI * y / (2.0 * marginY))).toFloat()
                val factor = 0.5f - t
                val idx = y * width + x
                r[idx] += factor * deltaR
                g[idx] += factor * deltaG
                b[idx] += factor * deltaB
            }
            for (y in 0 until marginY) {
                val yB = height - marginY + y
                val t = 0.5f * (1.0f - Math.cos(Math.PI * (y + marginY) / (2.0 * marginY))).toFloat()
                val factor = t - 0.5f
                val idx = yB * width + x
                r[idx] -= factor * deltaR
                g[idx] -= factor * deltaG
                b[idx] -= factor * deltaB
            }
        }

        val outPixels = IntArray(total)
        for (i in 0 until total) {
            val red = r[i].roundToInt().coerceIn(0, 255)
            val green = g[i].roundToInt().coerceIn(0, 255)
            val blue = b[i].roundToInt().coerceIn(0, 255)
            outPixels[i] = (a[i] shl 24) or (red shl 16) or (green shl 8) or blue
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        resultBitmap
    }

    override suspend fun makeSeamlessFloatMap(
        map: FloatArray,
        width: Int,
        height: Int,
        blendMarginRatio: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val outMap = map.copyOf()
        val marginX = (width * blendMarginRatio.coerceIn(0.10f, 0.50f)).roundToInt().coerceIn(4, width / 2)
        val marginY = (height * blendMarginRatio.coerceIn(0.10f, 0.50f)).roundToInt().coerceIn(4, height / 2)

        // Pass 1: Horizontal boundary reconciliation
        for (y in 0 until height) {
            val rowOffset = y * width
            val deltaH = outMap[rowOffset + width - 1] - outMap[rowOffset]

            for (x in 0 until marginX) {
                val t = 0.5f * (1.0f - Math.cos(Math.PI * x / (2.0 * marginX))).toFloat()
                outMap[rowOffset + x] += (0.5f - t) * deltaH
            }
            for (x in 0 until marginX) {
                val xR = width - marginX + x
                val t = 0.5f * (1.0f - Math.cos(Math.PI * (x + marginX) / (2.0 * marginX))).toFloat()
                outMap[rowOffset + xR] -= (t - 0.5f) * deltaH
            }
        }

        // Pass 2: Vertical boundary reconciliation
        for (x in 0 until width) {
            val deltaV = outMap[(height - 1) * width + x] - outMap[x]

            for (y in 0 until marginY) {
                val t = 0.5f * (1.0f - Math.cos(Math.PI * y / (2.0 * marginY))).toFloat()
                outMap[y * width + x] += (0.5f - t) * deltaV
            }
            for (y in 0 until marginY) {
                val yB = height - marginY + y
                val t = 0.5f * (1.0f - Math.cos(Math.PI * (y + marginY) / (2.0 * marginY))).toFloat()
                outMap[yB * width + x] -= (t - 0.5f) * deltaV
            }
        }

        outMap
    }

    override suspend fun analyzeSeamContinuity(
        source: Bitmap
    ): SeamAnalysisResult = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalHDiff = 0.0
        var totalVDiff = 0.0
        var maxDiff = 0.0f

        // Horizontal seam: compare left col (x = 0) to right col (x = width - 1)
        for (y in 0 until height) {
            val left = pixels[y * width]
            val right = pixels[y * width + (width - 1)]

            val lr = (left ushr 16) and 0xFF
            val lg = (left ushr 8) and 0xFF
            val lb = left and 0xFF

            val rr = (right ushr 16) and 0xFF
            val rg = (right ushr 8) and 0xFF
            val rb = right and 0xFF

            val dr = (lr - rr) / 255.0f
            val dg = (lg - rg) / 255.0f
            val db = (lb - rb) / 255.0f

            val diff = sqrt((dr * dr + dg * dg + db * db) / 3.0f)
            totalHDiff += (diff * diff)
            if (diff > maxDiff) maxDiff = diff
        }

        // Vertical seam: compare top row (y = 0) to bottom row (y = height - 1)
        val bottomRowOffset = (height - 1) * width
        for (x in 0 until width) {
            val top = pixels[x]
            val bottom = pixels[bottomRowOffset + x]

            val tr = (top ushr 16) and 0xFF
            val tg = (top ushr 8) and 0xFF
            val tb = top and 0xFF

            val br = (bottom ushr 16) and 0xFF
            val bg = (bottom ushr 8) and 0xFF
            val bb = bottom and 0xFF

            val dr = (tr - br) / 255.0f
            val dg = (tg - bg) / 255.0f
            val db = (tb - bb) / 255.0f

            val diff = sqrt((dr * dr + dg * dg + db * db) / 3.0f)
            totalVDiff += (diff * diff)
            if (diff > maxDiff) maxDiff = diff
        }

        val rmsH = sqrt(totalHDiff / height).toFloat()
        val rmsV = sqrt(totalVDiff / width).toFloat()
        val combinedDiscontinuity = (rmsH + rmsV) / 2.0f
        val continuityPercent = ((1.0f - combinedDiscontinuity).coerceIn(0.0f, 1.0f)) * 100.0f

        SeamAnalysisResult(
            seamDiscontinuityScore = combinedDiscontinuity,
            continuityPercent = continuityPercent,
            horizontalSeamError = rmsH,
            verticalSeamError = rmsV,
            maxEdgeDiscontinuity = maxDiff,
            isSeamless = continuityPercent >= 88.0f
        )
    }

    override suspend fun applyExclusionMask(
        heightMap: FloatArray,
        maskBitmap: Bitmap?,
        width: Int,
        height: Int,
        iterations: Int
    ): FloatArray = withContext(Dispatchers.Default) {
        if (maskBitmap == null) {
            return@withContext heightMap.copyOf()
        }

        val totalPixels = width * height
        val mask = BooleanArray(totalPixels)
        val maskWidth = maskBitmap.width
        val maskHeight = maskBitmap.height
        val maskPixels = IntArray(maskWidth * maskHeight)
        maskBitmap.getPixels(maskPixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

        var hasMaskedPixels = false
        val scaleX = maskWidth.toFloat() / width.toFloat()
        val scaleY = maskHeight.toFloat() / height.toFloat()

        for (y in 0 until height) {
            val my = ((y + 0.5f) * scaleY).toInt().coerceIn(0, maskHeight - 1)
            val myOffset = my * maskWidth
            val yOffset = y * width
            for (x in 0 until width) {
                val mx = ((x + 0.5f) * scaleX).toInt().coerceIn(0, maskWidth - 1)
                val pixel = maskPixels[myOffset + mx]
                val alpha = (pixel ushr 24) and 0xFF
                val red = (pixel ushr 16) and 0xFF
                // Mask pixel if drawn with semi-transparent red or active alpha
                val isMasked = alpha > 30 && (red > 40 || alpha > 60)
                if (isMasked) {
                    mask[yOffset + x] = true
                    hasMaskedPixels = true
                }
            }
        }

        if (!hasMaskedPixels) {
            return@withContext heightMap.copyOf()
        }

        inpaintHeightMap(heightMap, mask, width, height, iterations)
    }

    override suspend fun inpaintHeightMap(
        heightMap: FloatArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        iterations: Int
    ): FloatArray = withContext(Dispatchers.Default) {
        val totalPixels = width * height
        var maskedCount = 0
        for (i in 0 until totalPixels) {
            if (mask[i]) maskedCount++
        }
        if (maskedCount == 0 || maskedCount == totalPixels) {
            return@withContext heightMap.copyOf()
        }

        val result = heightMap.copyOf()

        // 1. Gather all boundary unmasked neighbor heights to initialize the interior smoothly
        var boundarySum = 0.0
        var boundaryCount = 0
        val isBoundary = BooleanArray(totalPixels)

        for (y in 0 until height) {
            val yOffset = y * width
            for (x in 0 until width) {
                val idx = yOffset + x
                if (mask[idx]) {
                    // Check 4 neighbors
                    var touchesUnmasked = false
                    if (x > 0 && !mask[idx - 1]) touchesUnmasked = true
                    if (x < width - 1 && !mask[idx + 1]) touchesUnmasked = true
                    if (y > 0 && !mask[idx - width]) touchesUnmasked = true
                    if (y < height - 1 && !mask[idx + width]) touchesUnmasked = true

                    if (touchesUnmasked) {
                        isBoundary[idx] = true
                        var localSum = 0f
                        var localCnt = 0
                        if (x > 0 && !mask[idx - 1]) { localSum += result[idx - 1]; localCnt++ }
                        if (x < width - 1 && !mask[idx + 1]) { localSum += result[idx + 1]; localCnt++ }
                        if (y > 0 && !mask[idx - width]) { localSum += result[idx - width]; localCnt++ }
                        if (y < height - 1 && !mask[idx + width]) { localSum += result[idx + width]; localCnt++ }
                        if (localCnt > 0) {
                            val avg = localSum / localCnt
                            result[idx] = avg
                            boundarySum += avg
                            boundaryCount++
                        }
                    }
                }
            }
        }

        // Initialize non-boundary masked pixels to the mean boundary value
        val initialFill = if (boundaryCount > 0) (boundarySum / boundaryCount).toFloat() else 0.5f
        for (i in 0 until totalPixels) {
            if (mask[i] && !isBoundary[i]) {
                result[i] = initialFill
            }
        }

        // 2. Perform N iterations of harmonic relaxation (discrete Laplacian smoothing on masked pixels)
        var currentBuffer = result
        var nextBuffer = result.copyOf()

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerChunk = (height + numCores - 1) / numCores

        for (iter in 0 until iterations) {
            coroutineScope {
                (0 until numCores).map { chunkIndex ->
                    async {
                        val startY = chunkIndex * rowsPerChunk
                        val endY = min(height, startY + rowsPerChunk)
                        for (y in startY until endY) {
                            val yOffset = y * width
                            for (x in 0 until width) {
                                val idx = yOffset + x
                                if (mask[idx]) {
                                    val left = if (x > 0) currentBuffer[idx - 1] else currentBuffer[idx]
                                    val right = if (x < width - 1) currentBuffer[idx + 1] else currentBuffer[idx]
                                    val top = if (y > 0) currentBuffer[idx - width] else currentBuffer[idx]
                                    val bottom = if (y < height - 1) currentBuffer[idx + width] else currentBuffer[idx]
                                    nextBuffer[idx] = (left + right + top + bottom) * 0.25f
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
            // Swap buffers
            val temp = currentBuffer
            currentBuffer = nextBuffer
            nextBuffer = temp
        }

        // Coerce output within [0.0, 1.0] and clean any NaN/Infs
        for (i in 0 until totalPixels) {
            val v = currentBuffer[i]
            if (v.isNaN() || v.isInfinite()) {
                currentBuffer[i] = 0.5f
            } else {
                currentBuffer[i] = v.coerceIn(0.0f, 1.0f)
            }
        }

        currentBuffer
    }

    override suspend fun generate2x2TiledBitmap(
        source: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height
        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val outW = w * 2
        val outH = h * 2
        val outPixels = IntArray(outW * outH)

        for (y in 0 until outH) {
            val srcY = y % h
            val srcRowOffset = srcY * w
            val outRowOffset = y * outW

            for (x in 0 until outW) {
                val srcX = x % w
                outPixels[outRowOffset + x] = srcPixels[srcRowOffset + srcX]
            }
        }

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        result
    }
}
