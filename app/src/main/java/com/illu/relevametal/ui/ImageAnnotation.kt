package com.illu.relevametal.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.illu.relevametal.annotation.AnnotationTool
import com.illu.relevametal.annotation.DetectionBox
import com.illu.relevametal.annotation.MarkupAnnotation
import com.illu.relevametal.annotation.MeasurementAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberPhotoBitmap(
    path: String,
    maxSide: Int = 1800,
    rotationDegrees: Int = 0
): State<ImageBitmap?> = produceState<ImageBitmap?>(null, path, maxSide, rotationDegrees) {
    value = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > maxSide * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        val source = BitmapFactory.decodeFile(path, options) ?: return@withContext null
        val rotation = ((rotationDegrees % 360) + 360) % 360
        val displayBitmap = if (rotation == 0) {
            source
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
                if (it !== source) source.recycle()
            }
        }
        displayBitmap.asImageBitmap()
    }
}

@Composable
fun rememberPhotoDimensions(path: String): State<IntSize?> = produceState<IntSize?>(null, path) {
    value = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) IntSize(bounds.outWidth, bounds.outHeight) else null
    }
}

data class PhotoStampUi(
    val companyName: String,
    val lines: List<String>,
    val logo: ImageBitmap? = null,
    val enabled: Boolean = true
)

private data class FitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

@Composable
fun AnnotatedPhoto(
    bitmap: ImageBitmap,
    detections: List<DetectionBox>,
    measurements: List<MeasurementAnnotation>,
    markups: List<MarkupAnnotation>,
    activeTool: AnnotationTool,
    firstPoint: Offset?,
    onFirstPoint: (Offset) -> Unit,
    onPair: (Offset, Offset) -> Unit,
    onSinglePoint: (Offset) -> Unit,
    stamp: PhotoStampUi? = null,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    val fit = remember(size, bitmap) {
        if (size.width == 0 || size.height == 0) {
            FitRect(0f, 0f, 0f, 0f)
        } else {
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            val cw = size.width.toFloat()
            val ch = size.height.toFloat()
            val scale = minOf(cw / iw, ch / ih)
            val w = iw * scale
            val h = ih * scale
            FitRect((cw - w) / 2f, (ch - h) / 2f, w, h)
        }
    }

    Box(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(activeTool, fit, firstPoint) {
                detectTapGestures { tap ->
                    if (activeTool == AnnotationTool.NONE || fit.width <= 0f || fit.height <= 0f) return@detectTapGestures
                    if (tap.x < fit.left || tap.x > fit.left + fit.width) return@detectTapGestures
                    if (tap.y < fit.top || tap.y > fit.top + fit.height) return@detectTapGestures

                    val normalized = Offset(
                        x = ((tap.x - fit.left) / fit.width).coerceIn(0f, 1f),
                        y = ((tap.y - fit.top) / fit.height).coerceIn(0f, 1f)
                    )
                    if (activeTool == AnnotationTool.TEXT) {
                        onSinglePoint(normalized)
                    } else {
                        val start = firstPoint
                        if (start == null) onFirstPoint(normalized) else onPair(start, normalized)
                    }
                }
            }
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Fotografía de relevamiento",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(Modifier.fillMaxSize()) {
            fun map(p: Offset) = Offset(fit.left + p.x * fit.width, fit.top + p.y * fit.height)

            detections.forEach { box ->
                drawRect(
                    color = Color(0xFFFF9800),
                    topLeft = Offset(fit.left + box.left * fit.width, fit.top + box.top * fit.height),
                    size = Size((box.right - box.left) * fit.width, (box.bottom - box.top) * fit.height),
                    style = Stroke(width = 3f)
                )
            }

            measurements.forEach { m ->
                val a = map(Offset(m.x1, m.y1))
                val b = map(Offset(m.x2, m.y2))
                val lineColor = Color(0xFF00D4FF)
                drawLine(lineColor, a, b, strokeWidth = 5f)
                drawCircle(lineColor, radius = 7f, center = a)
                drawCircle(lineColor, radius = 7f, center = b)
                drawDoubleArrow(a, b, lineColor)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 32f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
                }
                val text = listOf(m.label, m.value).filter { it.isNotBlank() }.joinToString(" · ")
                drawContext.canvas.nativeCanvas.drawText(text, (a.x + b.x) / 2f + 10f, (a.y + b.y) / 2f - 10f, paint)
            }

            markups.forEach { markup ->
                val a = map(Offset(markup.x1, markup.y1))
                val b = map(Offset(markup.x2, markup.y2))
                val markupColor = Color(0xFFFFD54F)
                when (markup.type) {
                    "ARROW" -> {
                        drawLine(markupColor, a, b, 5f)
                        drawArrowHead(a, b, markupColor)
                    }
                    "RECTANGLE" -> {
                        drawRect(
                            markupColor,
                            topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                            size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)),
                            style = Stroke(5f)
                        )
                    }
                    "CIRCLE" -> {
                        drawOval(
                            markupColor,
                            topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                            size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)),
                            style = Stroke(5f)
                        )
                    }
                    "TEXT" -> {
                        drawCircle(Color(0xFFD32F2F), radius = 14f, center = a)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.color = android.graphics.Color.WHITE
                            textSize = 31f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
                        }
                        drawContext.canvas.nativeCanvas.drawText(markup.text, a.x + 20f, a.y - 12f, paint)
                    }
                }
            }

            firstPoint?.let {
                if (activeTool != AnnotationTool.NONE && activeTool != AnnotationTool.TEXT) {
                    drawCircle(Color(0xFFFFFF00), radius = 12f, center = map(it))
                }
            }
        }

        stamp?.takeIf { it.enabled }?.let { PhotoStampOverlay(it) }
    }
}

@Composable
private fun BoxScope.PhotoStampOverlay(stamp: PhotoStampUi) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp)
            .background(Color(0xB8000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        stamp.logo?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                contentScale = ContentScale.Fit
            )
        }
        Column {
            Text(
                stamp.companyName,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            stamp.lines.take(4).forEach { line ->
                if (line.isNotBlank()) {
                    Text(line, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoubleArrow(a: Offset, b: Offset, color: Color) {
    val angle = atan2(b.y - a.y, b.x - a.x)
    val arrow = 18f
    val spread = 0.55f
    drawLine(color, a, Offset(a.x + cos(angle + spread) * arrow, a.y + sin(angle + spread) * arrow), 4f)
    drawLine(color, a, Offset(a.x + cos(angle - spread) * arrow, a.y + sin(angle - spread) * arrow), 4f)
    drawLine(color, b, Offset(b.x - cos(angle + spread) * arrow, b.y - sin(angle + spread) * arrow), 4f)
    drawLine(color, b, Offset(b.x - cos(angle - spread) * arrow, b.y - sin(angle - spread) * arrow), 4f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(a: Offset, b: Offset, color: Color) {
    val angle = atan2(b.y - a.y, b.x - a.x)
    val arrow = 24f
    val spread = 0.55f
    drawLine(color, b, Offset(b.x - cos(angle + spread) * arrow, b.y - sin(angle + spread) * arrow), 5f)
    drawLine(color, b, Offset(b.x - cos(angle - spread) * arrow, b.y - sin(angle - spread) * arrow), 5f)
}
