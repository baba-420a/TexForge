package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.example.data.preferences.GenerationMode
import com.example.data.preferences.NormalFormat
import com.example.ui.GeneratedMapState

enum class SelectedMapType(val title: String, val shortLabel: String) {
    NORMAL("Normal Map", "Normal"),
    HEIGHT("Height Map", "Height"),
    ROUGHNESS("Roughness Map", "Roughness"),
    AO("Ambient Occlusion", "AO"),
    CURVATURE("Curvature Map", "Curvature"),
    METALLIC("Metallic Map", "Metallic"),
    ALBEDO("Albedo (De-lit)", "Albedo"),
    NOISE("Procedural Noise", "Noise"),
    SOURCE("Source Photo", "Source")
}

@Composable
fun ResultsScreen(
    generatedState: GeneratedMapState,
    aiDepthState: GeneratedMapState? = null,
    fastState: GeneratedMapState? = null,
    activeVariant: GenerationMode = GenerationMode.AI_DEPTH,
    sourceBitmap: Bitmap?,
    normalFormat: NormalFormat,
    onDetailBoostChange: (Float) -> Unit,
    onNormalStrengthChange: (Float) -> Unit,
    onInvertToggle: () -> Unit,
    onNormalFormatToggle: () -> Unit,
    onRoughnessChange: (Float, Float) -> Unit,
    onAoChange: (Int, Float) -> Unit,
    onCurvatureChange: (Float) -> Unit,
    onMetallicChange: (Float, Float) -> Unit,
    onAlbedoChange: (Float) -> Unit,
    onNoiseChange: (Float, Int, Float, Float) -> Unit,
    onSwitchVariant: (GenerationMode) -> Unit = {},
    onBackToCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMap by remember { mutableStateOf(SelectedMapType.NORMAL) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val verticalScrollState = rememberScrollState()
    val thumbScrollState = rememberScrollState()

    val currentBitmap: Bitmap? = when (selectedMap) {
        SelectedMapType.NORMAL -> generatedState.normalBitmap
        SelectedMapType.HEIGHT -> generatedState.heightBitmap
        SelectedMapType.ROUGHNESS -> generatedState.roughnessBitmap
        SelectedMapType.AO -> generatedState.aoBitmap
        SelectedMapType.CURVATURE -> generatedState.curvatureBitmap
        SelectedMapType.METALLIC -> generatedState.metallicBitmap
        SelectedMapType.ALBEDO -> generatedState.albedoBitmap
        SelectedMapType.NOISE -> generatedState.noiseBitmap
        SelectedMapType.SOURCE -> sourceBitmap
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackToCreate,
                    modifier = Modifier.testTag("results_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Editor",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "PBR Texture Forge",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${generatedState.resolution} × ${generatedState.resolution} px • 8 PBR Maps Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = selectedMap.title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Variant Selection Filter Chips (if both variants exist)
        if (aiDepthState != null && fastState != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Variant:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                FilterChip(
                    selected = activeVariant == GenerationMode.AI_DEPTH,
                    onClick = { onSwitchVariant(GenerationMode.AI_DEPTH) },
                    label = { Text("AI Depth") },
                    modifier = Modifier.testTag("chip_variant_ai_depth")
                )
                FilterChip(
                    selected = activeVariant == GenerationMode.FAST,
                    onClick = { onSwitchVariant(GenerationMode.FAST) },
                    label = { Text("Fast (No AI)") },
                    modifier = Modifier.testTag("chip_variant_fast")
                )
                FilterChip(
                    selected = activeVariant == GenerationMode.COMPARE_BOTH,
                    onClick = { onSwitchVariant(GenerationMode.COMPARE_BOTH) },
                    label = { Text("Compare Both (Split)") },
                    modifier = Modifier.testTag("chip_variant_compare")
                )
            }
        }

        // Main Viewer with Interactive Pan, Pinch-Zoom, and Split Comparison
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clipToBounds()
                .pointerInput(selectedMap, activeVariant) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        val maxPan = (scale - 1f) * 400f
                        offsetX = (offsetX + pan.x).coerceIn(-maxPan, maxPan)
                        offsetY = (offsetY + pan.y).coerceIn(-maxPan, maxPan)
                    }
                }
                .testTag("results_interactive_viewer"),
            contentAlignment = Alignment.Center
        ) {
            val isComparing = activeVariant == GenerationMode.COMPARE_BOTH && aiDepthState != null && fastState != null && selectedMap != SelectedMapType.SOURCE

            if (isComparing) {
                val leftBitmap = getBitmapForMapType(aiDepthState, selectedMap, sourceBitmap)
                val rightBitmap = getBitmapForMapType(fastState, selectedMap, sourceBitmap)

                SplitCompareViewer(
                    leftBitmap = leftBitmap,
                    rightBitmap = rightBitmap,
                    contentDescription = selectedMap.title,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY
                )
            } else if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = selectedMap.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )
            } else {
                Text(
                    text = "Generating map...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Zoom reset HUD in top-right corner of viewer
            if (scale > 1.05f) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clickable {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Zoom",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "%.1fx Reset".format(scale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Bottom Controls and Thumbnails Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(verticalScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Horizontally scrollable strip of all 8 Maps + Source
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(thumbScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapThumbnailCard(
                    title = "Normal",
                    bitmap = generatedState.normalBitmap,
                    isSelected = selectedMap == SelectedMapType.NORMAL,
                    onClick = { selectedMap = SelectedMapType.NORMAL; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_normal"
                )
                MapThumbnailCard(
                    title = "Height",
                    bitmap = generatedState.heightBitmap,
                    isSelected = selectedMap == SelectedMapType.HEIGHT,
                    onClick = { selectedMap = SelectedMapType.HEIGHT; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_height"
                )
                MapThumbnailCard(
                    title = "Roughness",
                    bitmap = generatedState.roughnessBitmap,
                    isSelected = selectedMap == SelectedMapType.ROUGHNESS,
                    onClick = { selectedMap = SelectedMapType.ROUGHNESS; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_roughness"
                )
                MapThumbnailCard(
                    title = "AO",
                    bitmap = generatedState.aoBitmap,
                    isSelected = selectedMap == SelectedMapType.AO,
                    onClick = { selectedMap = SelectedMapType.AO; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_ao"
                )
                MapThumbnailCard(
                    title = "Curvature",
                    bitmap = generatedState.curvatureBitmap,
                    isSelected = selectedMap == SelectedMapType.CURVATURE,
                    onClick = { selectedMap = SelectedMapType.CURVATURE; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_curvature"
                )
                MapThumbnailCard(
                    title = "Metallic",
                    bitmap = generatedState.metallicBitmap,
                    isSelected = selectedMap == SelectedMapType.METALLIC,
                    onClick = { selectedMap = SelectedMapType.METALLIC; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_metallic"
                )
                MapThumbnailCard(
                    title = "Albedo",
                    bitmap = generatedState.albedoBitmap,
                    isSelected = selectedMap == SelectedMapType.ALBEDO,
                    onClick = { selectedMap = SelectedMapType.ALBEDO; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_albedo"
                )
                MapThumbnailCard(
                    title = "Noise",
                    bitmap = generatedState.noiseBitmap,
                    isSelected = selectedMap == SelectedMapType.NOISE,
                    onClick = { selectedMap = SelectedMapType.NOISE; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_noise"
                )
                MapThumbnailCard(
                    title = "Source",
                    bitmap = sourceBitmap,
                    isSelected = selectedMap == SelectedMapType.SOURCE,
                    onClick = { selectedMap = SelectedMapType.SOURCE; scale = 1f; offsetX = 0f; offsetY = 0f },
                    tag = "thumb_source"
                )
            }

            // Dynamic Slider Panel adapting to the selected map
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth().testTag("map_tuning_panel")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${selectedMap.title} Parameters",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    when (selectedMap) {
                        SelectedMapType.NORMAL -> {
                            MapSliderRow(
                                title = "Normal Strength",
                                value = generatedState.normalStrength,
                                valueRange = 0.0f..5.0f,
                                onValueChange = onNormalStrengthChange,
                                tag = "slider_normal_strength"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = normalFormat == NormalFormat.OPEN_GL,
                                    onClick = onNormalFormatToggle,
                                    label = { Text(if (normalFormat == NormalFormat.OPEN_GL) "OpenGL (+Y Up)" else "DirectX (-Y Down)") },
                                    modifier = Modifier.weight(1f).testTag("chip_normal_format_toggle")
                                )
                                FilterChip(
                                    selected = generatedState.invertHeight,
                                    onClick = onInvertToggle,
                                    label = { Text(if (generatedState.invertHeight) "Inverted" else "Invert Slopes") },
                                    modifier = Modifier.weight(1f).testTag("chip_invert_height_toggle")
                                )
                            }
                        }

                        SelectedMapType.HEIGHT -> {
                            MapSliderRow(
                                title = "Detail Boost (Luminance High-pass)",
                                value = generatedState.detailBoost,
                                valueRange = 0.0f..1.0f,
                                onValueChange = onDetailBoostChange,
                                tag = "slider_detail_boost"
                            )
                            MapSliderRow(
                                title = "Procedural Noise Blend into Height",
                                value = generatedState.blendNoiseIntoHeight,
                                valueRange = 0.0f..1.0f,
                                onValueChange = { weight ->
                                    onNoiseChange(
                                        generatedState.noiseScale,
                                        generatedState.noiseOctaves,
                                        generatedState.noiseIntensity,
                                        weight
                                    )
                                },
                                tag = "slider_noise_blend_height"
                            )
                        }

                        SelectedMapType.ROUGHNESS -> {
                            MapSliderRow(
                                title = "Base Roughness (Offset)",
                                value = generatedState.roughnessBase,
                                valueRange = 0.0f..1.0f,
                                onValueChange = { base ->
                                    onRoughnessChange(base, generatedState.roughnessContrast)
                                },
                                tag = "slider_roughness_base"
                            )
                            MapSliderRow(
                                title = "Micro-Texture Contrast (Variance)",
                                value = generatedState.roughnessContrast,
                                valueRange = 0.2f..2.5f,
                                onValueChange = { contrast ->
                                    onRoughnessChange(generatedState.roughnessBase, contrast)
                                },
                                tag = "slider_roughness_contrast"
                            )
                        }

                        SelectedMapType.AO -> {
                            MapSliderRow(
                                title = "Cavity Detection Radius",
                                value = generatedState.aoRadius.toFloat(),
                                valueRange = 4.0f..32.0f,
                                onValueChange = { r ->
                                    onAoChange(r.toInt(), generatedState.aoIntensity)
                                },
                                tag = "slider_ao_radius"
                            )
                            MapSliderRow(
                                title = "Occlusion Intensity",
                                value = generatedState.aoIntensity,
                                valueRange = 0.5f..3.0f,
                                onValueChange = { inten ->
                                    onAoChange(generatedState.aoRadius, inten)
                                },
                                tag = "slider_ao_intensity"
                            )
                        }

                        SelectedMapType.CURVATURE -> {
                            MapSliderRow(
                                title = "Curvature Ridge Contrast",
                                value = generatedState.curvatureContrast,
                                valueRange = 0.5f..3.5f,
                                onValueChange = onCurvatureChange,
                                tag = "slider_curvature_contrast"
                            )
                            Text(
                                text = "Convex surface peaks appear bright; concave crevices appear dark; flat planes are neutral 0.5 gray.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SelectedMapType.METALLIC -> {
                            MapSliderRow(
                                title = "Metallic Specular Threshold",
                                value = generatedState.metallicThreshold,
                                valueRange = 0.5f..1.0f,
                                onValueChange = { thresh ->
                                    onMetallicChange(thresh, generatedState.metallicBoost)
                                },
                                tag = "slider_metallic_threshold"
                            )
                            MapSliderRow(
                                title = "Metallic Boost",
                                value = generatedState.metallicBoost,
                                valueRange = 0.5f..2.5f,
                                onValueChange = { boost ->
                                    onMetallicChange(generatedState.metallicThreshold, boost)
                                },
                                tag = "slider_metallic_boost"
                            )
                            Text(
                                text = "Dielectric materials (wood, stone, dirt) are completely black (0.0). Only specular metallic reflections are isolated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SelectedMapType.ALBEDO -> {
                            MapSliderRow(
                                title = "De-Lighting Strength",
                                value = generatedState.deLightStrength,
                                valueRange = 0.0f..1.0f,
                                onValueChange = onAlbedoChange,
                                tag = "slider_albedo_delight"
                            )
                            Text(
                                text = "Eliminates baked shadows and directional light gradients by dividing the photo by heavily blurred surface luminance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SelectedMapType.NOISE -> {
                            MapSliderRow(
                                title = "Frequency Scale",
                                value = generatedState.noiseScale,
                                valueRange = 2.0f..32.0f,
                                onValueChange = { s ->
                                    onNoiseChange(s, generatedState.noiseOctaves, generatedState.noiseIntensity, generatedState.blendNoiseIntoHeight)
                                },
                                tag = "slider_noise_scale"
                            )
                            MapSliderRow(
                                title = "Fractal Octaves",
                                value = generatedState.noiseOctaves.toFloat(),
                                valueRange = 1.0f..6.0f,
                                onValueChange = { o ->
                                    onNoiseChange(generatedState.noiseScale, o.toInt(), generatedState.noiseIntensity, generatedState.blendNoiseIntoHeight)
                                },
                                tag = "slider_noise_octaves"
                            )
                            MapSliderRow(
                                title = "Noise Intensity",
                                value = generatedState.noiseIntensity,
                                valueRange = 0.2f..2.0f,
                                onValueChange = { i ->
                                    onNoiseChange(generatedState.noiseScale, generatedState.noiseOctaves, i, generatedState.blendNoiseIntoHeight)
                                },
                                tag = "slider_noise_intensity"
                            )
                            MapSliderRow(
                                title = "Blend Weight into Height Map",
                                value = generatedState.blendNoiseIntoHeight,
                                valueRange = 0.0f..1.0f,
                                onValueChange = { w ->
                                    onNoiseChange(generatedState.noiseScale, generatedState.noiseOctaves, generatedState.noiseIntensity, w)
                                },
                                tag = "slider_noise_blend"
                            )
                        }

                        SelectedMapType.SOURCE -> {
                            Text(
                                text = "Original cropped 1:1 photograph used as the primary source for all PBR map extractions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Buttons for Later Phases (Visibly disabled and labeled Coming soon as strictly required)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Disabled in Phase 3 */ },
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_3d_preview"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("3D Sphere (Phase 4)")
                }

                Button(
                    onClick = { /* Disabled in Phase 3 */ },
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export (Phase 7)")
                }
            }
        }
    }
}

@Composable
private fun MapSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.testTag(tag),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun MapThumbnailCard(
    title: String,
    bitmap: Bitmap?,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .width(84.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SplitCompareViewer(
    leftBitmap: Bitmap?,
    rightBitmap: Bitmap?,
    contentDescription: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (containerWidth > 0) {
                            splitFraction = (splitFraction + dragAmount.x / containerWidth).coerceIn(0.05f, 0.95f)
                        }
                    }
                }
        ) {
            // Right bitmap (Fast) on background
            if (rightBitmap != null) {
                Image(
                    bitmap = rightBitmap.asImageBitmap(),
                    contentDescription = "$contentDescription Right (Fast)",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )
            }

            // Left bitmap (AI Depth) clipped to splitFraction
            if (leftBitmap != null) {
                Image(
                    bitmap = leftBitmap.asImageBitmap(),
                    contentDescription = "$contentDescription Left (AI Depth)",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                            clip = true
                            shape = androidx.compose.ui.graphics.RectangleShape
                        }
                        .drawWithContent {
                            drawIntoCanvas { canvas ->
                                canvas.save()
                                canvas.clipRect(0f, 0f, size.width * splitFraction, size.height)
                                this@drawWithContent.drawContent()
                                canvas.restore()
                            }
                        }
                )
            }

            // Vertical divider handle line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = containerWidth * splitFraction - 1.5.dp.toPx()
                    }
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Handle badge button in center
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = containerWidth * splitFraction - 16.dp.toPx()
                    }
                    .size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "↔",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Overlay Badges
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "AI Depth (Left)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Fast (Right)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private fun getBitmapForMapType(state: GeneratedMapState?, type: SelectedMapType, source: Bitmap?): Bitmap? {
    if (state == null) return null
    return when (type) {
        SelectedMapType.NORMAL -> state.normalBitmap
        SelectedMapType.HEIGHT -> state.heightBitmap
        SelectedMapType.ROUGHNESS -> state.roughnessBitmap
        SelectedMapType.AO -> state.aoBitmap
        SelectedMapType.CURVATURE -> state.curvatureBitmap
        SelectedMapType.METALLIC -> state.metallicBitmap
        SelectedMapType.ALBEDO -> state.albedoBitmap
        SelectedMapType.NOISE -> state.noiseBitmap
        SelectedMapType.SOURCE -> source
    }
}
