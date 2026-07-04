package com.parallellite.launcher.data.remote

import kotlinx.serialization.Serializable

/** Wire DTOs for the Romhacking.com v3 API. Only the fields we actually consume. */

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val application: String = "ParallelLauncher",
)

@Serializable
data class LoginResponse(
    val token: String,
)

@Serializable
data class RhdcAuthorDto(
    val username: String = "",
)

@Serializable
data class RhdcDownloadDto(
    val directHref: String? = null,
)

@Serializable
data class RhdcVersionDto(
    val archived: Boolean = false,
    val download: RhdcDownloadDto? = null,
)

@Serializable
data class RhdcLayoutDto(
    val directHref: String? = null,
)

@Serializable
data class RhdcProgressDto(
    val playTime: Long = 0,
)

@Serializable
data class RhdcScreenshotDto(
    val directHref: String? = null,
)

/** Full hack detail from `/v3/hacks/hack/{id}`; used to resolve a thumbnail. */
@Serializable
data class RhdcHackDetailDto(
    val screenshots: List<RhdcScreenshotDto> = emptyList(),
)

@Serializable
data class RhdcFollowingHackDto(
    val hackId: String,
    val title: String,
    val description: String? = null,
    val stars: Int = 0,
    val playlists: List<String> = emptyList(),
    val authors: List<RhdcAuthorDto> = emptyList(),
    val versions: List<RhdcVersionDto> = emptyList(),
    val layout: RhdcLayoutDto? = null,
    val progress: RhdcProgressDto? = null,
)
