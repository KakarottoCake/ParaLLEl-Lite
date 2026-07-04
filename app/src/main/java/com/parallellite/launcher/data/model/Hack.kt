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
    /** The most recent downloadable variant, if any. */
    val latestVariant: HackVersion? get() = variants.lastOrNull()
}

data class HackVersion(
    val name: String,
    val downloadUrl: String,
)
