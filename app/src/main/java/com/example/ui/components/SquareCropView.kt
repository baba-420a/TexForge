package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.util.ImageCropHelper
import kotlin.math.max
import kotlin.math.min

@Composable
fun SquareCropView(
    sourceBitmap: Bitmap,
    targetResolution: Int,
    onCropConfirmed: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Square Texture Crop",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Pan and pinch to position the 1:1 texture area (${sourceBitmap.width}×${sourceBitmap.height}px)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.testTag("crop_cancel_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Crop",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Viewport Frame (Square 1:1 Container)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val sideLengthDp = minOf(maxWidth, maxHeight)
            val sideLengthPx = with(density) { sideLengthDp.toPx() }

            // Maximum allowed pan based on current scale
            val maxPan = max(0f, (scale - 1f) * (sideLengthPx / 2f))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier
                    .size(sideLengthDp)
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("square_crop_viewport")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(sideLengthPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                val currentMaxPan = (scale - 1f) * (sideLengthPx / 2f)
                                offsetX = (offsetX + pan.x).coerceIn(-currentMaxPan, currentMaxPan)
                                offsetY = (offsetY + pan.y).coerceIn(-currentMaxPan, currentMaxPan)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Image layer with transformation
                    androidx.compose.foundation.Image(
                        bitmap = imageBitmap,
                        contentDescription = "Source texture image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )

                    // Rule of thirds & 1:1 overlay grid lines
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val guideColor = Color.White.copy(alpha = 0.35f)
                        val strokeWidth = 1.5.dp.toPx()

                        // Grid lines (3x3)
                        drawLine(guideColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
                        drawLine(guideColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth)
                        drawLine(guideColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
                        drawLine(guideColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth)

                        // Outer border highlight
                        drawRect(
                            color = Color.Cyan.copy(alpha = 0.7f),
                            topLeft = Offset.Zero,
                            size = Size(w, h),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Zoom slider & Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )

                Slider(
                    value = scale,
                    onValueChange = { newScale ->
                        scale = newScale
                        // Reclamp offsets
                        val sidePx = 800f
                        val currentMaxPan = (scale - 1f) * (sidePx / 2f)
                        offsetX = offsetX.coerceIn(-currentMaxPan, currentMaxPan)
                        offsetY = offsetY.coerceIn(-currentMaxPan, currentMaxPan)
                    },
                    valueRange = 1f..4f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crop_zoom_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                FilledTonalButton(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("crop_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("crop_cancel_action_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        // Compute crop rectangle on source bitmap and scale
                        val cropRect = ImageCropHelper.calculateCropRect(
                            imageWidth = sourceBitmap.width,
                            imageHeight = sourceBitmap.height,
                            viewportSize = 800f,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY
                        )
                        val cropped = ImageCropHelper.cropAndScale(
                            source = sourceBitmap,
                            cropRect = cropRect,
                            targetSize = targetResolution
                        )
                        onCropConfirmed(cropped)
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp)
                        .testTag("crop_confirm_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm 1:1 Crop",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
