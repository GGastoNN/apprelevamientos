package com.illu.relevametal.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.illu.relevametal.annotation.AnnotationCodec
import com.illu.relevametal.annotation.MarkupAnnotation
import com.illu.relevametal.annotation.MeasurementAnnotation
import com.illu.relevametal.branding.BrandingSettings
import com.illu.relevametal.data.EvidenceEntity
import com.illu.relevametal.data.OpeningEntity
import com.illu.relevametal.data.ProjectEntity
import com.illu.relevametal.data.SpaceEntity
import com.illu.relevametal.personalization.DEFAULT_MARKUP
import com.illu.relevametal.personalization.DEFAULT_MEASUREMENT
import com.illu.relevametal.personalization.DEFAULT_TEXT
import com.illu.relevametal.personalization.DEFAULT_TEXT_PIN
import com.illu.relevametal.personalization.DEFAULT_STAMP_BACKGROUND
import com.illu.relevametal.personalization.DEFAULT_STAMP_TEXT
import com.illu.relevametal.personalization.PersonalizationSettings
import com.illu.relevametal.personalization.colorInt
import com.illu.relevametal.personalization.contrastShadowColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object PhotoRenderer {
    fun render(
        evidence: EvidenceEntity,
        project: ProjectEntity,
        space: SpaceEntity,
        opening: OpeningEntity,
        branding: BrandingSettings,
        style: PersonalizationSettings = PersonalizationSettings(),
        maxSide: Int = 2400
    ): Bitmap? {
        val source = decodeSampledBitmap(evidence.filePath, maxSide) ?: return null
        val rotated = rotateBitmap(source, evidence.rotationDegrees)
        val limited = limitBitmap(rotated, maxSide)
        val output = limited.copy(Bitmap.Config.ARGB_8888, true)
        if (output !== limited) limited.recycle()

        val measurements = AnnotationCodec.rotateMeasurements(
            AnnotationCodec.decodeMeasurements(evidence.annotationJson),
            evidence.rotationDegrees
        )
        val markups = AnnotationCodec.rotateMarkups(
            AnnotationCodec.decodeMarkups(evidence.annotationJson),
            evidence.rotationDegrees
        )

        val canvas = Canvas(output)
        drawMeasurements(canvas, output.width, output.height, measurements, style)
        drawMarkups(canvas, output.width, output.height, markups, style)
        if (branding.stampEnabled) {
            drawStamp(canvas, output, project, space, opening, evidence, branding, style)
        }
        return output
    }

    private fun decodeSampledBitmap(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / sample > maxSide * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun limitBitmap(source: Bitmap, maxSide: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxSide || maxSide <= 0) return source
        val scale = maxSide.toFloat() / largest.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true).also { scaled ->
            if (scaled !== source) source.recycle()
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val rotation = AnnotationCodec.normalizeRotation(degrees)
        if (rotation == 0) return source
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) source.recycle()
        return result
    }

    private fun drawMeasurements(
        canvas: Canvas,
        width: Int,
        height: Int,
        items: List<MeasurementAnnotation>,
        style: PersonalizationSettings
    ) {
        if (items.isEmpty()) return
        val stroke = max(4f, width / 500f) * style.lineThicknessScale
        val measurementColor = colorInt(style.measurementColorHex, DEFAULT_MEASUREMENT)
        val annotationTextColor = colorInt(style.textColorHex, DEFAULT_TEXT)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = measurementColor
            this.style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotationTextColor
            textSize = max(24f, width / 34f) * style.annotationTextScale
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, contrastShadowColor(annotationTextColor))
        }
        items.forEach { m ->
            val x1 = m.x1 * width
            val y1 = m.y1 * height
            val x2 = m.x2 * width
            val y2 = m.y2 * height
            canvas.drawLine(x1, y1, x2, y2, line)
            canvas.drawCircle(x1, y1, stroke * 1.4f, line)
            canvas.drawCircle(x2, y2, stroke * 1.4f, line)
            drawDoubleArrow(canvas, x1, y1, x2, y2, line)
            val label = listOf(m.label, m.value).filter { it.isNotBlank() }.joinToString(" · ")
            if (label.isNotBlank()) {
                canvas.drawText(label, (x1 + x2) / 2f + 10f, (y1 + y2) / 2f - 10f, text)
            }
        }
    }

    private fun drawMarkups(
        canvas: Canvas,
        width: Int,
        height: Int,
        items: List<MarkupAnnotation>,
        style: PersonalizationSettings
    ) {
        if (items.isEmpty()) return
        val stroke = max(4f, width / 500f) * style.lineThicknessScale
        val markupColor = colorInt(style.markupColorHex, DEFAULT_MARKUP)
        val annotationTextColor = colorInt(style.textColorHex, DEFAULT_TEXT)
        val textPinColor = colorInt(style.textPinColorHex, DEFAULT_TEXT_PIN)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = markupColor
            this.style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotationTextColor
            textSize = max(24f, width / 34f) * style.annotationTextScale
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, contrastShadowColor(annotationTextColor))
        }
        val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPinColor
            this.style = Paint.Style.FILL
        }
        items.forEach { m ->
            val x1 = m.x1 * width
            val y1 = m.y1 * height
            val x2 = m.x2 * width
            val y2 = m.y2 * height
            when (m.type) {
                "ARROW" -> {
                    canvas.drawLine(x1, y1, x2, y2, paint)
                    drawArrowHead(canvas, x1, y1, x2, y2, paint)
                }
                "RECTANGLE" -> canvas.drawRect(
                    minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2), paint
                )
                "CIRCLE" -> canvas.drawOval(
                    RectF(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2)), paint
                )
                "TEXT" -> {
                    canvas.drawCircle(x1, y1, max(12f, width / 130f), pin)
                    canvas.drawText(m.text, x1 + max(18f, width / 80f), y1 - 8f, text)
                }
            }
        }
    }

    private fun drawStamp(
        canvas: Canvas,
        bitmap: Bitmap,
        project: ProjectEntity,
        space: SpaceEntity,
        opening: OpeningEntity,
        evidence: EvidenceEntity,
        branding: BrandingSettings,
        style: PersonalizationSettings
    ) {
        val companyName = branding.companyName.trim()
        val lines = buildList {
            if (branding.showProject) add("Obra: ${project.name}")
            if (branding.showSpace) add("Sector: ${space.name}")
            if (branding.showOpening) add("Vano: ${opening.code} · ${phaseLabel(evidence.phase)}")
            if (branding.showTimestamp) add(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(evidence.createdAt)))
        }
        val hasLogo = branding.logoPath.isNotBlank() && File(branding.logoPath).exists()
        if (companyName.isBlank() && lines.none { it.isNotBlank() } && !hasLogo) return

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val margin = (w * 0.018f).coerceAtLeast(8f).coerceAtMost(w * 0.08f)
        val panelWidth = (w * 0.47f).coerceAtLeast(minOf(360f, w - margin * 2f)).coerceAtMost(w - margin * 2f)
        val panelHeight = (h * 0.16f).coerceAtLeast(minOf(130f, h - margin * 2f)).coerceAtMost(minOf(320f, h - margin * 2f))
        val left = (w - panelWidth - margin).coerceAtLeast(margin)
        val top = (h - panelHeight - margin).coerceAtLeast(margin)
        val right = w - margin
        val bottom = h - margin

        val stampBackground = colorInt(style.stampBackgroundColorHex, DEFAULT_STAMP_BACKGROUND)
        val stampText = colorInt(style.stampTextColorHex, DEFAULT_STAMP_TEXT)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, Color.red(stampBackground), Color.green(stampBackground), Color.blue(stampBackground))
        }
        canvas.drawRoundRect(RectF(left, top, right, bottom), 18f, 18f, bg)

        var textLeft = left + 22f
        val logo = branding.logoPath.takeIf { hasLogo }
            ?.let { BitmapFactory.decodeFile(it) }
        if (logo != null) {
            val side = panelHeight - 30f
            val scale = minOf(side / logo.width, side / logo.height)
            val dw = logo.width * scale
            val dh = logo.height * scale
            val dst = RectF(left + 15f, top + (panelHeight - dh) / 2f, left + 15f + dw, top + (panelHeight - dh) / 2f + dh)
            canvas.drawBitmap(logo, null, dst, null)
            textLeft = dst.right + 18f
            logo.recycle()
        }

        val titleSize = (w / 48f).coerceIn(24f, 48f)
        val bodySize = (w / 72f).coerceIn(18f, 34f)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stampText
            textSize = titleSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stampText
            textSize = bodySize
        }

        var y = top + 28f
        if (companyName.isNotBlank()) {
            y += titleSize * 0.2f
            canvas.drawText(companyName, textLeft, y, title)
        } else {
            y -= bodySize * 0.15f
        }
        lines.take(4).forEach { lineText ->
            y += bodySize * 1.23f
            canvas.drawText(lineText.take(72), textLeft, y, body)
        }
    }

    private fun drawDoubleArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        val angle = atan2(y2 - y1, x2 - x1)
        val arrow = max(18f, paint.strokeWidth * 5f)
        val spread = 0.55f
        canvas.drawLine(x1, y1, x1 + cos(angle + spread) * arrow, y1 + sin(angle + spread) * arrow, paint)
        canvas.drawLine(x1, y1, x1 + cos(angle - spread) * arrow, y1 + sin(angle - spread) * arrow, paint)
        canvas.drawLine(x2, y2, x2 - cos(angle + spread) * arrow, y2 - sin(angle + spread) * arrow, paint)
        canvas.drawLine(x2, y2, x2 - cos(angle - spread) * arrow, y2 - sin(angle - spread) * arrow, paint)
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        val angle = atan2(y2 - y1, x2 - x1)
        val arrow = max(22f, paint.strokeWidth * 6f)
        val spread = 0.55f
        canvas.drawLine(x2, y2, x2 - cos(angle + spread) * arrow, y2 - sin(angle + spread) * arrow, paint)
        canvas.drawLine(x2, y2, x2 - cos(angle - spread) * arrow, y2 - sin(angle - spread) * arrow, paint)
    }

    private fun phaseLabel(value: String): String = when (value) {
        "INICIAL" -> "Inicial"
        "INCIDENCIA" -> "Incidencia"
        "CORRECCION" -> "Corrección"
        "FINAL" -> "Final"
        else -> "General"
    }
}
