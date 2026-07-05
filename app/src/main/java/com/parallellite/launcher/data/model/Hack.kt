package com.parallellite.launcher.data.model

/**
 * UI-facing domain model for a ROM hack. Decoupled from the wire DTOs so that
 * API changes don't ripple into the UI layer.
 */
data class Hack(
    val id: String,
    val title: String,
    val version: String,
    val description: String,
    val authors: List<String>,
    val starCount: Int,
    val thumbnailUrl: String?,
    val playlists: List<String>,
    val variants: List<HackVersion>,
    val layoutUrl: String? = null,
) {
    /** The newest downloadable variant (variants are sorted newest-first). */
    val latestVariant: HackVersion? get() = variants.firstOrNull()

    /** Recommended graphics plugin for the newest version, if the API provides one. */
    val recommendedPlugin: String? get() = latestVariant?.plugin

    /** Human-readable settings the newest version needs (e.g. "16 kB EEPROM"). */
    val recommendedSettings: List<String> get() = latestVariant?.settings.orEmpty()
}

data class HackVersion(
    val name: String,
    val downloadUrl: String,
    val plugin: String? = null,
    val settings: List<String> = emptyList(),
)
