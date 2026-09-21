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

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun dec(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
}
