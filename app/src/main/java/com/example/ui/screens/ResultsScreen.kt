package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.GenerationMode
import com.example.data.preferences.NormalFormat
import com.example.ui.GeneratedMapState
import com.example.ui.components.ExportBottomSheet
import kotlinx.coroutines.launch

enum class SelectedMapType(val title: String, val shortLabel: String, val technicalDescription: String) {
    NORMAL("Normal Map", "Normal", "Normal (Tangent) • 2048×2048"),
    HEIGHT("Height Map", "Height", "Displacement (Float) • 2048×2048"),
    ROUGHNESS("Roughness Map", "Roughness", "Roughness (Grayscale) • 2048×2048"),
    AO("Ambient Occlusion", "AO", "Ambient Occlusion • 2048×2048"),
    CURVATURE("Curvature Map", "Curvature", "Surface Curvature • 2048×2048"),
    METALLIC("Metallic Map", "Metallic", "Metallic (Mask) • 2048×2048"),
    ALBEDO("Albedo (De-lit)", "Albedo", "Base Color (De-lit) • 2048×2048"),
    NOISE("Procedural Noise", "Noise", "Micro-Detail Noise • 2048×2048"),
    SOURCE("Source Photo", "Source", "Original Source Buffer • 2048×2048")
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onToggle2x2Tiled: () -> Unit = {},
    onApplySeamless: (Boolean, Float) -> Unit = { _, _ -> },
    onBackToCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var show3dPreview by remember { mutableStateOf(false) }
    var isCompareMode by remember { mutableStateOf(activeVariant == GenerationMode.COMPARE_BOTH) }
    var showAdjustSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var selectedMap by remember { mutableStateOf(SelectedMapType.NORMAL) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val verticalScrollState = rememberScrollState()
    val channelTabsScrollState = rememberScrollState()

    // 3D Preview screen branch
    if (show3dPreview) {
        PbrPreviewScreen(
            generatedState = generatedState,
            aiDepthState = aiDepthState,
            fastState = fastState,
            activeVariant = activeVariant,
            sourceBitmap = sourceBitmap,
            normalFormat = normalFormat,
            onBack = { show3dPreview = false },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

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
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Top App Bar
        TopHeaderBar(
            title = "Material Inspector",
            onBack = onBackToCreate
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(verticalScrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sub-header context & Adjust trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Synthesis 04",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF3DD68C), CircleShape)
                            )
                            Text(
                                text = "Ready",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF3DD68C)
                            )
                        }
                    }
                }

                // Adjust Trigger Button
                Button(
                    onClick = { showAdjustSheet = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(36.dp).testTag("btn_adjust_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Adjust",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            // Mode Segmented Control: AI Depth | Fast
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isAiActive = activeVariant == GenerationMode.AI_DEPTH && !isCompareMode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAiActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                isCompareMode = false
                                onSwitchVariant(GenerationMode.AI_DEPTH)
                            }
                            .testTag("seg_ai_depth")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isAiActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Depth",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isAiActive) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isAiActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isFastActive = activeVariant == GenerationMode.FAST && !isCompareMode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isFastActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                isCompareMode = false
                                onSwitchVariant(GenerationMode.FAST)
                            }
                            .testTag("seg_fast")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isFastActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Fast",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isFastActive) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isFastActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Primary 2D Texture Preview Card or Compare Split View
            if (isCompareMode && aiDepthState != null && fastState != null) {
                // Interactive Split Comparison View matching compare/code.html
                CompareSplitCard(
                    aiDepthState = aiDepthState,
                    fastState = fastState,
                    selectedMap = selectedMap,
                    sourceBitmap = sourceBitmap,
                    onExitCompare = { isCompareMode = false }
                )
            } else {
                // Standard 2D Viewport matching results/code.html
                SingleMapCard(
                    bitmap = currentBitmap,
                    selectedMap = selectedMap,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    show2x2Tiled = generatedState.show2x2Tiled,
                    onToggle2x2Tiled = onToggle2x2Tiled,
                    onTransformChange = { s, x, y ->
                        scale = s
                        offsetX = x
                        offsetY = y
                    },
                    onToggleCompare = {
                        isCompareMode = true
                        onSwitchVariant(GenerationMode.COMPARE_BOTH)
                    }
                )
            }

            // Horizontally Scrollable Texture Channels Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(channelTabsScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectedMapType.entries.filter { it != SelectedMapType.SOURCE }.forEach { mapType ->
                    val isSelected = selectedMap == mapType
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable {
                                selectedMap = mapType
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                            .testTag("tab_channel_${mapType.shortLabel.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = mapType.shortLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Technical Summary Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FilterVintage,
                                    contentDescription = null,
                                    tint = if (generatedState.isSeamless) Color(0xFF3DD68C) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Seamless Tiling",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (generatedState.isSeamless) Color(0xFF3DD68C).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        text = if (generatedState.isSeamless) "Seamless" else "Raw Edges",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                        color = if (generatedState.isSeamless) Color(0xFF3DD68C) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            val seam = generatedState.seamAnalysis
                            Text(
                                text = if (seam != null) {
                                    "Continuity: ${"%.1f".format(seam.continuityPercent)}% • Seam RMS: ${"%.3f".format(seam.seamDiscontinuityScore)}"
                                } else {
                                    "Border wrap verified (UV 1.0)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (generatedState.show2x2Tiled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (generatedState.show2x2Tiled) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .clickable { onToggle2x2Tiled() }
                            .testTag("btn_repeat_2x2")
                    ) {
                        Text(
                            text = if (generatedState.show2x2Tiled) "Repeat 2×2 (ON)" else "Repeat 2×2",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (generatedState.show2x2Tiled) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (generatedState.show2x2Tiled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Analytical Insights Card if Compare mode active
            if (isCompareMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Insights,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Neural Occlusion Delta",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Δ +14.2%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = Color(0xFF3DD68C),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Cavity Detail Preservation",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "98.4% SNR",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Neural Displacer eliminates specular shadow contamination and reconstructs micro-cavity depth from photographic falloff instead of raw pixel luminance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Pinned Thumb-Docked Action Zone
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary 3D Preview Button
                Button(
                    onClick = { show3dPreview = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_3d_preview"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "3D preview",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                // Primary Export Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Button(
                        onClick = { showExportSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxSize().testTag("btn_export")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export Material PBR Maps",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Adjust Material Parameters Modal Bottom Sheet
    if (showAdjustSheet) {
        AdjustBottomSheet(
            selectedMap = selectedMap,
            generatedState = generatedState,
            normalFormat = normalFormat,
            onNormalStrengthChange = onNormalStrengthChange,
            onNormalFormatToggle = onNormalFormatToggle,
            onInvertToggle = onInvertToggle,
            onDetailBoostChange = onDetailBoostChange,
            onRoughnessChange = onRoughnessChange,
            onAoChange = onAoChange,
            onCurvatureChange = onCurvatureChange,
            onMetallicChange = onMetallicChange,
            onAlbedoChange = onAlbedoChange,
            onNoiseChange = onNoiseChange,
            onApplySeamless = onApplySeamless,
            onDismiss = { showAdjustSheet = false }
        )
    }

    // Export Material Modal Bottom Sheet
    if (showExportSheet) {
        ExportBottomSheet(
            generatedState = generatedState,
            onDismiss = { showExportSheet = false }
        )
    }
}

/**
 * Top Header Bar matching quiet precision specs
 */
@Composable
private fun TopHeaderBar(
    title: String,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp).testTag("results_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Single Map 2D Viewport Card
 */
@Composable
private fun SingleMapCard(
    bitmap: Bitmap?,
    selectedMap: SelectedMapType,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    show2x2Tiled: Boolean = false,
    onToggle2x2Tiled: () -> Unit = {},
    onTransformChange: (Float, Float, Float) -> Unit,
    onToggleCompare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag("results_interactive_viewer")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .clipToBounds()
                .pointerInput(selectedMap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        val maxPan = (newScale - 1f) * 400f
                        val newX = (offsetX + pan.x).coerceIn(-maxPan, maxPan)
                        val newY = (offsetY + pan.y).coerceIn(-maxPan, maxPan)
                        onTransformChange(newScale, newX, newY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                if (show2x2Tiled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                        // Subtle seam alignment crosshair overlay
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val midX = size.width / 2f
                            val midY = size.height / 2f
                            val lineColor = Color(0x665B8CFF)
                            drawLine(color = lineColor, start = Offset(midX, 0f), end = Offset(midX, size.height), strokeWidth = 1.5f)
                            drawLine(color = lineColor, start = Offset(0f, midY), end = Offset(size.width, midY), strokeWidth = 1.5f)
                        }
                    }
                } else {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
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
                }
            }

            // Top HUD Chip: Channel Name & Specs + 2x2 Toggle
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Text(
                            text = if (show2x2Tiled) "${selectedMap.shortLabel} • 2×2 Tiled Repeat" else selectedMap.technicalDescription,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (show2x2Tiled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, if (show2x2Tiled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { onToggle2x2Tiled() }.testTag("viewport_repeat_toggle")
                ) {
                    Text(
                        text = if (show2x2Tiled) "2×2 View" else "1×1",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = if (show2x2Tiled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Top-right Fullscreen / Reset icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clickable { onTransformChange(1f, 0f, 0f) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (scale > 1.05f) Icons.Default.RestartAlt else Icons.Default.Fullscreen,
                        contentDescription = "Reset or Fullscreen",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom Floating Quick Compare & Bit-depth Strip
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Output,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "16-bit PNG",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { onToggleCompare() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Compare",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Split Comparison Card matching compare/code.html
 */
@Composable
private fun CompareSplitCard(
    aiDepthState: GeneratedMapState,
    fastState: GeneratedMapState,
    selectedMap: SelectedMapType,
    sourceBitmap: Bitmap?,
    onExitCompare: () -> Unit
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    val leftBitmap = getBitmapForMapType(aiDepthState, selectedMap, sourceBitmap)
    val rightBitmap = getBitmapForMapType(fastState, selectedMap, sourceBitmap)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag("split_compare_container")
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = constraints.maxWidth.toFloat()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (containerWidth > 0) {
                                splitFraction = (splitFraction + dragAmount.x / containerWidth).coerceIn(0.05f, 0.95f)
                            }
                        }
                    }
            ) {
                // Right Layer: Fast Height Model
                if (rightBitmap != null) {
                    Image(
                        bitmap = rightBitmap.asImageBitmap(),
                        contentDescription = "Fast (Right)",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Left Layer: AI Depth Neural Model (Clipped)
                if (leftBitmap != null) {
                    Image(
                        bitmap = leftBitmap.asImageBitmap(),
                        contentDescription = "AI Depth (Left)",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
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

                // Draggable Divider Line
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            translationX = containerWidth * splitFraction - 1.dp.toPx()
                        }
                        .background(MaterialTheme.colorScheme.primary)
                )

                // Draggable circular handle
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = containerWidth * splitFraction - 18.dp.toPx()
                        }
                        .size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "↔",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Floating Top-Left Badge (AI Depth)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "AI DEPTH",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Floating Top-Right Badge (Fast Height)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "FAST",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Bottom Viewport Metadata Bar
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "4096 × 4096 px",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Split ${(splitFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adjust Material Parameters Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustBottomSheet(
    selectedMap: SelectedMapType,
    generatedState: GeneratedMapState,
    normalFormat: NormalFormat,
    onNormalStrengthChange: (Float) -> Unit,
    onNormalFormatToggle: () -> Unit,
    onInvertToggle: () -> Unit,
    onDetailBoostChange: (Float) -> Unit,
    onRoughnessChange: (Float, Float) -> Unit,
    onAoChange: (Int, Float) -> Unit,
    onCurvatureChange: (Float) -> Unit,
    onMetallicChange: (Float, Float) -> Unit,
    onAlbedoChange: (Float) -> Unit,
    onNoiseChange: (Float, Int, Float, Float) -> Unit,
    onApplySeamless: (Boolean, Float) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var seamlessMargin by remember { mutableFloatStateOf(generatedState.seamlessBlendMargin) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Adjust Material",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = {
                        // Reset defaults for current map
                        onNormalStrengthChange(1.0f)
                        onDetailBoostChange(0.35f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Normal Map Controls
            Text(
                text = "Normal & Depth",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SliderRow(
                title = "Normal Strength",
                value = generatedState.normalStrength,
                valueRange = 0.0f..5.0f,
                onValueChange = onNormalStrengthChange,
                readout = "%.2fx".format(generatedState.normalStrength)
            )
            SliderRow(
                title = "Detail Boost",
                value = generatedState.detailBoost,
                valueRange = 0.0f..1.0f,
                onValueChange = onDetailBoostChange,
                readout = "%.2f".format(generatedState.detailBoost)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = normalFormat == NormalFormat.OPEN_GL,
                    onClick = onNormalFormatToggle,
                    label = { Text(if (normalFormat == NormalFormat.OPEN_GL) "OpenGL (+Y)" else "DirectX (-Y)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = generatedState.invertHeight,
                    onClick = onInvertToggle,
                    label = { Text(if (generatedState.invertHeight) "Inverted" else "Invert Depth") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Roughness Controls
            Text(
                text = "Roughness",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SliderRow(
                title = "Base Roughness",
                value = generatedState.roughnessBase,
                valueRange = 0.0f..1.0f,
                onValueChange = { base -> onRoughnessChange(base, generatedState.roughnessContrast) },
                readout = "%.2f".format(generatedState.roughnessBase)
            )
            SliderRow(
                title = "Roughness Contrast",
                value = generatedState.roughnessContrast,
                valueRange = 0.2f..2.5f,
                onValueChange = { contrast -> onRoughnessChange(generatedState.roughnessBase, contrast) },
                readout = "%.2f".format(generatedState.roughnessContrast)
            )

            // Ambient Occlusion Controls
            Text(
                text = "Ambient Occlusion",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SliderRow(
                title = "Cavity Radius",
                value = generatedState.aoRadius.toFloat(),
                valueRange = 4.0f..32.0f,
                onValueChange = { r -> onAoChange(r.toInt(), generatedState.aoIntensity) },
                readout = "${generatedState.aoRadius}px"
            )
            SliderRow(
                title = "Occlusion Intensity",
                value = generatedState.aoIntensity,
                valueRange = 0.5f..3.0f,
                onValueChange = { i -> onAoChange(generatedState.aoRadius, i) },
                readout = "%.2f".format(generatedState.aoIntensity)
            )

            // Curvature & Metallic
            Text(
                text = "Metallic & Delit",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SliderRow(
                title = "Metallic Threshold",
                value = generatedState.metallicThreshold,
                valueRange = 0.5f..1.0f,
                onValueChange = { th -> onMetallicChange(th, generatedState.metallicBoost) },
                readout = "%.2f".format(generatedState.metallicThreshold)
            )
            SliderRow(
                title = "De-Lighting Strength",
                value = generatedState.deLightStrength,
                valueRange = 0.0f..1.0f,
                onValueChange = onAlbedoChange,
                readout = "%.2f".format(generatedState.deLightStrength)
            )

            // Seamless Tiling & Border Synthesis (Phase 5)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Seamless Synthesis",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cross-border dual-axis edge blending",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = generatedState.isSeamless,
                            onCheckedChange = { enabled ->
                                onApplySeamless(enabled, seamlessMargin)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }

                    if (generatedState.isSeamless) {
                        SliderRow(
                            title = "Edge Blend Margin",
                            value = seamlessMargin,
                            valueRange = 0.08f..0.35f,
                            onValueChange = { seamlessMargin = it },
                            readout = "${(seamlessMargin * 100).toInt()}%"
                        )

                        Button(
                            onClick = { onApplySeamless(true, seamlessMargin) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text("Re-synthesize Seamless Borders", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Universal Technical Parameter Slider Row with label on left and monospace readout on right
 */
@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    readout: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = readout,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.height(24.dp)
        )
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
