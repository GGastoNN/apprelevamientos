package com.illu.relevametal.annotation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class MeasurementAnnotation(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val label: String,
    val value: String
)

data class DetectionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
)

object AnnotationCodec {
    fun encode(items: List<MeasurementAnnotation>): String =
        items.joinToString("\n") { item ->
            listOf(
                item.x1,
                item.y1,
                item.x2,
                item.y2,
                enc(item.label),
                enc(item.value)
            ).joinToString("|")
        }

    fun decode(raw: String): List<MeasurementAnnotation> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 6) return@mapNotNull null
            val x1 = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val y1 = parts[1].toFloatOrNull() ?: return@mapNotNull null
            val x2 = parts[2].toFloatOrNull() ?: return@mapNotNull null
            val y2 = parts[3].toFloatOrNull() ?: return@mapNotNull null
            MeasurementAnnotation(x1, y1, x2, y2, dec(parts[4]), dec(parts[5]))
        }.toList()
    }

    fun parseDetections(raw: String, imageWidth: Int, imageHeight: Int): List<DetectionBox> {
        if (raw.isBlank() || imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val regex = Regex(
            """\{\"l\":([0-9.Ee+\-]+),\"t\":([0-9.Ee+\-]+),\"r\":([0-9.Ee+\-]+),\"b\":([0-9.Ee+\-]+),\"confidence\":([0-9.Ee+\-]+)\}"""
        )
        return regex.findAll(raw).mapNotNull { match ->
            val l = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val t = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val r = match.groupValues[3].toFloatOrNull() ?: return@mapNotNull null
            val b = match.groupValues[4].toFloatOrNull() ?: return@mapNotNull null
            val c = match.groupValues[5].toFloatOrNull() ?: 0f
            DetectionBox(
                left = (l / imageWidth).coerceIn(0f, 1f),
                top = (t / imageHeight).coerceIn(0f, 1f),
                right = (r / imageWidth).coerceIn(0f, 1f),
                bottom = (b / imageHeight).coerceIn(0f, 1f),
                confidence = c
            )
        }.toList()
    }


    fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360

    fun rotatePoint(x: Float, y: Float, degrees: Int): Pair<Float, Float> =
        when (normalizeRotation(degrees)) {
            90 -> (1f - y) to x
            180 -> (1f - x) to (1f - y)
            270 -> y to (1f - x)
            else -> x to y
        }

    fun inverseRotatePoint(x: Float, y: Float, degrees: Int): Pair<Float, Float> =
        rotatePoint(x, y, 360 - normalizeRotation(degrees))

    fun rotateMeasurements(
        items: List<MeasurementAnnotation>,
        degrees: Int
    ): List<MeasurementAnnotation> {
        val rotation = normalizeRotation(degrees)
        if (rotation == 0) return items
        return items.map { item ->
            val p1 = rotatePoint(item.x1, item.y1, rotation)
            val p2 = rotatePoint(item.x2, item.y2, rotation)
            item.copy(
                x1 = p1.first,
                y1 = p1.second,
                x2 = p2.first,
                y2 = p2.second
            )
        }
    }

    fun rotateDetections(
        items: List<DetectionBox>,
        degrees: Int
    ): List<DetectionBox> {
        val rotation = normalizeRotation(degrees)
        if (rotation == 0) return items
        return items.map { box ->
            val corners = listOf(
                rotatePoint(box.left, box.top, rotation),
                rotatePoint(box.right, box.top, rotation),
                rotatePoint(box.right, box.bottom, rotation),
                rotatePoint(box.left, box.bottom, rotation)
            )
            DetectionBox(
                left = corners.minOf { it.first }.coerceIn(0f, 1f),
                top = corners.minOf { it.second }.coerceIn(0f, 1f),
                right = corners.maxOf { it.first }.coerceIn(0f, 1f),
                bottom = corners.maxOf { it.second }.coerceIn(0f, 1f),
                confidence = box.confidence
            )
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun dec(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
}
