package com.parallellite.launcher.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the auth token in [EncryptedSharedPreferences] and user paths in
 * regular prefs. Exposes the token as a [StateFlow] so the UI reacts to
 * login/logout without polling.
 */
class SessionStore(context: Context) {

    private val appContext = context.applicationContext

    private val securePrefs = run {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val prefs = appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(securePrefs.getString(KEY_TOKEN, null).orEmpty())
    val token: StateFlow<String> = _token.asStateFlow()

    fun currentToken(): String = _token.value

    fun saveToken(token: String) {
        securePrefs.edit().putString(KEY_TOKEN, token).apply()
        _token.value = token
    }

    fun clearToken() {
        securePrefs.edit().remove(KEY_TOKEN).apply()
        _token.value = ""
    }

    var baseRomPath: String
        get() = prefs.getString(KEY_BASE_ROM, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_ROM, value).apply()

    var patchedRomDir: String
        get() = prefs.getString(KEY_PATCHED_DIR, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PATCHED_DIR, value).apply()

    var liveTrackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIVE_TRACK, false)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_TRACK, value).apply()

    fun coreOverride(hackId: String): CoreChoice =
        prefs.getString("$KEY_CORE_PREFIX$hackId", null)
            ?.let { runCatching { CoreChoice.valueOf(it) }.getOrNull() }
            ?: CoreChoice.AUTO

    fun setCoreOverride(hackId: String, choice: CoreChoice) =
        prefs.edit().putString("$KEY_CORE_PREFIX$hackId", choice.name).apply()

    private companion object {
        const val SECURE_PREFS = "secure_prefs"
        const val APP_PREFS = "sm64_launcher_prefs"
        const val KEY_TOKEN = "session_token"
        const val KEY_BASE_ROM = "base_rom_path"
        const val KEY_PATCHED_DIR = "patched_rom_dir"
        const val KEY_LIVE_TRACK = "live_tracking_enabled"
        const val KEY_CORE_PREFIX = "core_override_"
    }
}
