package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppSettings
import com.example.data.preferences.EnginePreference
import com.example.data.preferences.ExportFormat
import com.example.data.preferences.NormalFormat
import com.example.data.preferences.QualityPreset
import com.example.data.preferences.UserPreferencesRepository
import com.example.util.ImageDecoderHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
    val isCropping: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = preferencesRepository.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _imageState = MutableStateFlow(ImageInputState())
    val imageState: StateFlow<ImageInputState> = _imageState.asStateFlow()

    private var pendingCameraUri: Uri? = null

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
            isCropping = false
        )
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
}
