package com.illu.relevametal.branding

import android.content.Context

data class BrandingSettings(
    val companyName: String = "Grupo IDEA",
    val logoPath: String = "",
    val stampEnabled: Boolean = true,
    val showTimestamp: Boolean = true,
    val showProject: Boolean = true,
    val showSpace: Boolean = true,
    val showOpening: Boolean = true
)

class BrandingStore(context: Context) {
    private val prefs = context.getSharedPreferences("grupo_idea_branding", Context.MODE_PRIVATE)

    fun load(): BrandingSettings = BrandingSettings(
        companyName = prefs.getString("company_name", "Grupo IDEA").orEmpty(),
        logoPath = prefs.getString("logo_path", "").orEmpty(),
        stampEnabled = prefs.getBoolean("stamp_enabled", true),
        showTimestamp = prefs.getBoolean("show_timestamp", true),
        showProject = prefs.getBoolean("show_project", true),
        showSpace = prefs.getBoolean("show_space", true),
        showOpening = prefs.getBoolean("show_opening", true)
    )

    fun save(settings: BrandingSettings) {
        prefs.edit()
            .putString("company_name", settings.companyName.trim())
            .putString("logo_path", settings.logoPath)
            .putBoolean("stamp_enabled", settings.stampEnabled)
            .putBoolean("show_timestamp", settings.showTimestamp)
            .putBoolean("show_project", settings.showProject)
            .putBoolean("show_space", settings.showSpace)
            .putBoolean("show_opening", settings.showOpening)
            .apply()
    }
}
