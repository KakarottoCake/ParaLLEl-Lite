package com.parallellite.launcher.data

import com.parallellite.launcher.data.model.Hack
import com.parallellite.launcher.data.model.HackVersion
import com.parallellite.launcher.data.remote.LoginRequest
import com.parallellite.launcher.data.remote.RhdcFollowingHackDto
import com.parallellite.launcher.data.remote.RomhackingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/**
 * Single source of truth for auth and hack metadata. The UI/ViewModel talks
 * only to this class, never directly to Retrofit.
 */
class HackRepository(
    private val api: RomhackingApi,
    private val sessionStore: SessionStore,
) {
    /** Authenticates and persists the token. Returns the token on success. */
    suspend fun login(username: String, password: String): String = withContext(Dispatchers.IO) {
        val response = api.login(LoginRequest(username, password))
        sessionStore.saveToken(response.token)
        response.token
    }

    fun logout() = sessionStore.clearToken()

    /** Fetches the signed-in user's followed hacks, mapped to domain models. */
    suspend fun fetchFollowedHacks(): List<Hack> = withContext(Dispatchers.IO) {
        api.following().map { it.toHack() }
    }

    private val thumbnailCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Resolves a hack's thumbnail URL (its first screenshot). The `following`
     * list carries no images, so this fetches `/v3/hacks/hack/{id}` on demand and
     * memoises the result. Returns null if the hack has no screenshots.
     */
    suspend fun thumbnailUrl(hackId: String): String? {
        thumbnailCache[hackId]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val href = api.hackDetail(hackId).screenshots.firstOrNull()?.directHref
                if (href.isNullOrBlank()) {
                    null
                } else {
                    (RomhackingApi.BASE_URL.trimEnd('/') + href).also { thumbnailCache[hackId] = it }
                }
            }.getOrNull()
        }
    }

    private fun RhdcFollowingHackDto.toHack(): Hack {
        val apiOrder = versions.mapNotNull { v ->
            val href = v.download?.directHref ?: return@mapNotNull null
            val decoded = URLDecoder.decode(href, "UTF-8")
            HackVersion(
                name = decoded.substringAfterLast('/'),
                downloadUrl = RomhackingApi.BASE_URL.trimEnd('/') + decoded,
                plugin = v.plugin,
                settings = settingLabels(v.hackFlags, v.pluginFlags),
            )
        }
        val variants = sortNewestFirst(apiOrder)
        return Hack(
            id = hackId,
            title = title,
            version = variants.firstOrNull()?.name ?: "1.0",
            description = cleanDescription(description),
            authors = authors.map { it.username },
            starCount = stars,
            thumbnailUrl = null,
            playlists = playlists,
            variants = variants,
            layoutUrl = layout?.directHref
                ?.let { RomhackingApi.BASE_URL.trimEnd('/') + URLDecoder.decode(it, "UTF-8") },
        )
    }

    companion object {
        private val versionNumberRegex = Regex("""\d+(?:\.\d+)*""")

        /** Maps RHDC hack/plugin flags to human-readable recommended-setting labels. */
        fun settingLabels(hackFlags: List<String>, pluginFlags: List<String>): List<String> {
            val labels = mutableListOf<String>()
            hackFlags.forEach {
                when (it) {
                    "big-eeprom" -> labels += "16 kB EEPROM"
                    "vi-hack" -> labels += "Overclock VI"
                    "no-overclock" -> labels += "Disable Overclock"
                    "dual-analog" -> labels += "Dual Analog"
                    "sd-card" -> labels += "SD Card"
                }
            }
            pluginFlags.forEach {
                when (it) {
                    "emulate-framebuffer" -> labels += "Emulate Framebuffer"
                    "accurate-depth-compare" -> labels += "Accurate Depth Compare"
                    "upscale-texrects" -> labels += "Upscale Texrects"
                    "widescreen" -> labels += "Widescreen"
                    "lle-rsp" -> labels += "LLE RSP"
                    "allow-hle-fallback" -> labels += "Allow HLE Fallback"
                }
            }
            return labels
        }

        /**
         * Sorts patch versions newest-first. The API returns them oldest→newest,
         * so recency = reversed order. If every filename carries a parseable
         * version number, we sort by that number instead (recency breaks ties).
         */
        fun sortNewestFirst(apiOrder: List<HackVersion>): List<HackVersion> {
            val byRecency = apiOrder.reversed()
            if (byRecency.size < 2) return byRecency
            val keys = byRecency.map { versionKey(it.name) }
            if (keys.any { it.isEmpty() }) return byRecency
            return byRecency.sortedWith { a, b -> compareVersions(versionKey(b.name), versionKey(a.name)) }
        }

        private fun versionKey(name: String): List<Int> =
            versionNumberRegex.find(name)?.value?.split('.')?.mapNotNull { it.toIntOrNull() } ?: emptyList()

        private fun compareVersions(a: List<Int>, b: List<Int>): Int {
            for (i in 0 until maxOf(a.size, b.size)) {
                val diff = a.getOrElse(i) { 0} - b.getOrElse(i) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }

        /** Strips HTML tags and decodes common entities from a hack description. */
        fun cleanDescription(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw
                .replace(Regex("<[^>]*>"), "")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }
    }
}
