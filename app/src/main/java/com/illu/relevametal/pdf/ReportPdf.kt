package com.illu.relevametal.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.illu.relevametal.annotation.AnnotationCodec
import com.illu.relevametal.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class ReportPdf(private val context: Context) {
    data class OpeningBundle(
        val opening: OpeningEntity,
        val evidence: List<EvidenceEntity>
    )

    data class SpaceBundle(
        val space: SpaceEntity,
        val openings: List<OpeningBundle>
    )

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 36f

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 23f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.rgb(20, 62, 57)
    }
    private val h1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.rgb(20, 62, 57)
    }
    private val h2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.rgb(38, 56, 52)
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = Color.rgb(35, 39, 38)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8.5f
        color = Color.rgb(85, 94, 91)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(205, 216, 212)
        strokeWidth = 1f
    }

    fun generate(
        project: ProjectEntity,
        spaces: List<SpaceBundle>,
        events: List<EventEntity>
    ): File {
        val pdf = PdfDocument()
        var pageNumber = 0

        fun startPage(title: String? = null): Pair<PdfDocument.Page, Canvas> {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdf.startPage(info)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            drawHeader(canvas, project.name, pageNumber)
            title?.let {
                canvas.drawText(it, margin, 72f, h1Paint)
            }
            return page to canvas
        }

        fun finish(page: PdfDocument.Page) {
            pdf.finishPage(page)
        }

        var (page, canvas) = startPage()
        drawCover(canvas, project, spaces, events)
        finish(page)

        for (space in spaces) {
            val started = startPage("ESPACIO / SECTOR")
            page = started.first
            canvas = started.second
            var y = 102f
            canvas.drawText(space.space.name, margin, y, titlePaint)
            y += 24f
            val contextLine = listOf(space.space.level, space.space.sector)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (contextLine.isNotBlank()) {
                canvas.drawText(contextLine, margin, y, bodyPaint)
                y += 18f
            }
            if (space.space.notes.isNotBlank()) {
                y = drawWrapped(canvas, space.space.notes, margin, y, pageWidth - margin * 2, bodyPaint)
                y += 10f
            }
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 18f
            canvas.drawText("Vanos registrados: ${space.openings.size}", margin, y, h2Paint)
            y += 20f

            if (space.openings.isEmpty()) {
                canvas.drawText("Sin vanos registrados en este espacio.", margin, y, bodyPaint)
            } else {
                space.openings.forEach { bundle ->
                    if (y > 720f) {
                        finish(page)
                        val next = startPage("ESPACIO / SECTOR · ${space.space.name}")
                        page = next.first
                        canvas = next.second
                        y = 104f
                    }
                    y = drawOpeningSummary(canvas, bundle.opening, bundle.evidence.size, y)
                    y += 12f
                }
            }
            finish(page)

            for (bundle in space.openings) {
                val openingPage = startPage("FICHA DE VANO · ${bundle.opening.code}")
                page = openingPage.first
                canvas = openingPage.second
                drawOpeningDetail(canvas, space.space, bundle.opening)
                finish(page)

                bundle.evidence.forEachIndexed { index, evidence ->
                    val photoPage = startPage("EVIDENCIA · ${bundle.opening.code} · Foto ${index + 1}/${bundle.evidence.size}")
                    page = photoPage.first
                    canvas = photoPage.second
                    drawEvidencePage(canvas, bundle.opening, evidence)
                    finish(page)
                }
            }
        }

        if (events.isNotEmpty()) {
            var started = startPage("BITÁCORA DE OBRA")
            page = started.first
            canvas = started.second
            var y = 105f
            events.forEach { event ->
                val detailLines = if (event.detail.isBlank()) 0 else 2
                val estimated = 46f + detailLines * 12f
                if (y + estimated > 780f) {
                    finish(page)
                    started = startPage("BITÁCORA DE OBRA · continuación")
                    page = started.first
                    canvas = started.second
                    y = 105f
                }
                val severity = when (event.severity) {
                    "ALERTA" -> "ALERTA"
                    "DECISION" -> "DECISIÓN"
                    else -> event.kind
                }
                canvas.drawText(
                    "${formatDate(event.createdAt)} · $severity",
                    margin,
                    y,
                    smallPaint
                )
                y += 15f
                canvas.drawText(event.title, margin, y, h2Paint)
                y += 14f
                if (event.detail.isNotBlank()) {
                    y = drawWrapped(
                        canvas,
                        event.detail,
                        margin,
                        y,
                        pageWidth - margin * 2,
                        bodyPaint,
                        maxLines = 4
                    )
                }
                y += 12f
                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
                y += 14f
            }
            finish(page)
        }

        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val safeName = project.name
            .replace(Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ_-]+"), "_")
            .take(40)
            .ifBlank { "Obra" }
        val out = File(
            dir,
            "GrupoIDEA_Relevamiento_${safeName}_${System.currentTimeMillis()}.pdf"
        )
        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    private fun drawHeader(canvas: Canvas, projectName: String, pageNo: Int) {
        canvas.drawText("GRUPO IDEA · RELEVAMIENTOS", margin, 28f, smallPaint)
        val pageText = "Pág. $pageNo"
        canvas.drawText(pageText, pageWidth - margin - smallPaint.measureText(pageText), 28f, smallPaint)
        canvas.drawLine(margin, 38f, pageWidth - margin, 38f, linePaint)
        val project = projectName.take(70)
        canvas.drawText(project, margin, 53f, smallPaint)
    }

    private fun drawCover(
        canvas: Canvas,
        project: ProjectEntity,
        spaces: List<SpaceBundle>,
        events: List<EventEntity>
    ) {
        var y = 120f
        canvas.drawText("INFORME DE RELEVAMIENTO", margin, y, titlePaint)
        y += 28f
        canvas.drawText("CARPINTERÍA METÁLICA", margin, y, h1Paint)
        y += 52f

        canvas.drawText("Obra", margin, y, smallPaint)
        y += 18f
        y = drawWrapped(canvas, project.name, margin, y, pageWidth - margin * 2, h1Paint, 3)
        y += 18f

        y = coverField(canvas, "Cliente", project.client, y)
        y = coverField(canvas, "Dirección", project.address, y)
        y = coverField(canvas, "Responsable", project.responsible, y)
        y = coverField(canvas, "Estado", statusLabel(project.status), y)
        y = coverField(canvas, "Informe generado", formatDate(System.currentTimeMillis()), y)

        val openingCount = spaces.sumOf { it.openings.size }
        val photoCount = spaces.sumOf { s -> s.openings.sumOf { it.evidence.size } }
        y += 12f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 28f
        canvas.drawText("RESUMEN", margin, y, h1Paint)
        y += 26f
        canvas.drawText("Espacios / sectores: ${spaces.size}", margin, y, bodyPaint)
        y += 17f
        canvas.drawText("Vanos relevados / cargados: $openingCount", margin, y, bodyPaint)
        y += 17f
        canvas.drawText("Fotografías: $photoCount", margin, y, bodyPaint)
        y += 17f
        canvas.drawText("Eventos en bitácora: ${events.size}", margin, y, bodyPaint)
        y += 28f

        if (project.notes.isNotBlank()) {
            canvas.drawText("OBSERVACIONES GENERALES", margin, y, h2Paint)
            y += 18f
            drawWrapped(canvas, project.notes, margin, y, pageWidth - margin * 2, bodyPaint, 8)
        }

        canvas.drawText(
            "Las cotas manuales/calibradas constituyen la referencia documental. La detección visual automática de vanos es una ayuda de encuadre y no reemplaza la medición física.",
            margin,
            790f,
            smallPaint
        )
    }

    private fun coverField(canvas: Canvas, label: String, value: String, startY: Float): Float {
        var y = startY
        canvas.drawText(label, margin, y, smallPaint)
        y += 15f
        canvas.drawText(value.ifBlank { "—" }.take(90), margin, y, bodyPaint)
        return y + 24f
    }

    private fun drawOpeningSummary(
        canvas: Canvas,
        o: OpeningEntity,
        photoCount: Int,
        startY: Float
    ): Float {
        var y = startY
        canvas.drawText("${o.code} · ${o.type} · ${statusLabel(o.status)}", margin, y, h2Paint)
        y += 16f
        canvas.drawText(
            "${o.widthMm ?: "—"} × ${o.heightMm ?: "—"} mm · Antepecho ${o.sillMm ?: "—"} mm · Fotos $photoCount",
            margin,
            y,
            bodyPaint
        )
        y += 15f
        if (o.notes.isNotBlank()) {
            y = drawWrapped(canvas, "Obs.: ${o.notes}", margin, y, pageWidth - margin * 2, smallPaint, 2)
        }
        canvas.drawLine(margin, y + 5f, pageWidth - margin, y + 5f, linePaint)
        return y + 14f
    }

    private fun drawOpeningDetail(canvas: Canvas, space: SpaceEntity, o: OpeningEntity) {
        var y = 105f
        canvas.drawText("${o.code} · ${o.type}", margin, y, titlePaint)
        y += 22f
        canvas.drawText("Espacio: ${space.name}", margin, y, bodyPaint)
        y += 18f
        canvas.drawText("Estado: ${statusLabel(o.status)}", margin, y, bodyPaint)
        y += 30f

        canvas.drawText("MEDIDAS PRINCIPALES", margin, y, h2Paint)
        y += 20f
        val rows = listOf(
            "Ancho" to mm(o.widthMm),
            "Alto" to mm(o.heightMm),
            "Antepecho" to mm(o.sillMm),
            "Diagonal 1" to mm(o.diagonal1Mm),
            "Diagonal 2" to mm(o.diagonal2Mm),
            "Espesor muro" to mm(o.wallThicknessMm),
            "Profundidad" to mm(o.depthMm),
            "Libre izquierda" to mm(o.clearanceLeftMm),
            "Libre derecha" to mm(o.clearanceRightMm),
            "Libre superior" to mm(o.clearanceTopMm),
            "Libre inferior" to mm(o.clearanceBottomMm)
        )
        rows.chunked(2).forEach { pair ->
            val left = pair[0]
            canvas.drawText("${left.first}: ${left.second}", margin, y, bodyPaint)
            pair.getOrNull(1)?.let { right ->
                canvas.drawText("${right.first}: ${right.second}", 305f, y, bodyPaint)
            }
            y += 17f
        }

        y += 12f
        canvas.drawText("CONTROL DEL VANO", margin, y, h2Paint)
        y += 20f
        listOf(
            "Plomo" to o.plumbState,
            "Nivel" to o.levelState,
            "Escuadra" to o.squareState,
            "Piso" to o.floorState,
            "Revoque" to o.plasterState,
            "Premarco" to o.premarcoState
        ).chunked(2).forEach { pair ->
            val left = pair[0]
            canvas.drawText("${left.first}: ${checkLabel(left.second)}", margin, y, bodyPaint)
            pair.getOrNull(1)?.let { right ->
                canvas.drawText("${right.first}: ${checkLabel(right.second)}", 305f, y, bodyPaint)
            }
            y += 17f
        }

        y += 12f
        if (o.openingDirection.isNotBlank()) {
            canvas.drawText("Apertura / condición", margin, y, h2Paint)
            y += 17f
            y = drawWrapped(canvas, o.openingDirection, margin, y, pageWidth - margin * 2, bodyPaint, 4)
            y += 10f
        }
        if (o.interference.isNotBlank()) {
            canvas.drawText("Interferencias", margin, y, h2Paint)
            y += 17f
            y = drawWrapped(canvas, o.interference, margin, y, pageWidth - margin * 2, bodyPaint, 5)
            y += 10f
        }
        if (o.notes.isNotBlank()) {
            canvas.drawText("Observaciones", margin, y, h2Paint)
            y += 17f
            drawWrapped(canvas, o.notes, margin, y, pageWidth - margin * 2, bodyPaint, 8)
        }
    }

    private fun drawEvidencePage(
        canvas: Canvas,
        opening: OpeningEntity,
        evidence: EvidenceEntity
    ) {
        var y = 104f
        val source = decodeSampledBitmap(evidence.filePath, 2200)
        if (source == null) {
            canvas.drawText("La fotografía no está disponible en el dispositivo.", margin, y, bodyPaint)
            return
        }

        val annotated = renderAnnotations(source, evidence.annotationJson)
        val maxW = pageWidth - margin * 2
        val maxH = 560f
        val scale = minOf(maxW / annotated.width, maxH / annotated.height)
        val drawW = annotated.width * scale
        val drawH = annotated.height * scale
        val dst = RectF(margin, y, margin + drawW, y + drawH)
        canvas.drawBitmap(annotated, null, dst, null)
        y = dst.bottom + 18f

        canvas.drawText("Vano: ${opening.code} · ${opening.type}", margin, y, h2Paint)
        y += 16f
        canvas.drawText("Fecha: ${formatDate(evidence.createdAt)}", margin, y, smallPaint)
        y += 16f
        if (evidence.caption.isNotBlank()) {
            y = drawWrapped(canvas, evidence.caption, margin, y, pageWidth - margin * 2, bodyPaint, 5)
            y += 8f
        }
        val count = AnnotationCodec.decode(evidence.annotationJson).size
        canvas.drawText("Cotas dibujadas: $count", margin, y, smallPaint)

        if (annotated !== source) annotated.recycle()
        source.recycle()
    }


    private fun decodeSampledBitmap(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > maxSide * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun renderAnnotations(source: Bitmap, raw: String): Bitmap {
        val annotations = AnnotationCodec.decode(raw)
        if (annotations.isEmpty()) return source
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 205, 235)
            strokeWidth = maxOf(4f, source.width / 400f * 4f)
            style = Paint.Style.STROKE
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = maxOf(24f, source.width / 30f)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }

        annotations.forEach { m ->
            val x1 = m.x1 * out.width
            val y1 = m.y1 * out.height
            val x2 = m.x2 * out.width
            val y2 = m.y2 * out.height
            canvas.drawLine(x1, y1, x2, y2, line)
            canvas.drawCircle(x1, y1, 8f, line)
            canvas.drawCircle(x2, y2, 8f, line)

            val angle = atan2(y2 - y1, x2 - x1)
            val arrow = maxOf(18f, source.width / 45f)
            val spread = 0.55f
            canvas.drawLine(x1, y1, x1 + cos(angle + spread) * arrow, y1 + sin(angle + spread) * arrow, line)
            canvas.drawLine(x1, y1, x1 + cos(angle - spread) * arrow, y1 + sin(angle - spread) * arrow, line)
            canvas.drawLine(x2, y2, x2 - cos(angle + spread) * arrow, y2 - sin(angle + spread) * arrow, line)
            canvas.drawLine(x2, y2, x2 - cos(angle - spread) * arrow, y2 - sin(angle - spread) * arrow, line)

            val label = listOf(m.label, m.value).filter { it.isNotBlank() }.joinToString(" · ")
            canvas.drawText(label, (x1 + x2) / 2f + 8f, (y1 + y2) / 2f - 8f, fill)
        }
        return out
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int = Int.MAX_VALUE
    ): Float {
        var y = startY
        var lines = 0
        val lineHeight = paint.textSize * 1.35f
        text.split('\n').forEach { paragraph ->
            var current = ""
            paragraph.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotBlank() && lines < maxLines) {
                        canvas.drawText(current, x, y, paint)
                        y += lineHeight
                        lines++
                    }
                    current = word
                }
            }
            if (current.isNotBlank() && lines < maxLines) {
                canvas.drawText(current, x, y, paint)
                y += lineHeight
                lines++
            }
        }
        return y
    }

    private fun mm(value: Int?): String = value?.let { "$it mm" } ?: "—"

    private fun checkLabel(value: String): String = when (value) {
        "OK" -> "OK"
        "OBSERVAR" -> "OBSERVAR"
        else -> "No verificado"
    }

    private fun statusLabel(value: String): String = when (value) {
        "EN_CURSO" -> "En curso"
        "PAUSADA" -> "Pausada"
        "FINALIZADA" -> "Finalizada"
        "PENDIENTE" -> "Pendiente"
        "VERIFICAR" -> "Verificar"
        "RELEVADO" -> "Relevado"
        "APROBADO" -> "Aprobado"
        else -> value.replace('_', ' ')
    }

    private fun formatDate(time: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(time))
}
