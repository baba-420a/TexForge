package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.dao.ProjectDao
import com.example.data.local.entity.ProjectEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.EnginePreference
import com.example.data.preferences.ExportFormat
import com.example.data.preferences.GenerationMode
import com.example.data.preferences.NormalFormat
import com.example.data.preferences.QualityPreset
import com.example.data.preferences.UserPreferencesRepository
import com.example.engine.DepthModelDiagnosticResult
import com.example.engine.MapProcessor
import com.example.engine.OnnxDepthEngine
import com.example.util.ImageDecoderHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImageInputState(
    val sourceBitmap: Bitmap? = null,
    val croppedBitmap: Bitmap? = null,
    val exclusionMaskBitmap: Bitmap? = null,
    val isCropping: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class GeneratedMapState(
    val resolution: Int = 2048,
    // 8 PBR Maps (Bitmaps)
    val heightBitmap: Bitmap? = null,
    val normalBitmap: Bitmap? = null,
    val roughnessBitmap: Bitmap? = null,
    val aoBitmap: Bitmap? = null,
    val curvatureBitmap: Bitmap? = null,
    val metallicBitmap: Bitmap? = null,
    val albedoBitmap: Bitmap? = null,
    val noiseBitmap: Bitmap? = null,
    // Raw Primitive Map Buffers
    val baseDepthMap: FloatArray? = null,
    val highPassLuminance: FloatArray? = null,
    val heightMap: FloatArray? = null,
    val normalPixels: IntArray? = null,
    val roughnessMap: FloatArray? = null,
    val aoMap: FloatArray? = null,
    val curvatureMap: FloatArray? = null,
    val metallicMap: FloatArray? = null,
    val albedoPixels: IntArray? = null,
    val noiseMap: FloatArray? = null,
    // Live adjustment parameters per map
    val detailBoost: Float = 0.5f,
    val normalStrength: Float = 1.5f,
    val invertHeight: Boolean = false,
    val roughnessBase: Float = 0.5f,
    val roughnessContrast: Float = 1.0f,
    val aoRadius: Int = 12,
    val aoIntensity: Float = 1.5f,
    val curvatureContrast: Float = 1.5f,
    val metallicThreshold: Float = 0.85f,
    val metallicBoost: Float = 1.0f,
    val deLightStrength: Float = 0.7f,
    val noiseScale: Float = 8.0f,
    val noiseOctaves: Int = 4,
    val noiseIntensity: Float = 1.0f,
    val blendNoiseIntoHeight: Float = 0.0f,
    // Seamless Tiling & Seam Synthesis (Phase 5)
    val isSeamless: Boolean = false,
    val seamAnalysis: com.example.engine.SeamAnalysisResult? = null,
    val tiled2x2Bitmap: Bitmap? = null,
    val show2x2Tiled: Boolean = false,
    val seamlessBlendMargin: Float = 0.20f,
    // Blemish Exclusion Mask (Phase 6)
    val exclusionMaskBitmap: Bitmap? = null
)

data class ProcessingState(
    val isProcessing: Boolean = false,
    val progressPercent: Int = 0,
    val currentStep: String = "",
    val errorMessage: String? = null,
    val isModelMissing: Boolean = false
)

data class ModelStatus(
    val isAvailable: Boolean = false,
    val isInAssets: Boolean = false,
    val isImported: Boolean = false,
    val fileSizeMb: Float = 0f
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val onnxDepthEngine: OnnxDepthEngine,
    private val mapProcessor: MapProcessor,
    private val projectDao: ProjectDao
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = preferencesRepository.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val projectsList: StateFlow<List<ProjectEntity>> = projectDao.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _imageState = MutableStateFlow(ImageInputState())
    val imageState: StateFlow<ImageInputState> = _imageState.asStateFlow()
    private val _processingState = MutableStateFlow(ProcessingState())
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _generatedState = MutableStateFlow<GeneratedMapState?>(null)
    val generatedState: StateFlow<GeneratedMapState?> = _generatedState.asStateFlow()

    private val _aiDepthState = MutableStateFlow<GeneratedMapState?>(null)
    val aiDepthState: StateFlow<GeneratedMapState?> = _aiDepthState.asStateFlow()

    private val _fastState = MutableStateFlow<GeneratedMapState?>(null)
    val fastState: StateFlow<GeneratedMapState?> = _fastState.asStateFlow()

    private val _activeVariant = MutableStateFlow<GenerationMode>(GenerationMode.AI_DEPTH)
    val activeVariant: StateFlow<GenerationMode> = _activeVariant.asStateFlow()

    private val _selfTestResult = MutableStateFlow<DepthModelDiagnosticResult?>(null)
    val selfTestResult: StateFlow<DepthModelDiagnosticResult?> = _selfTestResult.asStateFlow()

    private val _isSelfTesting = MutableStateFlow(false)
    val isSelfTesting: StateFlow<Boolean> = _isSelfTesting.asStateFlow()

    private val _modelStatus = MutableStateFlow(ModelStatus())
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private var pendingCameraUri: Uri? = null
    private var processingJob: Job? = null

    init {
        refreshModelStatus()
    }

    fun runDepthModelSelfTest() {
        viewModelScope.launch {
            _isSelfTesting.value = true
            _selfTestResult.value = null
            val result = onnxDepthEngine.runSelfTest(useNnapi = appSettings.value.useNnapi)
            _selfTestResult.value = result
            _isSelfTesting.value = false
        }
    }

    fun clearSelfTestResult() {
        _selfTestResult.value = null
    }

    fun setGenerationMode(mode: GenerationMode) {
        viewModelScope.launch {
            preferencesRepository.updateGenerationMode(mode)
            _activeVariant.value = mode
        }
    }

    fun switchVariant(variant: GenerationMode) {
        _activeVariant.value = variant
        when (variant) {
            GenerationMode.AI_DEPTH -> {
                if (_aiDepthState.value != null) _generatedState.value = _aiDepthState.value
            }
            GenerationMode.FAST -> {
                if (_fastState.value != null) _generatedState.value = _fastState.value
            }
            GenerationMode.COMPARE_BOTH -> {
                if (_aiDepthState.value != null) _generatedState.value = _aiDepthState.value
            }
        }
    }

    fun refreshModelStatus() {
        val inAssets = onnxDepthEngine.isModelInAssets()
        val importedFile = onnxDepthEngine.getImportedModelFile()
        val isImported = importedFile.exists() && importedFile.length() > 0
        val sizeMb = if (isImported) importedFile.length() / (1024f * 1024f) else 0f

        _modelStatus.value = ModelStatus(
            isAvailable = inAssets || isImported,
            isInAssets = inAssets,
            isImported = isImported,
            fileSizeMb = sizeMb
        )
    }

    fun importDepthModel(uri: Uri) {
        viewModelScope.launch {
            _processingState.value = _processingState.value.copy(
                isProcessing = true,
                currentStep = "Importing depth model...",
                errorMessage = null
            )
            val result = onnxDepthEngine.importModel(uri)
            if (result.isSuccess) {
                refreshModelStatus()
                _processingState.value = ProcessingState(
                    isProcessing = false,
                    isModelMissing = false
                )
            } else {
                _processingState.value = ProcessingState(
                    isProcessing = false,
                    errorMessage = "Failed to import model: ${result.exceptionOrNull()?.localizedMessage ?: "Invalid ONNX file"}"
                )
            }
        }
    }

    fun prepareCameraCapture(context: Context): Uri {
        val uri = ImageDecoderHelper.createCameraImageUri(context)
        pendingCameraUri = uri
        return uri
    }

    fun onCameraCaptureResult(context: Context, success: Boolean) {
        val uri = pendingCameraUri
        if (success && uri != null) {
            loadSelectedImage(context, uri)
        }
    }

    fun loadSelectedImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _imageState.value = _imageState.value.copy(isLoading = true, errorMessage = null)
            try {
                val maxDim = appSettings.value.qualityPreset.maxResolution
                val decoded = withContext(Dispatchers.IO) {
                    ImageDecoderHelper.decodeSampledBitmap(context, uri, maxDim)
                }

                if (decoded != null) {
                    _imageState.value = _imageState.value.copy(
                        sourceBitmap = decoded,
                        croppedBitmap = null,
                        isCropping = true,
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    _imageState.value = _imageState.value.copy(
                        isLoading = false,
                        errorMessage = "Unable to decode image. Please choose another file."
                    )
                }
            } catch (oom: OutOfMemoryError) {
                _imageState.value = _imageState.value.copy(
                    isLoading = false,
                    errorMessage = "Out of memory loading image. Please try a lower quality preset in Settings."
                )
            } catch (e: Exception) {
                _imageState.value = _imageState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load image: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun onCropConfirmed(cropped: Bitmap) {
        _imageState.value = _imageState.value.copy(
            croppedBitmap = cropped,
            exclusionMaskBitmap = null,
            isCropping = false
        )
    }

    fun setExclusionMask(maskBitmap: Bitmap?) {
        _imageState.value = _imageState.value.copy(exclusionMaskBitmap = maskBitmap)
    }

    fun onCancelCrop() {
        if (_imageState.value.croppedBitmap != null) {
            _imageState.value = _imageState.value.copy(isCropping = false)
        } else {
            _imageState.value = _imageState.value.copy(
                sourceBitmap = null,
                isCropping = false
            )
        }
    }

    fun reCrop() {
        if (_imageState.value.sourceBitmap != null) {
            _imageState.value = _imageState.value.copy(isCropping = true)
        }
    }

    fun clearImage() {
        _imageState.value = ImageInputState()
        _generatedState.value = null
    }

    fun clearResults() {
        _generatedState.value = null
    }

    /**
     * Executes PBR map generation pipeline according to selected GenerationMode.
     */
    fun generatePbrMaps(isSeamless: Boolean = false, blendMargin: Float = 0.20f) {
        val cropped = _imageState.value.croppedBitmap ?: return

        val settings = appSettings.value
        val genMode = settings.generationMode
        val generateAi = genMode == GenerationMode.AI_DEPTH || genMode == GenerationMode.COMPARE_BOTH
        val generateFast = genMode == GenerationMode.FAST || genMode == GenerationMode.COMPARE_BOTH

        if (generateAi && !onnxDepthEngine.hasModel()) {
            _processingState.value = ProcessingState(
                isProcessing = false,
                isModelMissing = true,
                errorMessage = "Depth model (depth_model.onnx) not found. Please import the model or place it in assets/models/."
            )
            return
        }

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _processingState.value = ProcessingState(
                isProcessing = true,
                progressPercent = 5,
                currentStep = "Initializing PBR Pipeline (${genMode.label})${if (isSeamless) " [Seamless Tiling]" else ""}...",
                errorMessage = null
            )

            try {
                val targetResolution = settings.qualityPreset.maxResolution
                val highPassLum = mapProcessor.extractHighPassLuminance(source = cropped, blurRadius = 8)

                var aiResultState: GeneratedMapState? = null
                var fastResultState: GeneratedMapState? = null

                if (generateAi) {
                    _processingState.value = _processingState.value.copy(
                        progressPercent = 15,
                        currentStep = "Running Depth Anything V2 (ONNX)..."
                    )

                    val rawDepth518 = onnxDepthEngine.runInference(
                        bitmap = cropped,
                        useNnapi = settings.useNnapi,
                        onProgress = { p ->
                            _processingState.value = _processingState.value.copy(
                                progressPercent = 15 + (p * 0.30f).toInt(),
                                currentStep = "Running AI Depth Inference ($p%)..."
                            )
                        }
                    )

                    _processingState.value = _processingState.value.copy(
                        progressPercent = 48,
                        currentStep = "Upscaling AI Depth & Blending Surface High-Pass..."
                    )

                    val upscaledDepth = mapProcessor.bicubicUpscale(
                        src = rawDepth518,
                        srcWidth = 518,
                        srcHeight = 518,
                        dstWidth = targetResolution,
                        dstHeight = targetResolution
                    )

                    val blendedHeight = mapProcessor.blendHeightMap(
                        depth = upscaledDepth,
                        highPass = highPassLum,
                        detailBoost = 0.5f,
                        width = targetResolution,
                        height = targetResolution
                    )

                    _processingState.value = _processingState.value.copy(
                        progressPercent = 60,
                        currentStep = if (isSeamless) "Synthesizing Seamless AI Depth PBR Maps..." else "Building AI Depth PBR Maps..."
                    )

                    aiResultState = buildPbrMapSet(
                        cropped = cropped,
                        heightMap = blendedHeight,
                        baseDepthMap = upscaledDepth,
                        highPassLum = highPassLum,
                        targetResolution = targetResolution,
                        settings = settings,
                        isSeamless = isSeamless,
                        seamlessBlendMargin = blendMargin,
                        exclusionMaskBitmap = _imageState.value.exclusionMaskBitmap
                    )
                }

                if (generateFast) {
                    _processingState.value = _processingState.value.copy(
                        progressPercent = if (generateAi) 80 else 30,
                        currentStep = "Computing Fast Multi-Scale Luminance Height Map..."
                    )

                    val fastHeight = mapProcessor.generateFastHeightMap(
                        source = cropped,
                        detailBoost = 0.5f
                    )

                    _processingState.value = _processingState.value.copy(
                        progressPercent = if (generateAi) 90 else 65,
                        currentStep = if (isSeamless) "Synthesizing Seamless Fast PBR Maps..." else "Building Fast PBR Maps..."
                    )

                    fastResultState = buildPbrMapSet(
                        cropped = cropped,
                        heightMap = fastHeight,
                        baseDepthMap = fastHeight,
                        highPassLum = highPassLum,
                        targetResolution = targetResolution,
                        settings = settings,
                        isSeamless = isSeamless,
                        seamlessBlendMargin = blendMargin,
                        exclusionMaskBitmap = _imageState.value.exclusionMaskBitmap
                    )
                }

                _aiDepthState.value = aiResultState
                _fastState.value = fastResultState
                _activeVariant.value = genMode

                _generatedState.value = when (genMode) {
                    GenerationMode.AI_DEPTH -> aiResultState
                    GenerationMode.FAST -> fastResultState
                    GenerationMode.COMPARE_BOTH -> aiResultState ?: fastResultState
                }

                // Automatically persist generated material project into Room database
                val projectTitle = "PBR Material #${(System.currentTimeMillis() % 10000)}"
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        projectDao.insertProject(
                            ProjectEntity(
                                name = projectTitle,
                                createdAt = System.currentTimeMillis(),
                                resolution = settings.qualityPreset.maxResolution,
                                mapsCount = 8,
                                isFavorite = false,
                                generationMode = genMode.name,
                                isSeamless = isSeamless
                            )
                        )
                    } catch (_: Exception) {}
                }

                _processingState.value = ProcessingState(isProcessing = false)
            } catch (ce: CancellationException) {
                _processingState.value = ProcessingState(
                    isProcessing = false,
                    errorMessage = "Generation cancelled."
                )
            } catch (oom: OutOfMemoryError) {
                _processingState.value = ProcessingState(
                    isProcessing = false,
                    errorMessage = "Out of memory during texture processing. Please switch to Standard (2048px) or Draft (1024px) in Settings."
                )
            } catch (e: Exception) {
                _processingState.value = ProcessingState(
                    isProcessing = false,
                    errorMessage = "Generation error: ${e.localizedMessage ?: "Unknown failure"}"
                )
            }
        }
    }

    private suspend fun buildPbrMapSet(
        cropped: Bitmap,
        heightMap: FloatArray,
        baseDepthMap: FloatArray?,
        highPassLum: FloatArray,
        targetResolution: Int,
        settings: AppSettings,
        isSeamless: Boolean = false,
        seamlessBlendMargin: Float = 0.20f,
        exclusionMaskBitmap: Bitmap? = null
    ): GeneratedMapState {
        // Apply blemish exclusion mask to height map if present
        val maskedHeightMap = if (exclusionMaskBitmap != null) {
            mapProcessor.applyExclusionMask(
                heightMap = heightMap,
                maskBitmap = exclusionMaskBitmap,
                width = targetResolution,
                height = targetResolution
            )
        } else {
            heightMap
        }

        val workingCropped = if (isSeamless) {
            mapProcessor.makeSeamlessBitmap(cropped, seamlessBlendMargin)
        } else cropped

        val workingHeightMap = if (isSeamless) {
            mapProcessor.makeSeamlessFloatMap(maskedHeightMap, targetResolution, targetResolution, seamlessBlendMargin)
        } else maskedHeightMap

        val heightBitmap = mapProcessor.floatMapToGrayscaleBitmap(workingHeightMap, targetResolution, targetResolution)
        val initialNormalStrength = 1.5f
        val normalPixels = mapProcessor.generateNormalMap(
            heightMap = workingHeightMap,
            width = targetResolution,
            height = targetResolution,
            strength = initialNormalStrength,
            normalFormat = settings.normalFormat,
            invertHeight = false,
            scale1Weight = settings.normalScale1,
            scale2Weight = settings.normalScale2,
            scale3Weight = settings.normalScale3,
            scale4Weight = settings.normalScale4
        )
        val normalBitmap = mapProcessor.pixelArrayToBitmap(normalPixels, targetResolution, targetResolution)
        val roughnessMap = mapProcessor.generateRoughnessMap(workingCropped, 0.5f, 1.0f)
        val roughnessBitmap = mapProcessor.floatMapToGrayscaleBitmap(roughnessMap, targetResolution, targetResolution)
        val aoMap = mapProcessor.generateAoMap(workingHeightMap, targetResolution, targetResolution, 12, 1.5f)
        val aoBitmap = mapProcessor.floatMapToGrayscaleBitmap(aoMap, targetResolution, targetResolution)
        val curvatureMap = mapProcessor.generateCurvatureMap(workingHeightMap, targetResolution, targetResolution, 1.5f)
        val curvatureBitmap = mapProcessor.floatMapToGrayscaleBitmap(curvatureMap, targetResolution, targetResolution)
        val metallicMap = mapProcessor.generateMetallicMap(workingCropped, 0.85f, 1.0f)
        val metallicBitmap = mapProcessor.floatMapToGrayscaleBitmap(metallicMap, targetResolution, targetResolution)
        val albedoPixels = mapProcessor.generateAlbedoMap(workingCropped, 0.7f)
        val albedoBitmap = mapProcessor.pixelArrayToBitmap(albedoPixels, targetResolution, targetResolution)
        val noiseMap = mapProcessor.generatePerlinNoiseMap(targetResolution, targetResolution, 8.0f, 4, 1.0f)
        val noiseBitmap = mapProcessor.floatMapToGrayscaleBitmap(noiseMap, targetResolution, targetResolution)

        // Real seam continuity calculation & 2x2 tiled repeat preview
        val seamAnalysis = mapProcessor.analyzeSeamContinuity(workingCropped)
        val tiled2x2 = mapProcessor.generate2x2TiledBitmap(workingCropped)

        return GeneratedMapState(
            resolution = targetResolution,
            heightBitmap = heightBitmap,
            normalBitmap = normalBitmap,
            roughnessBitmap = roughnessBitmap,
            aoBitmap = aoBitmap,
            curvatureBitmap = curvatureBitmap,
            metallicBitmap = metallicBitmap,
            albedoBitmap = albedoBitmap,
            noiseBitmap = noiseBitmap,
            baseDepthMap = baseDepthMap,
            highPassLuminance = highPassLum,
            heightMap = workingHeightMap,
            normalPixels = normalPixels,
            roughnessMap = roughnessMap,
            aoMap = aoMap,
            curvatureMap = curvatureMap,
            metallicMap = metallicMap,
            albedoPixels = albedoPixels,
            noiseMap = noiseMap,
            detailBoost = 0.5f,
            normalStrength = initialNormalStrength,
            invertHeight = false,
            isSeamless = isSeamless,
            seamAnalysis = seamAnalysis,
            tiled2x2Bitmap = tiled2x2,
            show2x2Tiled = false,
            seamlessBlendMargin = seamlessBlendMargin,
            exclusionMaskBitmap = exclusionMaskBitmap
        )
    }

    fun toggle2x2TiledView() {
        val current = _generatedState.value ?: return
        val updated = current.copy(show2x2Tiled = !current.show2x2Tiled)
        _generatedState.value = updated
        if (_activeVariant.value == GenerationMode.AI_DEPTH) _aiDepthState.value = updated
        if (_activeVariant.value == GenerationMode.FAST) _fastState.value = updated
    }

    fun applySeamlessTiling(enabled: Boolean, blendMargin: Float = 0.20f) {
        val current = _generatedState.value ?: return
        val cropped = _imageState.value.croppedBitmap ?: return
        val res = current.resolution
        val settings = appSettings.value

        viewModelScope.launch {
            _processingState.value = ProcessingState(
                isProcessing = true,
                progressPercent = 40,
                currentStep = if (enabled) "Synthesizing Seamless Edges..." else "Restoring Raw Edges..."
            )
            try {
                val baseH = current.baseDepthMap ?: current.heightMap ?: FloatArray(res * res) { 0.5f }
                val updated = buildPbrMapSet(
                    cropped = cropped,
                    heightMap = baseH,
                    baseDepthMap = current.baseDepthMap,
                    highPassLum = current.highPassLuminance ?: FloatArray(res * res),
                    targetResolution = res,
                    settings = settings,
                    isSeamless = enabled,
                    seamlessBlendMargin = blendMargin
                )
                _generatedState.value = updated
                if (_activeVariant.value == GenerationMode.AI_DEPTH) _aiDepthState.value = updated
                if (_activeVariant.value == GenerationMode.FAST) _fastState.value = updated
                _processingState.value = ProcessingState(isProcessing = false)
            } catch (e: Exception) {
                _processingState.value = ProcessingState(
                    isProcessing = false,
                    errorMessage = "Seamless adjustment error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun cancelGeneration() {
        processingJob?.cancel()
        _processingState.value = ProcessingState(
            isProcessing = false,
            errorMessage = "Generation cancelled by user."
        )
    }

    // Live tuning methods
    fun updateDetailBoost(boost: Float) {
        val current = _generatedState.value ?: return
        val baseDepth = current.baseDepthMap ?: return
        val highPass = current.highPassLuminance ?: return
        val res = current.resolution

        viewModelScope.launch {
            val blendedHeight = mapProcessor.blendHeightMap(
                depth = baseDepth,
                highPass = highPass,
                detailBoost = boost,
                width = res,
                height = res
            )

            val maskedHeight = if (current.exclusionMaskBitmap != null) {
                mapProcessor.applyExclusionMask(
                    heightMap = blendedHeight,
                    maskBitmap = current.exclusionMaskBitmap,
                    width = res,
                    height = res
                )
            } else {
                blendedHeight
            }

            val normalPixels = mapProcessor.generateNormalMap(
                heightMap = maskedHeight,
                width = res,
                height = res,
                strength = current.normalStrength,
                normalFormat = appSettings.value.normalFormat,
                invertHeight = current.invertHeight,
                scale1Weight = appSettings.value.normalScale1,
                scale2Weight = appSettings.value.normalScale2,
                scale3Weight = appSettings.value.normalScale3,
                scale4Weight = appSettings.value.normalScale4
            )

            val heightBitmap = mapProcessor.floatMapToGrayscaleBitmap(maskedHeight, res, res)
            val normalBitmap = mapProcessor.pixelArrayToBitmap(normalPixels, res, res)

            _generatedState.value = current.copy(
                detailBoost = boost,
                heightMap = maskedHeight,
                normalPixels = normalPixels,
                heightBitmap = heightBitmap,
                normalBitmap = normalBitmap
            )
        }
    }

    fun updateNormalStrength(strength: Float) {
        val current = _generatedState.value ?: return
        val heightMap = current.heightMap ?: return
        val res = current.resolution

        viewModelScope.launch {
            val normalPixels = mapProcessor.generateNormalMap(
                heightMap = heightMap,
                width = res,
                height = res,
                strength = strength,
                normalFormat = appSettings.value.normalFormat,
                invertHeight = current.invertHeight,
                scale1Weight = appSettings.value.normalScale1,
                scale2Weight = appSettings.value.normalScale2,
                scale3Weight = appSettings.value.normalScale3,
                scale4Weight = appSettings.value.normalScale4
            )

            val normalBitmap = mapProcessor.pixelArrayToBitmap(normalPixels, res, res)

            _generatedState.value = current.copy(
                normalStrength = strength,
                normalPixels = normalPixels,
                normalBitmap = normalBitmap
            )
        }
    }

    fun toggleInvertHeight() {
        val current = _generatedState.value ?: return
        val heightMap = current.heightMap ?: return
        val newInvert = !current.invertHeight
        val res = current.resolution

        viewModelScope.launch {
            val normalPixels = mapProcessor.generateNormalMap(
                heightMap = heightMap,
                width = res,
                height = res,
                strength = current.normalStrength,
                normalFormat = appSettings.value.normalFormat,
                invertHeight = newInvert,
                scale1Weight = appSettings.value.normalScale1,
                scale2Weight = appSettings.value.normalScale2,
                scale3Weight = appSettings.value.normalScale3,
                scale4Weight = appSettings.value.normalScale4
            )

            val normalBitmap = mapProcessor.pixelArrayToBitmap(normalPixels, res, res)

            _generatedState.value = current.copy(
                invertHeight = newInvert,
                normalPixels = normalPixels,
                normalBitmap = normalBitmap
            )
        }
    }

    fun toggleNormalFormat() {
        val currentFormat = appSettings.value.normalFormat
        val newFormat = if (currentFormat == NormalFormat.OPEN_GL) NormalFormat.DIRECT_X else NormalFormat.OPEN_GL
        viewModelScope.launch {
            preferencesRepository.updateNormalFormat(newFormat)
            val current = _generatedState.value ?: return@launch
            val heightMap = current.heightMap ?: return@launch
            val res = current.resolution

            val normalPixels = mapProcessor.generateNormalMap(
                heightMap = heightMap,
                width = res,
                height = res,
                strength = current.normalStrength,
                normalFormat = newFormat,
                invertHeight = current.invertHeight,
                scale1Weight = appSettings.value.normalScale1,
                scale2Weight = appSettings.value.normalScale2,
                scale3Weight = appSettings.value.normalScale3,
                scale4Weight = appSettings.value.normalScale4
            )

            val normalBitmap = mapProcessor.pixelArrayToBitmap(normalPixels, res, res)

            _generatedState.value = current.copy(
                normalPixels = normalPixels,
                normalBitmap = normalBitmap
            )
        }
    }

    fun updateRoughness(base: Float, contrast: Float) {
        val current = _generatedState.value ?: return
        val cropped = _imageState.value.croppedBitmap ?: return
        val res = current.resolution

        viewModelScope.launch {
            val roughness = mapProcessor.generateRoughnessMap(cropped, base, contrast)
            val bitmap = mapProcessor.floatMapToGrayscaleBitmap(roughness, res, res)
            _generatedState.value = current.copy(
                roughnessBase = base,
                roughnessContrast = contrast,
                roughnessMap = roughness,
                roughnessBitmap = bitmap
            )
        }
    }

    fun updateAo(radius: Int, intensity: Float) {
        val current = _generatedState.value ?: return
        val heightMap = current.heightMap ?: return
        val res = current.resolution

        viewModelScope.launch {
            val ao = mapProcessor.generateAoMap(heightMap, res, res, radius, intensity)
            val bitmap = mapProcessor.floatMapToGrayscaleBitmap(ao, res, res)
            _generatedState.value = current.copy(
                aoRadius = radius,
                aoIntensity = intensity,
                aoMap = ao,
                aoBitmap = bitmap
            )
        }
    }

    fun updateCurvature(contrast: Float) {
        val current = _generatedState.value ?: return
        val heightMap = current.heightMap ?: return
        val res = current.resolution

        viewModelScope.launch {
            val curvature = mapProcessor.generateCurvatureMap(heightMap, res, res, contrast)
            val bitmap = mapProcessor.floatMapToGrayscaleBitmap(curvature, res, res)
            _generatedState.value = current.copy(
                curvatureContrast = contrast,
                curvatureMap = curvature,
                curvatureBitmap = bitmap
            )
        }
    }

    fun updateMetallic(threshold: Float, boost: Float) {
        val current = _generatedState.value ?: return
        val cropped = _imageState.value.croppedBitmap ?: return
        val res = current.resolution

        viewModelScope.launch {
            val metallic = mapProcessor.generateMetallicMap(cropped, threshold, boost)
            val bitmap = mapProcessor.floatMapToGrayscaleBitmap(metallic, res, res)
            _generatedState.value = current.copy(
                metallicThreshold = threshold,
                metallicBoost = boost,
                metallicMap = metallic,
                metallicBitmap = bitmap
            )
        }
    }

    fun updateAlbedo(deLight: Float) {
        val current = _generatedState.value ?: return
        val cropped = _imageState.value.croppedBitmap ?: return
        val res = current.resolution

        viewModelScope.launch {
            val albedo = mapProcessor.generateAlbedoMap(cropped, deLight)
            val bitmap = mapProcessor.pixelArrayToBitmap(albedo, res, res)
            _generatedState.value = current.copy(
                deLightStrength = deLight,
                albedoPixels = albedo,
                albedoBitmap = bitmap
            )
        }
    }

    fun updateNoise(scale: Float, octaves: Int, intensity: Float, blendWeight: Float) {
        val current = _generatedState.value ?: return
        val res = current.resolution
        val baseHeight = current.baseDepthMap ?: return
        val highPass = current.highPassLuminance ?: return

        viewModelScope.launch {
            val noise = mapProcessor.generatePerlinNoiseMap(res, res, scale, octaves, intensity)
            val noiseBitmap = mapProcessor.floatMapToGrayscaleBitmap(noise, res, res)

            // If blendWeight > 0, blend noise into height & regenerate normal
            val blendedHeight = if (blendWeight > 0f) {
                val blendedBase = mapProcessor.blendHeightMap(baseHeight, highPass, current.detailBoost, res, res)
                mapProcessor.blendNoiseIntoHeight(blendedBase, noise, blendWeight)
            } else {
                mapProcessor.blendHeightMap(baseHeight, highPass, current.detailBoost, res, res)
            }

            val heightBitmap = mapProcessor.floatMapToGrayscaleBitmap(blendedHeight, res, res)

            val normalPixels = mapProcessor.generateNormalMap(
                heightMap = blendedHeight,
                width = res,
                height = res,
                strength = current.normalStrength,
                normalFormat = appSettings.value.normalFormat,
                invertHeight = current.invertHeight,
                scale1Weight = appSettings.value.normalScale1,
                scale2Weight = appSettings.value.normalScale2,
                scale3Weight = appSettings.value.normalScale3,
                scale4Weight = appSettings.value.normalScale4
            )
            val normalBitmap = mapProcessor.pixelArrayToBitmap(normalPixels, res, res)

            _generatedState.value = current.copy(
                noiseScale = scale,
                noiseOctaves = octaves,
                noiseIntensity = intensity,
                blendNoiseIntoHeight = blendWeight,
                noiseMap = noise,
                noiseBitmap = noiseBitmap,
                heightMap = blendedHeight,
                heightBitmap = heightBitmap,
                normalPixels = normalPixels,
                normalBitmap = normalBitmap
            )
        }
    }

    // Settings actions
    fun setQualityPreset(preset: QualityPreset) {
        viewModelScope.launch { preferencesRepository.updateQualityPreset(preset) }
    }

    fun setNormalFormat(format: NormalFormat) {
        viewModelScope.launch { preferencesRepository.updateNormalFormat(format) }
    }

    fun setEnginePreference(preference: EnginePreference) {
        viewModelScope.launch { preferencesRepository.updateEnginePreference(preference) }
    }

    fun setHeightBitDepth(depth: Int) {
        viewModelScope.launch { preferencesRepository.updateHeightBitDepth(depth) }
    }

    fun setTileSize(size: Int) {
        viewModelScope.launch { preferencesRepository.updateTileSize(size) }
    }

    fun setAoSamples(samples: Int) {
        viewModelScope.launch { preferencesRepository.updateAoSamples(samples) }
    }

    fun setNormalScales(s1: Float, s2: Float, s3: Float, s4: Float) {
        viewModelScope.launch { preferencesRepository.updateNormalScales(s1, s2, s3, s4) }
    }

    fun setExportFormat(format: ExportFormat) {
        viewModelScope.launch { preferencesRepository.updateExportFormat(format) }
    }

    fun setJpgQuality(quality: Int) {
        viewModelScope.launch { preferencesRepository.updateJpgQuality(quality) }
    }

    fun setUseNnapi(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateUseNnapi(enabled) }
    }

    // Room History Management
    fun toggleProjectFavorite(project: ProjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateFavoriteStatus(project.id, !project.isFavorite)
        }
    }

    fun deleteProjectFromHistory(project: ProjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.deleteProject(project)
        }
    }
}
