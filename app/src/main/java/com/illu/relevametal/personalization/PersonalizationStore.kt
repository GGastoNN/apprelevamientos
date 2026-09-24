package com.illu.relevametal.personalization

import android.content.Context
import android.graphics.Color

const val DEFAULT_ACCENT = "#0B5E55"
const val DEFAULT_MEASUREMENT = "#00D4FF"
const val DEFAULT_MARKUP = "#FFD54F"
const val DEFAULT_TEXT = "#FFFFFF"
const val DEFAULT_TEXT_PIN = "#D32F2F"
const val DEFAULT_DETECTION = "#FF9800"
const val DEFAULT_STAMP_BACKGROUND = "#000000"
const val DEFAULT_STAMP_TEXT = "#FFFFFF"

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class PersonalizationSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val accentColorHex: String = DEFAULT_ACCENT,
    val measurementColorHex: String = DEFAULT_MEASUREMENT,
    val markupColorHex: String = DEFAULT_MARKUP,
    val textColorHex: String = DEFAULT_TEXT,
    val textPinColorHex: String = DEFAULT_TEXT_PIN,
    val detectionColorHex: String = DEFAULT_DETECTION,
    val stampBackgroundColorHex: String = DEFAULT_STAMP_BACKGROUND,
    val stampTextColorHex: String = DEFAULT_STAMP_TEXT,
    val annotationTextScale: Float = 1f,
    val lineThicknessScale: Float = 1f
)

class PersonalizationStore(context: Context) {
    private val prefs = context.getSharedPreferences("grupo_idea_personalization", Context.MODE_PRIVATE)

    fun load(): PersonalizationSettings = PersonalizationSettings(
        themeMode = runCatching {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.SYSTEM.name).orEmpty())
        }.getOrDefault(AppThemeMode.SYSTEM),
        accentColorHex = normalizeHexColor(prefs.getString("accent_color", DEFAULT_ACCENT).orEmpty(), DEFAULT_ACCENT),
        measurementColorHex = normalizeHexColor(prefs.getString("measurement_color", DEFAULT_MEASUREMENT).orEmpty(), DEFAULT_MEASUREMENT),
        markupColorHex = normalizeHexColor(prefs.getString("markup_color", DEFAULT_MARKUP).orEmpty(), DEFAULT_MARKUP),
        textColorHex = normalizeHexColor(prefs.getString("text_color", DEFAULT_TEXT).orEmpty(), DEFAULT_TEXT),
        textPinColorHex = normalizeHexColor(prefs.getString("text_pin_color", DEFAULT_TEXT_PIN).orEmpty(), DEFAULT_TEXT_PIN),
        detectionColorHex = normalizeHexColor(prefs.getString("detection_color", DEFAULT_DETECTION).orEmpty(), DEFAULT_DETECTION),
        stampBackgroundColorHex = normalizeHexColor(prefs.getString("stamp_background_color", DEFAULT_STAMP_BACKGROUND).orEmpty(), DEFAULT_STAMP_BACKGROUND),
        stampTextColorHex = normalizeHexColor(prefs.getString("stamp_text_color", DEFAULT_STAMP_TEXT).orEmpty(), DEFAULT_STAMP_TEXT),
        annotationTextScale = prefs.getFloat("annotation_text_scale", 1f).coerceIn(0.75f, 1.5f),
        lineThicknessScale = prefs.getFloat("line_thickness_scale", 1f).coerceIn(0.65f, 2f)
    )

    fun save(settings: PersonalizationSettings) {
        val normalized = normalize(settings)
        prefs.edit()
            .putString("theme_mode", normalized.themeMode.name)
            .putString("accent_color", normalized.accentColorHex)
            .putString("measurement_color", normalized.measurementColorHex)
            .putString("markup_color", normalized.markupColorHex)
            .putString("text_color", normalized.textColorHex)
            .putString("text_pin_color", normalized.textPinColorHex)
            .putString("detection_color", normalized.detectionColorHex)
            .putString("stamp_background_color", normalized.stampBackgroundColorHex)
            .putString("stamp_text_color", normalized.stampTextColorHex)
            .putFloat("annotation_text_scale", normalized.annotationTextScale)
            .putFloat("line_thickness_scale", normalized.lineThicknessScale)
            .apply()
    }

    fun normalize(settings: PersonalizationSettings): PersonalizationSettings = settings.copy(
        accentColorHex = normalizeHexColor(settings.accentColorHex, DEFAULT_ACCENT),
        measurementColorHex = normalizeHexColor(settings.measurementColorHex, DEFAULT_MEASUREMENT),
        markupColorHex = normalizeHexColor(settings.markupColorHex, DEFAULT_MARKUP),
        textColorHex = normalizeHexColor(settings.textColorHex, DEFAULT_TEXT),
        textPinColorHex = normalizeHexColor(settings.textPinColorHex, DEFAULT_TEXT_PIN),
        detectionColorHex = normalizeHexColor(settings.detectionColorHex, DEFAULT_DETECTION),
        stampBackgroundColorHex = normalizeHexColor(settings.stampBackgroundColorHex, DEFAULT_STAMP_BACKGROUND),
        stampTextColorHex = normalizeHexColor(settings.stampTextColorHex, DEFAULT_STAMP_TEXT),
        annotationTextScale = settings.annotationTextScale.coerceIn(0.75f, 1.5f),
        lineThicknessScale = settings.lineThicknessScale.coerceIn(0.65f, 2f)
    )
}

fun isValidHexColor(value: String): Boolean {
    val clean = value.trim().removePrefix("#")
    return clean.length == 6 && clean.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
}

fun normalizeHexColor(value: String, fallback: String): String {
    val clean = value.trim().removePrefix("#").uppercase()
    return if (clean.length == 6 && clean.all { it in '0'..'9' || it in 'A'..'F' }) "#$clean" else fallback
}

fun colorInt(hex: String, fallback: String = DEFAULT_ACCENT): Int =
    runCatching { Color.parseColor(normalizeHexColor(hex, fallback)) }
        .getOrElse { Color.parseColor(fallback) }

fun contrastShadowColor(color: Int): Int {
    val luminance = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f
    return if (luminance > 0.55f) Color.BLACK else Color.WHITE
}
