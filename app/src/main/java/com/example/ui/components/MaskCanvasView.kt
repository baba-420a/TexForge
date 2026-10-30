package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class MaskTool {
    BRUSH,
    ERASE
}

data class MaskStroke(
    val points: List<Offset>,
    val strokeWidth: Float,
    val tool: MaskTool
)

/**
 * Interactive Material Inspector & Blemish Exclusion Mask canvas.
 * Implements interactive touch painting overlay, brush size slider with presets,
 * and real-time blemish exclusion rendering matching mask/code.html.
 */
@Composable
fun MaskCanvasView(
    sourceBitmap: Bitmap,
    initialMask: Bitmap? = null,
    onMaskUpdated: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTool by remember { mutableStateOf(MaskTool.BRUSH) }
    var brushSizeDp by remember { mutableFloatStateOf(24f) }
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }
    val strokes = remember { mutableStateListOf<MaskStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val density = LocalDensity.current
    val brushColor = Color(0x73FF6B6B) // #FF6B6B at 45% opacity

    // Helper to generate a mask Bitmap from completed strokes
    fun generateMaskBitmap(targetSize: Int): Bitmap? {
        if (strokes.isEmpty()) return null
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)

        val brushPaint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            color = android.graphics.Color.argb(0x73, 0xFF, 0x6B, 0x6B)
        }

        val erasePaint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        for (stroke in strokes) {
            if (stroke.points.size < 2) {
                if (stroke.points.size == 1) {
                    val p = stroke.points[0]
                    val paint = if (stroke.tool == MaskTool.BRUSH) brushPaint else erasePaint
                    paint.strokeWidth = stroke.strokeWidth
                    canvas.drawPoint(p.x, p.y, paint)
                }
                continue
            }
            val path = AndroidPath()
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                val pt = stroke.points[i]
                path.lineTo(pt.x, pt.y)
            }
            val paint = if (stroke.tool == MaskTool.BRUSH) brushPaint else erasePaint
            paint.strokeWidth = stroke.strokeWidth
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1:1 Aspect Ratio Interactive Viewport Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .testTag("mask_canvas_viewport")
        ) {
            val canvasWidthPx = constraints.maxWidth.toFloat()
            val canvasHeightPx = constraints.maxHeight.toFloat()
            val strokeWidthPx = with(density) { brushSizeDp.dp.toPx() }

            // 1. Base cropped texture image
            Image(
                bitmap = sourceBitmap.asImageBitmap(),
                contentDescription = "Base texture preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Interactive Mask Painting Layer (Offscreen compositing allows erase to clear red mask without touching image)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .pointerInput(currentTool, brushSizeDp) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                cursorPosition = offset
                                currentPoints = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                cursorPosition = change.position
                                currentPoints = currentPoints + change.position
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    val newStroke = MaskStroke(
                                        points = currentPoints,
                                        strokeWidth = strokeWidthPx,
                                        tool = currentTool
                                    )
                                    strokes.add(newStroke)
                                    currentPoints = emptyList()
                                    // Notify mask updated
                                    onMaskUpdated(generateMaskBitmap(sourceBitmap.width))
                                }
                                cursorPosition = null
                            },
                            onDragCancel = {
                                currentPoints = emptyList()
                                cursorPosition = null
                            }
                        )
                    }
            ) {
                // Render completed strokes
                for (stroke in strokes) {
                    drawMaskStroke(stroke, brushColor)
                }
                // Render currently active drag stroke
                if (currentPoints.isNotEmpty()) {
                    drawMaskStroke(
                        MaskStroke(
                            points = currentPoints,
                            strokeWidth = strokeWidthPx,
                            tool = currentTool
                        ),
                        brushColor
                    )
                }
            }

            // 3. Interactive Brush Cursor Ring (follows finger during dragging)
            cursorPosition?.let { pos ->
                val cursorSizeDp = brushSizeDp.dp
                val halfSizePx = with(density) { (brushSizeDp / 2f).dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pos.x - halfSizePx).roundToInt(),
                                (pos.y - halfSizePx).roundToInt()
                            )
                        }
                        .size(cursorSizeDp)
                        .clip(CircleShape)
                        .background(brushColor)
                        .padding(1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.Transparent)
                    )
                }
            }

            // 4. Viewport HUD Overlay Chips (Matches mask/code.html lines 34-42)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left HUD Chip: Red dot + "Blemish Exclusion"
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Text(
                            text = "Blemish Exclusion",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Right HUD Chip: "1:1 PBR"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.height(28.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "1:1 PBR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Primary Editing Controls & Tool Palette (Matches mask/code.html lines 45-67)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brush Tool Button
            val isBrush = currentTool == MaskTool.BRUSH
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isBrush) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isBrush) Color.Transparent else MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .height(36.dp)
                    .clickable { currentTool = MaskTool.BRUSH }
                    .testTag("tool_brush")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = "Brush",
                        tint = if (isBrush) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Brush",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isBrush) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Erase Tool Button
            val isErase = currentTool == MaskTool.ERASE
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isErase) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isErase) Color.Transparent else MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .height(36.dp)
                    .clickable { currentTool = MaskTool.ERASE }
                    .testTag("tool_erase")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixNormal,
                        contentDescription = "Erase",
                        tint = if (isErase) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Erase",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isErase) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Undo Tool Button
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .height(36.dp)
                    .clickable(enabled = strokes.isNotEmpty()) {
                        if (strokes.isNotEmpty()) {
                            strokes.removeAt(strokes.size - 1)
                            onMaskUpdated(generateMaskBitmap(sourceBitmap.width))
                        }
                    }
                    .testTag("tool_undo")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Clear All Button
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .height(36.dp)
                    .clickable(enabled = strokes.isNotEmpty()) {
                        strokes.clear()
                        currentPoints = emptyList()
                        onMaskUpdated(null)
                    }
                    .testTag("tool_clear")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear",
                        tint = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Parameter Slider Section: Brush Size (Matches mask/code.html lines 68-87)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            imageVector = Icons.Default.LineWeight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Brush size",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${brushSizeDp.roundToInt()} px",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Slider(
                    value = brushSizeDp,
                    onValueChange = { brushSizeDp = it },
                    valueRange = 8f..64f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .testTag("brush_size_slider")
                )

                // Quick Presets: Fine (12px), Medium (24px), Coarse (48px)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Fine (12px)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (brushSizeDp == 12f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { brushSizeDp = 12f }
                            .padding(vertical = 4.dp)
                            .testTag("preset_fine")
                    )
                    Text(
                        text = "Medium (24px)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (brushSizeDp == 24f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { brushSizeDp = 24f }
                            .padding(vertical = 4.dp)
                            .testTag("preset_medium")
                    )
                    Text(
                        text = "Coarse (48px)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (brushSizeDp == 48f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { brushSizeDp = 48f }
                            .padding(vertical = 4.dp)
                            .testTag("preset_coarse")
                    )
                }
            }
        }

        // Quick Tip & Contextual Insight (Matches mask/code.html lines 88-94)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Mask deep shadows or unnatural voids to prevent depth warping during height map synthesis.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Draws a single mask stroke onto the Compose DrawScope.
 */
private fun DrawScope.drawMaskStroke(stroke: MaskStroke, brushColor: Color) {
    if (stroke.points.size < 2) {
        if (stroke.points.size == 1) {
            val p = stroke.points[0]
            drawCircle(
                color = if (stroke.tool == MaskTool.BRUSH) brushColor else Color.Transparent,
                radius = stroke.strokeWidth / 2f,
                center = p,
                blendMode = if (stroke.tool == MaskTool.BRUSH) BlendMode.SrcOver else BlendMode.Clear
            )
        }
        return
    }

    val path = Path().apply {
        moveTo(stroke.points[0].x, stroke.points[0].y)
        for (i in 1 until stroke.points.size) {
            lineTo(stroke.points[i].x, stroke.points[i].y)
        }
    }

    drawPath(
        path = path,
        color = if (stroke.tool == MaskTool.BRUSH) brushColor else Color.Transparent,
        style = Stroke(
            width = stroke.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = if (stroke.tool == MaskTool.BRUSH) BlendMode.SrcOver else BlendMode.Clear
    )
}
