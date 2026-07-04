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
        val variants = versions
            .mapNotNull { it.download?.directHref }
            .map { href ->
                val decoded = URLDecoder.decode(href, "UTF-8")
                HackVersion(
                    name = decoded.substringAfterLast('/'),
                    downloadUrl = RomhackingApi.BASE_URL.trimEnd('/') + decoded,
                )
            }
        return Hack(
            id = hackId,
            title = title,
            version = variants.lastOrNull()?.name ?: "1.0",
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

    /** Downloads the raw star-layout JSON for a hack, or null if it has none. */
    suspend fun layoutJson(layoutUrl: String?): String? {
        if (layoutUrl.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching { api.download(layoutUrl).string() }.getOrNull()
        }
    }

    companion object {
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
