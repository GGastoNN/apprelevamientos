package com.illu.relevametal.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.illu.relevametal.annotation.AnnotationCodec
import com.illu.relevametal.annotation.MeasurementAnnotation
import com.illu.relevametal.branding.BrandingSettings
import com.illu.relevametal.data.*
import com.illu.relevametal.render.PhotoRenderer
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
        events: List<EventEntity>,
        branding: BrandingSettings,
        referencePhotos: List<ProjectReferencePhotoEntity>
    ): File {
        require(referencePhotos.isNotEmpty()) { "El informe requiere al menos una imagen del edificio para la portada." }
        val pdf = PdfDocument()
        var pageNumber = 0

        fun startPage(title: String? = null): Pair<PdfDocument.Page, Canvas> {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdf.startPage(info)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            drawHeader(canvas, project.name, pageNumber, branding)
            title?.let {
                canvas.drawText(it, margin, 72f, h1Paint)
            }
            return page to canvas
        }

        fun finish(page: PdfDocument.Page) {
            pdf.finishPage(page)
        }

        var (page, canvas) = startPage()
        drawCover(canvas, project, spaces, events, referencePhotos)
        finish(page)

        for (space in spaces) {
            if (space.openings.isEmpty()) continue

            space.openings.forEachIndexed { index, bundle ->
                val openingPage = startPage("VANO · ${bundle.opening.code} · ${space.space.name}")
                page = openingPage.first
                canvas = openingPage.second
                drawOpeningSheet(
                    canvas = canvas,
                    project = project,
                    space = space.space,
                    opening = bundle.opening,
                    selectedEvidence = bundle.evidence.firstOrNull(),
                    branding = branding,
                    indexInSpace = index + 1,
                    openingsInSpace = space.openings.size
                )
                finish(page)
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
        val brandPrefix = branding.companyName
            .trim()
            .replace(Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ_-]+"), "_")
            .take(24)
            .trim('_')
            .ifBlank { "Relevamiento" }
        val out = File(
            dir,
            "${brandPrefix}_${safeName}_${System.currentTimeMillis()}.pdf"
        )
        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    private fun drawHeader(canvas: Canvas, projectName: String, pageNo: Int, branding: BrandingSettings) {
        val company = branding.companyName.trim()
        val headerText = if (company.isBlank()) "RELEVAMIENTOS" else "${company.uppercase()} · RELEVAMIENTOS"
        canvas.drawText(headerText, margin, 28f, smallPaint)
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
        events: List<EventEntity>,
        referencePhotos: List<ProjectReferencePhotoEntity>
    ) {
        var y = 96f
        canvas.drawText("INFORME DE RELEVAMIENTO", margin, y, titlePaint)
        y += 28f
        canvas.drawText("CARPINTERÍA METÁLICA", margin, y, h1Paint)
        y += 34f

        canvas.drawText("Obra", margin, y, smallPaint)
        y += 17f
        y = drawWrapped(canvas, project.name, margin, y, pageWidth - margin * 2, h1Paint, 2)
        y += 12f

        canvas.drawText("REFERENCIAS DEL EDIFICIO", margin, y, h2Paint)
        y += 10f
        val photoTop = y
        val photoHeight = 205f
        drawCoverReferencePhotos(canvas, referencePhotos, photoTop, photoHeight)
        y = photoTop + photoHeight + 22f
        if (referencePhotos.size > 3) {
            canvas.drawText("+${referencePhotos.size - 3} imagen(es) de referencia adicionales", margin, y - 7f, smallPaint)
        }

        val colGap = 22f
        val colWidth = (pageWidth - margin * 2 - colGap) / 2f
        val rightX = margin + colWidth + colGap
        drawCoverField(canvas, "Cliente", project.client, margin, y, colWidth)
        drawCoverField(canvas, "Responsable", project.responsible, rightX, y, colWidth)
        y += 43f
        drawCoverField(canvas, "Dirección", project.address, margin, y, colWidth)
        drawCoverField(canvas, "Estado", statusLabel(project.status), rightX, y, colWidth)
        y += 43f
        drawCoverField(canvas, "Informe generado", formatDate(System.currentTimeMillis()), margin, y, colWidth)

        val openingCount = spaces.sumOf { it.openings.size }
        val evidenceCount = spaces.sumOf { s -> s.openings.sumOf { it.evidence.size } }
        y += 48f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 22f
        canvas.drawText("RESUMEN", margin, y, h1Paint)
        y += 21f
        canvas.drawText("Espacios / sectores: ${spaces.size} · Vanos: $openingCount", margin, y, bodyPaint)
        y += 16f
        canvas.drawText("Fotos incluidas: $evidenceCount · Referencias del edificio: ${referencePhotos.size} · Eventos: ${events.size}", margin, y, bodyPaint)
        y += 24f

        if (project.notes.isNotBlank() && y < 740f) {
            canvas.drawText("OBSERVACIONES GENERALES", margin, y, h2Paint)
            y += 17f
            drawWrapped(canvas, project.notes, margin, y, pageWidth - margin * 2, bodyPaint, 3)
        }

        drawWrapped(
            canvas,
            "Las cotas manuales/calibradas constituyen la referencia documental. La detección visual automática de vanos es una ayuda de encuadre y no reemplaza la medición física.",
            margin,
            790f,
            pageWidth - margin * 2,
            smallPaint,
            2
        )
    }

    private fun drawCoverField(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float
    ) {
        canvas.drawText(label, x, y, smallPaint)
        drawWrapped(canvas, value.ifBlank { "—" }, x, y + 14f, width, bodyPaint, 2)
    }

    private fun drawCoverReferencePhotos(
        canvas: Canvas,
        referencePhotos: List<ProjectReferencePhotoEntity>,
        top: Float,
        height: Float
    ) {
        val shown = referencePhotos.take(3)
        val gap = 8f
        val totalWidth = pageWidth - margin * 2
        val cellWidth = (totalWidth - gap * (shown.size - 1).coerceAtLeast(0)) / shown.size.coerceAtLeast(1)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 216, 212)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val missingBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 245, 244) }

        shown.forEachIndexed { index, photo ->
            val left = margin + index * (cellWidth + gap)
            val dst = RectF(left, top, left + cellWidth, top + height)
            val bitmap = decodeSampledBitmap(photo.filePath, 900)
            if (bitmap != null) {
                drawBitmapCenterCrop(canvas, bitmap, dst)
                bitmap.recycle()
            } else {
                canvas.drawRect(dst, missingBg)
                canvas.drawText("Imagen no disponible", left + 8f, top + height / 2f, smallPaint)
            }
            canvas.drawRect(dst, border)
        }
    }

    private fun drawBitmapCenterCrop(canvas: Canvas, bitmap: Bitmap, dst: RectF) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val dstRatio = dst.width() / dst.height()
        val src = if (srcRatio > dstRatio) {
            val wantedWidth = (bitmap.height * dstRatio).toInt().coerceAtLeast(1)
            val left = ((bitmap.width - wantedWidth) / 2).coerceAtLeast(0)
            Rect(left, 0, (left + wantedWidth).coerceAtMost(bitmap.width), bitmap.height)
        } else {
            val wantedHeight = (bitmap.width / dstRatio).toInt().coerceAtLeast(1)
            val top = ((bitmap.height - wantedHeight) / 2).coerceAtLeast(0)
            Rect(0, top, bitmap.width, (top + wantedHeight).coerceAtMost(bitmap.height))
        }
        canvas.drawBitmap(bitmap, src, dst, null)
    }

    private fun drawOpeningSheet(
        canvas: Canvas,
        project: ProjectEntity,
        space: SpaceEntity,
        opening: OpeningEntity,
        selectedEvidence: EvidenceEntity?,
        branding: BrandingSettings,
        indexInSpace: Int,
        openingsInSpace: Int
    ) {
        var y = 94f
        canvas.drawText("${opening.code} · ${opening.type}", margin, y, titlePaint)
        y += 20f
        canvas.drawText(
            "Espacio: ${space.name} · Vano $indexInSpace de $openingsInSpace · ${statusLabel(opening.status)}",
            margin,
            y,
            bodyPaint
        )
        y += 14f
        val contextLine = listOf(space.level, space.sector).filter { it.isNotBlank() }.joinToString(" · ")
        if (contextLine.isNotBlank()) {
            canvas.drawText(contextLine, margin, y, smallPaint)
            y += 13f
        }
        canvas.drawLine(margin, y + 2f, pageWidth - margin, y + 2f, linePaint)
        y += 22f

        val gap = 18f
        val leftWidth = 248f
        val rightX = margin + leftWidth + gap
        val rightWidth = pageWidth - margin - rightX
        val contentTop = y

        var leftY = contentTop
        canvas.drawText("MEDIDAS", margin, leftY, h2Paint)
        leftY += 18f
        val measurementRows = listOf(
            "Ancho: ${mm(opening.widthMm)}   Alto: ${mm(opening.heightMm)}",
            "Antepecho: ${mm(opening.sillMm)}",
            "Diagonal 1: ${mm(opening.diagonal1Mm)}",
            "Diagonal 2: ${mm(opening.diagonal2Mm)}",
            "Espesor muro: ${mm(opening.wallThicknessMm)}",
            "Profundidad: ${mm(opening.depthMm)}",
            "Libre izq.: ${mm(opening.clearanceLeftMm)}",
            "Libre der.: ${mm(opening.clearanceRightMm)}",
            "Libre sup.: ${mm(opening.clearanceTopMm)}",
            "Libre inf.: ${mm(opening.clearanceBottomMm)}"
        )
        measurementRows.forEach { row ->
            canvas.drawText(row, margin, leftY, bodyPaint)
            leftY += 14f
        }

        leftY += 10f
        canvas.drawText("CONTROL DEL VANO", margin, leftY, h2Paint)
        leftY += 18f
        val checks = listOf(
            "Plomo" to opening.plumbState,
            "Nivel" to opening.levelState,
            "Escuadra" to opening.squareState,
            "Piso" to opening.floorState,
            "Revoque" to opening.plasterState,
            "Premarco" to opening.premarcoState
        )
        checks.chunked(2).forEach { pair ->
            val text = pair.joinToString("   ·   ") { (label, value) -> "$label: ${checkLabel(value)}" }
            canvas.drawText(text, margin, leftY, smallPaint)
            leftY += 14f
        }

        var rightY = contentTop
        canvas.drawText("FOTO PARA EL INFORME", rightX, rightY, h2Paint)
        rightY += 10f
        val photoBox = RectF(rightX, rightY, rightX + rightWidth, rightY + 278f)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 216, 212)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val photoBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 247, 246) }
        canvas.drawRect(photoBox, photoBg)

        if (selectedEvidence != null) {
            val annotated = PhotoRenderer.render(
                evidence = selectedEvidence,
                project = project,
                space = space,
                opening = opening,
                branding = branding,
                maxSide = 1200
            )
            if (annotated != null) {
                drawBitmapFitCenter(canvas, annotated, photoBox)
                annotated.recycle()
            } else {
                canvas.drawText("Imagen no disponible", rightX + 10f, photoBox.centerY(), smallPaint)
            }
        } else {
            canvas.drawText("Sin foto incluida", rightX + 10f, photoBox.centerY(), smallPaint)
        }
        canvas.drawRect(photoBox, border)
        rightY = photoBox.bottom + 15f

        if (selectedEvidence != null) {
            canvas.drawText(
                "${phaseLabel(selectedEvidence.phase)} · ${formatDate(selectedEvidence.createdAt)}",
                rightX,
                rightY,
                smallPaint
            )
            rightY += 14f
            if (selectedEvidence.caption.isNotBlank()) {
                rightY = drawWrapped(canvas, selectedEvidence.caption, rightX, rightY, rightWidth, bodyPaint, 3)
            }
            val measureCount = AnnotationCodec.decodeMeasurements(selectedEvidence.annotationJson).size
            val markupCount = AnnotationCodec.decodeMarkups(selectedEvidence.annotationJson).size
            rightY += 4f
            canvas.drawText("Cotas: $measureCount · Marcas: $markupCount", rightX, rightY, smallPaint)
            rightY += 12f
        }

        y = maxOf(leftY, rightY) + 16f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 20f

        if (opening.openingDirection.isNotBlank()) {
            canvas.drawText("Apertura / condición", margin, y, h2Paint)
            y += 15f
            y = drawWrapped(canvas, opening.openingDirection, margin, y, pageWidth - margin * 2, bodyPaint, 2)
            y += 7f
        }
        if (opening.interference.isNotBlank() && y < 742f) {
            canvas.drawText("Interferencias", margin, y, h2Paint)
            y += 15f
            y = drawWrapped(canvas, opening.interference, margin, y, pageWidth - margin * 2, bodyPaint, 3)
            y += 7f
        }
        if (opening.notes.isNotBlank() && y < 755f) {
            canvas.drawText("Observaciones", margin, y, h2Paint)
            y += 15f
            drawWrapped(canvas, opening.notes, margin, y, pageWidth - margin * 2, bodyPaint, 4)
        }
    }

    private fun drawBitmapFitCenter(canvas: Canvas, bitmap: Bitmap, dst: RectF) {
        val scale = minOf(dst.width() / bitmap.width.toFloat(), dst.height() / bitmap.height.toFloat())
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val left = dst.left + (dst.width() - drawW) / 2f
        val top = dst.top + (dst.height() - drawH) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawW, top + drawH), null)
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
        project: ProjectEntity,
        space: SpaceEntity,
        opening: OpeningEntity,
        evidence: EvidenceEntity,
        branding: BrandingSettings
    ) {
        var y = 104f
        val annotated = PhotoRenderer.render(
            evidence = evidence,
            project = project,
            space = space,
            opening = opening,
            branding = branding,
            maxSide = 2200
        )
        if (annotated == null) {
            canvas.drawText("La fotografía no está disponible en el dispositivo.", margin, y, bodyPaint)
            return
        }

        val maxW = pageWidth - margin * 2
        val maxH = 560f
        val scale = minOf(maxW / annotated.width, maxH / annotated.height)
        val drawW = annotated.width * scale
        val drawH = annotated.height * scale
        val dst = RectF(margin, y, margin + drawW, y + drawH)
        canvas.drawBitmap(annotated, null, dst, null)
        y = dst.bottom + 18f

        canvas.drawText("Vano: ${opening.code} · ${opening.type} · ${phaseLabel(evidence.phase)}", margin, y, h2Paint)
        y += 16f
        canvas.drawText("Fecha: ${formatDate(evidence.createdAt)}", margin, y, smallPaint)
        y += 16f
        if (evidence.caption.isNotBlank()) {
            y = drawWrapped(canvas, evidence.caption, margin, y, pageWidth - margin * 2, bodyPaint, 5)
            y += 8f
        }
        val measureCount = AnnotationCodec.decodeMeasurements(evidence.annotationJson).size
        val markupCount = AnnotationCodec.decodeMarkups(evidence.annotationJson).size
        canvas.drawText("Cotas: $measureCount · Marcas: $markupCount", margin, y, smallPaint)
        annotated.recycle()
    }

    private fun phaseLabel(value: String): String = when (value) {
        "INICIAL" -> "Inicial"
        "INCIDENCIA" -> "Incidencia"
        "CORRECCION" -> "Corrección"
        "FINAL" -> "Final"
        else -> "General"
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
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        val decodedLargest = maxOf(decoded.width, decoded.height)
        if (decodedLargest <= maxSide || maxSide <= 0) return decoded
        val scale = maxSide.toFloat() / decodedLargest.toFloat()
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(decoded, width, height, true).also { scaled ->
            if (scaled !== decoded) decoded.recycle()
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val rotation = AnnotationCodec.normalizeRotation(degrees)
        if (rotation == 0) return source
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun renderAnnotations(
        source: Bitmap,
        annotations: List<MeasurementAnnotation>
    ): Bitmap {
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
