package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.crypto.KeystoreHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "texforge_settings")

enum class QualityPreset(val label: String, val maxResolution: Int, val description: String) {
    DRAFT_1024("Draft (1024px)", 1024, "Fastest generation, ideal for mobile previews and low-end devices."),
    STANDARD_2048("Standard (2048px)", 2048, "Balanced performance & detail. Recommended for most projects."),
    HIGH_4096("High (4096px)", 4096, "High-fidelity textures. Demands high RAM."),
    ULTRA_8192("Ultra (8192px)", 8192, "EXPERIMENTAL: Heavy memory and processing load. Tiled pipeline.")
}

enum class NormalFormat(val label: String, val description: String) {
    OPEN_GL("OpenGL (+Y Up)", "Blender, Maya, Godot, Unity"),
    DIRECT_X("DirectX (-Y Down)", "Unreal Engine, 3ds Max")
}

enum class EnginePreference(val label: String, val description: String) {
    LOCAL_ONLY("Local Only", "Runs 100% on-device using ONNX Runtime. Fully offline."),
    LOCAL_WITH_FALLBACK("Local + Cloud Fallback", "Uses on-device ONNX first; falls back to Gemini if model is unavailable.")
}

enum class ExportFormat(val extension: String, val label: String) {
    PNG("png", "PNG (Lossless 8/16-bit)"),
    JPG("jpg", "JPEG (Configurable quality)"),
    WEBP("webp", "WebP (Modern compressed)")
}

data class AppSettings(
    val qualityPreset: QualityPreset = QualityPreset.STANDARD_2048,
    val normalFormat: NormalFormat = NormalFormat.OPEN_GL,
    val enginePreference: EnginePreference = EnginePreference.LOCAL_ONLY,
    val heightBitDepth: Int = 16,
    val tileSize: Int = 512,
    val aoSamples: Int = 16,
    val normalScale1: Float = 1.0f,
    val normalScale2: Float = 0.75f,
    val normalScale3: Float = 0.5f,
    val normalScale4: Float = 0.25f,
    val exportFormat: ExportFormat = ExportFormat.PNG,
    val jpgQuality: Int = 90,
    val geminiModel: String = "gemini-2.5-flash-image",
    val hasApiKey: Boolean = false
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val context: Context
) {
    companion object {
        val KEY_QUALITY_PRESET = stringPreferencesKey("quality_preset")
        val KEY_NORMAL_FORMAT = stringPreferencesKey("normal_format")
        val KEY_ENGINE_PREF = stringPreferencesKey("engine_preference")
        val KEY_HEIGHT_BIT_DEPTH = intPreferencesKey("height_bit_depth")
        val KEY_TILE_SIZE = intPreferencesKey("tile_size")
        val KEY_AO_SAMPLES = intPreferencesKey("ao_samples")
        val KEY_NORMAL_SCALE_1 = floatPreferencesKey("normal_scale_1")
        val KEY_NORMAL_SCALE_2 = floatPreferencesKey("normal_scale_2")
        val KEY_NORMAL_SCALE_3 = floatPreferencesKey("normal_scale_3")
        val KEY_NORMAL_SCALE_4 = floatPreferencesKey("normal_scale_4")
        val KEY_EXPORT_FORMAT = stringPreferencesKey("export_format")
        val KEY_JPG_QUALITY = intPreferencesKey("jpg_quality")
        val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val KEY_ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        val KEY_API_KEY_IV = stringPreferencesKey("api_key_iv")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val quality = prefs[KEY_QUALITY_PRESET]?.let { name ->
            runCatching { QualityPreset.valueOf(name) }.getOrNull()
        } ?: QualityPreset.STANDARD_2048

        val normal = prefs[KEY_NORMAL_FORMAT]?.let { name ->
            runCatching { NormalFormat.valueOf(name) }.getOrNull()
        } ?: NormalFormat.OPEN_GL

        val engine = prefs[KEY_ENGINE_PREF]?.let { name ->
            runCatching { EnginePreference.valueOf(name) }.getOrNull()
        } ?: EnginePreference.LOCAL_ONLY

        val format = prefs[KEY_EXPORT_FORMAT]?.let { name ->
            runCatching { ExportFormat.valueOf(name) }.getOrNull()
        } ?: ExportFormat.PNG

        val bitDepth = prefs[KEY_HEIGHT_BIT_DEPTH] ?: 16
        val tileSize = prefs[KEY_TILE_SIZE] ?: 512
        val aoSamples = prefs[KEY_AO_SAMPLES] ?: 16
        val s1 = prefs[KEY_NORMAL_SCALE_1] ?: 1.0f
        val s2 = prefs[KEY_NORMAL_SCALE_2] ?: 0.75f
        val s3 = prefs[KEY_NORMAL_SCALE_3] ?: 0.5f
        val s4 = prefs[KEY_NORMAL_SCALE_4] ?: 0.25f
        val jpgQuality = prefs[KEY_JPG_QUALITY] ?: 90
        val geminiModel = prefs[KEY_GEMINI_MODEL] ?: "gemini-2.5-flash-image"
        val hasKey = !prefs[KEY_ENCRYPTED_API_KEY].isNullOrEmpty()

        AppSettings(
            qualityPreset = quality,
            normalFormat = normal,
            enginePreference = engine,
            heightBitDepth = bitDepth,
            tileSize = tileSize,
            aoSamples = aoSamples,
            normalScale1 = s1,
            normalScale2 = s2,
            normalScale3 = s3,
            normalScale4 = s4,
            exportFormat = format,
            jpgQuality = jpgQuality,
            geminiModel = geminiModel,
            hasApiKey = hasKey
        )
    }

    suspend fun updateQualityPreset(preset: QualityPreset) {
        context.dataStore.edit { it[KEY_QUALITY_PRESET] = preset.name }
    }

    suspend fun updateNormalFormat(format: NormalFormat) {
        context.dataStore.edit { it[KEY_NORMAL_FORMAT] = format.name }
    }

    suspend fun updateEnginePreference(preference: EnginePreference) {
        context.dataStore.edit { it[KEY_ENGINE_PREF] = preference.name }
    }

    suspend fun updateHeightBitDepth(bitDepth: Int) {
        context.dataStore.edit { it[KEY_HEIGHT_BIT_DEPTH] = bitDepth }
    }

    suspend fun updateTileSize(tileSize: Int) {
        context.dataStore.edit { it[KEY_TILE_SIZE] = tileSize }
    }

    suspend fun updateAoSamples(samples: Int) {
        context.dataStore.edit { it[KEY_AO_SAMPLES] = samples }
    }

    suspend fun updateNormalScales(s1: Float, s2: Float, s3: Float, s4: Float) {
        context.dataStore.edit {
            it[KEY_NORMAL_SCALE_1] = s1
            it[KEY_NORMAL_SCALE_2] = s2
            it[KEY_NORMAL_SCALE_3] = s3
            it[KEY_NORMAL_SCALE_4] = s4
        }
    }

    suspend fun updateExportFormat(format: ExportFormat) {
        context.dataStore.edit { it[KEY_EXPORT_FORMAT] = format.name }
    }

    suspend fun updateJpgQuality(quality: Int) {
        context.dataStore.edit { it[KEY_JPG_QUALITY] = quality.coerceIn(50, 100) }
    }

    suspend fun setApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            context.dataStore.edit {
                it.remove(KEY_ENCRYPTED_API_KEY)
                it.remove(KEY_API_KEY_IV)
            }
        } else {
            val (ciphertext, iv) = KeystoreHelper.encrypt(apiKey)
            context.dataStore.edit {
                it[KEY_ENCRYPTED_API_KEY] = ciphertext
                it[KEY_API_KEY_IV] = iv
            }
        }
    }

    suspend fun getApiKey(): String {
        var result = ""
        context.dataStore.edit { prefs ->
            val ciphertext = prefs[KEY_ENCRYPTED_API_KEY] ?: ""
            val iv = prefs[KEY_API_KEY_IV] ?: ""
            result = KeystoreHelper.decrypt(ciphertext, iv)
        }
        return result
    }
}
