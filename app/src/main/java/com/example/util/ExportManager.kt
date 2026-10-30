package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.ui.GeneratedMapState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Target 3D/Game engine export presets matching export/code.html.
 */
enum class ExportEnginePreset(
    val id: String,
    val title: String,
    val description: String,
    val badge: String,
    val subtitle: String
) {
    GENERIC(
        id = "generic",
        title = "Generic PBR",
        description = "Discrete map bundle",
        badge = "Standard",
        subtitle = "Height, Normal, Roughness, AO, Curvature, Metallic, Albedo, Noise"
    ),
    UNITY(
        id = "unity",
        title = "Unity",
        description = "Standard specular / metallic setup",
        badge = "URP / HDRP",
        subtitle = "Albedo, Normal, Height, MaskMap (Metallic, AO, Detail, Smoothness)"
    ),
    UNREAL(
        id = "unreal",
        title = "Unreal",
        description = "Packed ORM texture layout",
        badge = "UE 5.4+",
        subtitle = "BaseColor, Normal, Height, Packed ORM (Occlusion, Roughness, Metallic)"
    ),
    GODOT(
        id = "godot",
        title = "Godot",
        description = "Standard roughness/metallic specs",
        badge = "StandardMaterial3D",
        subtitle = "Albedo, Normal, Roughness, Metallic, Height, AO"
    ),
    BLENDER(
        id = "blender",
        title = "Blender",
        description = "Principled BSDF node layout",
        badge = "Cycles / Eevee",
        subtitle = "BaseColor, Roughness, Normal, Displacement, Metallic, AO"
    )
}

data class ExportResult(
    val success: Boolean,
    val message: String,
    val savedCount: Int = 0,
    val fileUri: Uri? = null
)

/**
 * Manager handling texture packing, zip archiving, MediaStore saving, and system sharing.
 */
object ExportManager {

    /**
     * Packs Ambient Occlusion (R), Roughness (G), and Metallic (B) into a single ORM texture for Unreal Engine.
     */
    fun packOrmBitmap(
        aoBitmap: Bitmap?,
        roughnessBitmap: Bitmap?,
        metallicBitmap: Bitmap?,
        width: Int,
        height: Int
    ): Bitmap {
        val ormBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val total = width * height
        val outPixels = IntArray(total)

        val aoPixels = IntArray(total)
        val roughPixels = IntArray(total)
        val metalPixels = IntArray(total)

        aoBitmap?.let {
            val scaled = if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true)
            scaled.getPixels(aoPixels, 0, width, 0, 0, width, height)
        } ?: run {
            aoPixels.fill((0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF) // default AO 1.0 (white)
        }

        roughnessBitmap?.let {
            val scaled = if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true)
            scaled.getPixels(roughPixels, 0, width, 0, 0, width, height)
        } ?: run {
            roughPixels.fill((0xFF shl 24) or (0x80 shl 16) or (0x80 shl 8) or 0x80) // default 0.5
        }

        metallicBitmap?.let {
            val scaled = if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true)
            scaled.getPixels(metalPixels, 0, width, 0, 0, width, height)
        } ?: run {
            metalPixels.fill((0xFF shl 24)) // default 0.0 (black)
        }

        for (i in 0 until total) {
            val r = (aoPixels[i] ushr 16) and 0xFF       // R = Occlusion
            val g = (roughPixels[i] ushr 8) and 0xFF     // G = Roughness
            val b = metalPixels[i] and 0xFF              // B = Metallic
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        ormBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return ormBitmap
    }

    /**
     * Packs Metallic (R), Occlusion (G), Detail (B), and Smoothness (A = 1 - Roughness) for Unity URP/HDRP.
     */
    fun packUnityMaskMap(
        metallicBitmap: Bitmap?,
        aoBitmap: Bitmap?,
        detailBitmap: Bitmap?,
        roughnessBitmap: Bitmap?,
        width: Int,
        height: Int
    ): Bitmap {
        val maskMap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val total = width * height
        val outPixels = IntArray(total)

        val metalPixels = IntArray(total)
        val aoPixels = IntArray(total)
        val detailPixels = IntArray(total)
        val roughPixels = IntArray(total)

        metallicBitmap?.let {
            val scaled = if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true)
            scaled.getPixels(metalPixels, 0, width, 0, 0, width, height)
        }

        aoBitmap?.let {
            val scaled = if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true)
            scaled.getPixels(aoPixels, 0, width, 0, 0, width, height)
        } ?: run {
            aoPixels.fill((0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF)
        }

        detailBitmap?.let {
            val scaled = if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true)
            scaled.getPixels(detailPixels, 0, width, 0, 0, width, height)
        }

        roughnessBitmap?.let {
            val scaled = if (it.width == width && it.height == height) it else Bitmap.createScaledBitmap(it, width, height, true)
            scaled.getPixels(roughPixels, 0, width, 0, 0, width, height)
        } ?: run {
            roughPixels.fill((0xFF shl 24) or (0x80 shl 16) or (0x80 shl 8) or 0x80)
        }

        for (i in 0 until total) {
            val r = metalPixels[i] and 0xFF              // R = Metallic
            val g = (aoPixels[i] ushr 8) and 0xFF        // G = Occlusion
            val b = detailPixels[i] and 0xFF             // B = Detail mask
            val roughVal = (roughPixels[i] ushr 8) and 0xFF
            val a = 255 - roughVal                       // A = Smoothness (1 - roughness)
            outPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        maskMap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return maskMap
    }

    /**
     * Prepares map filenames and bitmaps according to the selected engine preset.
     */
    fun prepareExportMapBundle(
        state: GeneratedMapState,
        preset: ExportEnginePreset,
        materialName: String = "Material"
    ): Map<String, Bitmap> {
        val maps = mutableMapOf<String, Bitmap>()
        val w = state.resolution
        val h = state.resolution

        when (preset) {
            ExportEnginePreset.GENERIC -> {
                state.heightBitmap?.let { maps["${materialName}_Height.png"] = it }
                state.normalBitmap?.let { maps["${materialName}_Normal.png"] = it }
                state.roughnessBitmap?.let { maps["${materialName}_Roughness.png"] = it }
                state.aoBitmap?.let { maps["${materialName}_AO.png"] = it }
                state.curvatureBitmap?.let { maps["${materialName}_Curvature.png"] = it }
                state.metallicBitmap?.let { maps["${materialName}_Metallic.png"] = it }
                state.albedoBitmap?.let { maps["${materialName}_Albedo.png"] = it }
                state.noiseBitmap?.let { maps["${materialName}_Noise.png"] = it }
            }
            ExportEnginePreset.UNREAL -> {
                state.albedoBitmap?.let { maps["${materialName}_BaseColor.png"] = it }
                state.normalBitmap?.let { maps["${materialName}_Normal.png"] = it }
                state.heightBitmap?.let { maps["${materialName}_Displacement.png"] = it }
                val orm = packOrmBitmap(state.aoBitmap, state.roughnessBitmap, state.metallicBitmap, w, h)
                maps["${materialName}_ORM.png"] = orm
                state.roughnessBitmap?.let { maps["${materialName}_Roughness.png"] = it }
                state.aoBitmap?.let { maps["${materialName}_AO.png"] = it }
            }
            ExportEnginePreset.UNITY -> {
                state.albedoBitmap?.let { maps["${materialName}_Albedo.png"] = it }
                state.normalBitmap?.let { maps["${materialName}_Normal.png"] = it }
                state.heightBitmap?.let { maps["${materialName}_Height.png"] = it }
                val maskMap = packUnityMaskMap(state.metallicBitmap, state.aoBitmap, state.noiseBitmap, state.roughnessBitmap, w, h)
                maps["${materialName}_MaskMap.png"] = maskMap
            }
            ExportEnginePreset.GODOT -> {
                state.albedoBitmap?.let { maps["${materialName}_Albedo.png"] = it }
                state.normalBitmap?.let { maps["${materialName}_Normal.png"] = it }
                state.roughnessBitmap?.let { maps["${materialName}_Roughness.png"] = it }
                state.metallicBitmap?.let { maps["${materialName}_Metallic.png"] = it }
                state.heightBitmap?.let { maps["${materialName}_Height.png"] = it }
                state.aoBitmap?.let { maps["${materialName}_AO.png"] = it }
            }
            ExportEnginePreset.BLENDER -> {
                state.albedoBitmap?.let { maps["${materialName}_BaseColor.png"] = it }
                state.roughnessBitmap?.let { maps["${materialName}_Roughness.png"] = it }
                state.normalBitmap?.let { maps["${materialName}_Normal.png"] = it }
                state.heightBitmap?.let { maps["${materialName}_Displacement.png"] = it }
                state.metallicBitmap?.let { maps["${materialName}_Metallic.png"] = it }
                state.aoBitmap?.let { maps["${materialName}_AO.png"] = it }
            }
        }
        return maps
    }

    /**
     * Estimates total uncompressed/compressed export file size in megabytes.
     */
    fun calculateTotalExportSizeMb(maps: Map<String, Bitmap>): Float {
        var totalBytes: Long = 0
        for ((_, bmp) in maps) {
            // PNG typically compresses 24/32-bit images by ~30% to 50%
            totalBytes += (bmp.width.toLong() * bmp.height.toLong() * 2L)
        }
        return (totalBytes / (1024f * 1024f)).coerceAtLeast(0.1f)
    }

    /**
     * Writes all maps into a ZIP archive output stream.
     */
    fun writeZipArchive(
        files: Map<String, Bitmap>,
        outputStream: OutputStream,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ) {
        ZipOutputStream(outputStream.buffered()).use { zipOut ->
            for ((filename, bmp) in files) {
                val entry = ZipEntry(filename)
                zipOut.putNextEntry(entry)
                bmp.compress(format, quality, zipOut)
                zipOut.closeEntry()
            }
            zipOut.finish()
        }
    }

    /**
     * Saves maps directly to device public MediaStore storage (Downloads or Pictures).
     */
    suspend fun saveToDevice(
        context: Context,
        state: GeneratedMapState,
        preset: ExportEnginePreset,
        bundleAsZip: Boolean,
        materialName: String = "Material"
    ): ExportResult = withContext(Dispatchers.IO) {
        val maps = prepareExportMapBundle(state, preset, materialName)
        if (maps.isEmpty()) {
            return@withContext ExportResult(false, "No generated maps to export.")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val resolver = context.contentResolver

        if (bundleAsZip) {
            val zipName = "${materialName}_${preset.id}_$timestamp.zip"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, zipName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TexForge")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = resolver.insert(collection, contentValues)
                ?: return@withContext ExportResult(false, "Failed to create target ZIP in Downloads.")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    writeZipArchive(maps, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                return@withContext ExportResult(
                    success = true,
                    message = "Saved $zipName to Downloads/TexForge",
                    savedCount = maps.size,
                    fileUri = uri
                )
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                return@withContext ExportResult(false, "Save error: ${e.localizedMessage ?: "Unknown failure"}")
            }
        } else {
            // Save discrete PNG files to Pictures/TexForge
            var savedCount = 0
            for ((filename, bmp) in maps) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TexForge/${materialName}_$timestamp")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    savedCount++
                }
            }
            return@withContext ExportResult(
                success = savedCount > 0,
                message = "Saved $savedCount PNG maps to Pictures/TexForge",
                savedCount = savedCount
            )
        }
    }

    /**
     * Prepares files in app cache and launches system Share sheet via FileProvider.
     */
    suspend fun shareMaterial(
        context: Context,
        state: GeneratedMapState,
        preset: ExportEnginePreset,
        bundleAsZip: Boolean,
        materialName: String = "Material"
    ): ExportResult = withContext(Dispatchers.IO) {
        val maps = prepareExportMapBundle(state, preset, materialName)
        if (maps.isEmpty()) {
            return@withContext ExportResult(false, "No generated maps to share.")
        }

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        // Clean previous exports
        exportDir.listFiles()?.forEach { it.delete() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val authority = "${context.packageName}.fileprovider"

        if (bundleAsZip) {
            val zipFile = File(exportDir, "${materialName}_${preset.id}_$timestamp.zip")
            FileOutputStream(zipFile).use { out ->
                writeZipArchive(maps, out)
            }

            val uri = FileProvider.getUriForFile(context, authority, zipFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$materialName PBR Maps (${preset.title})")
                putExtra(Intent.EXTRA_TEXT, "Exported $materialName PBR maps pack for ${preset.title}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Share $materialName PBR Maps").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            return@withContext ExportResult(true, "Ready to share ZIP", maps.size, uri)
        } else {
            val uris = ArrayList<Uri>()
            for ((filename, bmp) in maps) {
                val file = File(exportDir, filename)
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val uri = FileProvider.getUriForFile(context, authority, file)
                uris.add(uri)
            }

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/png"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "$materialName PBR Maps")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Share $materialName Maps").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            return@withContext ExportResult(true, "Ready to share ${uris.size} maps", uris.size)
        }
    }
}
