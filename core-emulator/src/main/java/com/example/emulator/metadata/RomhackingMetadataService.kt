package com.example.emulator.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val application: String = "ParallelLauncher"
)

@Serializable
data class LoginResponse(
    val token: String
)

@Serializable
data class RhdcAuthorDto(
    val username: String
)

@Serializable
data class RhdcDownloadDto(
    val directHref: String? = null
)

@Serializable
data class RhdcLayoutDto(
    val directHref: String? = null
)

@Serializable
data class RhdcVersionDto(
    val patchedSha1: String? = null,
    val plugin: String? = null,
    val pluginFlags: List<String> = emptyList(),
    val hackFlags: List<String> = emptyList(),
    val archived: Boolean = false,
    val download: RhdcDownloadDto? = null
)

@Serializable
data class RhdcProgressDto(
    val playTime: Long = 0,
    val claimedStarPoints: Int = 0,
    val claimedHackComplete: Boolean = false
)

@Serializable
data class RhdcFollowingHackDto(
    val hackId: String,
    val title: String,
    val urlTitle: String? = null,
    val description: String? = null,
    val stars: Int = 0,
    val numDownloads: Int = 0,
    val rating: Double = 0.0,
    val difficulty: Double = 0.0,
    val category: String? = null,
    val needsVerification: Boolean = false,
    val playlists: List<String> = emptyList(),
    val authors: List<RhdcAuthorDto> = emptyList(),
    val versions: List<RhdcVersionDto> = emptyList(),
    val layout: RhdcLayoutDto? = null,
    val progress: RhdcProgressDto? = null
)

typealias ParallelManifest = RhdcPlaylist

@Serializable
data class CourseData(
    val offset: Int,
    val mask: Int
)

@Serializable
data class Course(
    val name: String? = null,
    val data: List<CourseData>
)

@Serializable
data class Group(
    val name: String,
    val side: String,
    val courses: List<Course>
)

@Serializable
data class SaveFormat(
    @SerialName("save_type") val saveType: String = "EEPROM",
    @SerialName("num_slots") val numSlots: Int,
    @SerialName("slots_start") val slotsStart: Int,
    @SerialName("slot_size") val slotSize: Int,
    @SerialName("active_bit") val activeBit: Int,
    @SerialName("checksum_offset") val checksumOffset: Int? = null
)

@Serializable
data class StarLayoutSchema(
    @SerialName("\$schema") val schema: String = "https://parallel-launcher.ca/layout/advanced-01/schema.json",
    val format: SaveFormat,
    val groups: List<Group>,
    val collectedStarIcon: String? = null,
    val missingStarIcon: String? = null
)

@Serializable
data class RhdcHackVersion(
    val name: String,
    val downloadUrl: String
)

object StringOrIntSerializer : kotlinx.serialization.KSerializer<String> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("StringOrInt", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: String) {
        encoder.encodeString(value)
    }
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String {
        return if (decoder is kotlinx.serialization.json.JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is kotlinx.serialization.json.JsonPrimitive) {
                element.content
            } else {
                element.toString()
            }
        } else {
            decoder.decodeString()
        }
    }
}

@Serializable
data class RhdcHack(
    val id: String,
    val title: String,
    val version: String,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @Serializable(with = StringOrIntSerializer::class) @SerialName("star_count") val starCount: String = "0",
    @SerialName("star_layout") val starLayout: StarLayoutSchema? = null,
    @SerialName("base_rom_hash") val baseRomHash: String? = null,
    @SerialName("bps_patch_url") val bpsPatchUrl: String? = null,
    @SerialName("play_time") val playTime: Long = 0,
    @SerialName("file_variants") val fileVariants: List<RhdcHackVersion> = emptyList(),
    @SerialName("playlists") val playlists: List<String> = emptyList()
)

@Serializable
data class RhdcPlaylist(
    val id: String,
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val hacks: List<RhdcHack> = emptyList()
)

object RomhackingMetadataService {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    fun parsePlaylist(jsonStr: String): RhdcPlaylist {
        return json.decodeFromString(RhdcPlaylist.serializer(), jsonStr)
    }

    fun parseHack(jsonStr: String): RhdcHack {
        return json.decodeFromString(RhdcHack.serializer(), jsonStr)
    }

    fun parseStarLayout(jsonStr: String): StarLayoutSchema {
        return json.decodeFromString(StarLayoutSchema.serializer(), jsonStr)
    }

    fun serializePlaylist(playlist: RhdcPlaylist): String {
        return json.encodeToString(RhdcPlaylist.serializer(), playlist)
    }

    fun serializeHack(hack: RhdcHack): String {
        return json.encodeToString(RhdcHack.serializer(), hack)
    }

    fun serializeStarLayout(layout: StarLayoutSchema): String {
        return json.encodeToString(StarLayoutSchema.serializer(), layout)
    }

    fun cleanDescription(rawDesc: String?): String {
        if (rawDesc == null) return ""
        var clean = rawDesc.replace(Regex("<[^>]*>"), "")
        clean = clean.replace("&quot;", "\"")
                     .replace("&#039;", "'")
                     .replace("&amp;", "&")
                     .replace("&lt;", "<")
                     .replace("&gt;", ">")
                     .replace("&nbsp;", " ")
        clean = clean.replace(Regex("\n{3,}"), "\n\n").trim()
        return clean
    }

    /**
     * Performs asynchronous HTTP GET request to fetch the live playlist manifest.
     */
    suspend fun fetchLivePlaylist(urlStr: String): ParallelManifest = withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Server returned HTTP ${connection.responseCode}")
        }

        val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        parsePlaylist(jsonStr)
    }

    suspend fun loginToRhdc(user: String, pass: String): String = withContext(Dispatchers.IO) {
        val url = URL("https://api.romhacking.com/v3/auth/login")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")

        val requestBody = json.encodeToString(LoginRequest.serializer(), LoginRequest(user, pass))
        connection.outputStream.use { os ->
            os.write(requestBody.toByteArray(Charsets.UTF_8))
        }

        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Login failed: HTTP $responseCode - $errorResponse")
        }

        val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val loginResponse = json.decodeFromString(LoginResponse.serializer(), jsonStr)
        loginResponse.token
    }

    suspend fun fetchUserCloudPlaylist(sessionToken: String): RhdcPlaylist = withContext(Dispatchers.IO) {
        val url = URL("https://api.romhacking.com/v3/hacks/following")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Authorization", "Bearer $sessionToken")
        connection.setRequestProperty("Accept", "application/json")
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Failed to fetch followed hacks: HTTP $responseCode - $errorResponse")
        }

        val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val followDtos = json.decodeFromString<List<RhdcFollowingHackDto>>(
            kotlinx.serialization.builtins.ListSerializer(RhdcFollowingHackDto.serializer()),
            jsonStr
        )

        val hacks = followDtos.map { dto ->
            val cleanDesc = cleanDescription(dto.description)
            val variants = dto.versions.filter { it.download?.directHref != null }.map { ver ->
                val decodedPath = URLDecoder.decode(ver.download!!.directHref!!, "UTF-8")
                val filename = decodedPath.substringAfterLast('/')
                RhdcHackVersion(
                    name = filename,
                    downloadUrl = "https://api.romhacking.com$decodedPath"
                )
            }

            val latestVersion = dto.versions.lastOrNull { it.download?.directHref != null }
            val dlUrl = if (latestVersion != null) {
                val decodedPath = URLDecoder.decode(latestVersion.download!!.directHref!!, "UTF-8")
                "https://api.romhacking.com$decodedPath"
            } else {
                ""
            }
            val vName = if (latestVersion != null) {
                val decodedPath = URLDecoder.decode(latestVersion.download?.directHref ?: "", "UTF-8")
                decodedPath.substringAfterLast('/', "1.0")
            } else {
                "1.0"
            }

            val finalImgUrl: String? = null

            RhdcHack(
                id = dto.hackId,
                title = dto.title,
                version = vName,
                description = cleanDesc,
                authors = dto.authors.map { it.username },
                downloadUrl = dlUrl,
                thumbnailUrl = finalImgUrl,
                starCount = dto.stars.toString(),
                bpsPatchUrl = dlUrl,
                playTime = dto.progress?.playTime ?: 0L,
                fileVariants = variants,
                playlists = dto.playlists
            )
        }

        RhdcPlaylist(
            id = "user_cloud_playlist",
            title = "Cloud Playlist",
            description = "User's followed hacks from Romhacking.com",
            author = "Romhacking.com",
            hacks = hacks
        )
    }
}
