package com.parallellite.launcher.ui.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.parallellite.launcher.ParallelLiteApp
import com.parallellite.launcher.R
import com.parallellite.launcher.data.CoreChoice
import com.parallellite.launcher.data.HackRepository
import com.parallellite.launcher.data.PatchLauncher
import com.parallellite.launcher.data.SessionStore
import com.parallellite.launcher.data.model.Hack
import com.parallellite.launcher.data.model.HackVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class LauncherViewModel(
    application: Application,
    private val sessionStore: SessionStore,
    private val repository: HackRepository,
    private val patchLauncher: PatchLauncher,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        LauncherUiState(
            isSignedIn = sessionStore.currentToken().isNotEmpty(),
            baseRomPath = sessionStore.baseRomPath,
            patchedRomDir = sessionStore.patchedRomDir,
            liveTrackingEnabled = sessionStore.liveTrackingEnabled,
            status = StatusMessage.Info(string(R.string.status_signin)),
        ),
    )
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    init {
        if (uiState.value.isSignedIn) refreshHacks()
    }

    // ── Auth ────────────────────────────────────────────────────────────────
    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginUiState(error = string(R.string.login_fill_fields))
            return
        }
        _loginState.value = LoginUiState(isLoggingIn = true)
        viewModelScope.launch {
            runCatching { repository.login(username, password) }
                .onSuccess {
                    _loginState.value = LoginUiState()
                    _uiState.update { it.copy(isSignedIn = true) }
                    refreshHacks()
                }
                .onFailure { e ->
                    _loginState.value = LoginUiState(error = e.message ?: string(R.string.login_failed))
                }
        }
    }

    fun logout() {
        repository.logout()
        _uiState.update {
            it.copy(
                isSignedIn = false,
                hacks = emptyList(),
                selectedHackId = null,
                status = StatusMessage.Info(string(R.string.status_signin)),
            )
        }
    }

    private fun refreshHacks() {
        _uiState.update { it.copy(isLoading = true, status = StatusMessage.Info(string(R.string.status_syncing))) }
        viewModelScope.launch {
            runCatching { repository.fetchFollowedHacks() }
                .onSuccess { hacks ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hacks = hacks,
                            status = StatusMessage.Success(getApplication<Application>().getString(R.string.status_synced, hacks.size)),
                        )
                    }
                }
                .onFailure { e ->
                    val message = e.message.orEmpty()
                    if ("401" in message || "Login" in message) {
                        logout()
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, status = StatusMessage.Error(getApplication<Application>().getString(R.string.error_sync_failed, message)))
                        }
                    }
                }
        }
    }

    // ── Selection & filtering ────────────────────────────────────────────────
    fun selectHack(hackId: String) = _uiState.update { it.copy(selectedHackId = hackId) }

    fun setFilter(filter: HackFilter) = _uiState.update { it.copy(filter = filter, selectedHackId = null) }

    // ── ROM paths ─────────────────────────────────────────────────────────────
    fun setBaseRomPath(path: String) {
        sessionStore.baseRomPath = path
        _uiState.update { it.copy(baseRomPath = path) }
    }

    fun setPatchedRomDir(path: String) {
        sessionStore.patchedRomDir = path
        _uiState.update { it.copy(patchedRomDir = path) }
        // Writing patched ROMs into an arbitrary folder needs All-Files access.
        patchLauncher.requestAllFilesAccess()
    }

    fun setLiveTracking(enabled: Boolean) {
        sessionStore.liveTrackingEnabled = enabled
        _uiState.update { it.copy(liveTrackingEnabled = enabled) }
    }

    /** Builds the second-screen counter payload when tracking is on. */
    private suspend fun buildTracking(hack: Hack): PatchLauncher.TrackingInfo? {
        if (!sessionStore.liveTrackingEnabled) return null
        return PatchLauncher.TrackingInfo(
            title = hack.title,
            thumbnailUrl = repository.thumbnailUrl(hack.id),
            totalStars = hack.starCount,
        )
    }

    // ── Patch / launch ──────────────────────────────────────────────────────
    /**
     * Returns an already-patched ROM for a hack, or null if none. Checks every
     * variant (not just the newest) so a hack with many patches still flips to
     * "Play" once any of its versions has been patched.
     */
    fun existingRom(hack: Hack): File? {
        for (variant in hack.variants) {
            val file = patchLauncher.patchedRomFile(hack.title, variant.name)
            if (file.exists()) return file
        }
        return patchLauncher.patchedRomFile(hack.title, hack.version).takeIf { it.exists() }
    }

    fun launchExisting(hack: Hack, rom: File) {
        viewModelScope.launch {
            runCatching {
                patchLauncher.launch(rom, buildTracking(hack), hack.recommendedPlugin, sessionStore.coreOverride(hack.id))
            }
        }
    }

    /** The user's core override for a hack (AUTO uses the recommended plugin). */
    fun coreOverrideFor(hackId: String): CoreChoice = sessionStore.coreOverride(hackId)

    fun setCoreOverride(hackId: String, choice: CoreChoice) {
        sessionStore.setCoreOverride(hackId, choice)
        _uiState.update { it.copy(coreOverrideVersion = it.coreOverrideVersion + 1) }
        // M64Plus FZ needs a real file in shared storage, so grab All-Files access now.
        if (choice == CoreChoice.M64PLUS_FZ) patchLauncher.requestAllFilesAccess()
    }

    /** Deletes any patched ROMs for a hack (across all its variants). */
    fun unpatch(hack: Hack) {
        (hack.variants.map { it.name } + hack.version).distinct().forEach { version ->
            patchLauncher.deletePatched(patchLauncher.patchedRomFile(hack.title, version))
        }
        _uiState.update {
            it.copy(
                patchGeneration = it.patchGeneration + 1,
                status = StatusMessage.Info(getApplication<Application>().getString(R.string.status_unpatched, hack.title)),
            )
        }
    }

    /** Lazily resolves (and caches) a hack's thumbnail URL for the grid. */
    suspend fun thumbnailUrl(hackId: String): String? = repository.thumbnailUrl(hackId)

    fun patchAndLaunch(hack: Hack, variant: HackVersion) {
        val basePath = uiState.value.baseRomPath
        if (basePath.isEmpty()) {
            _uiState.update { it.copy(status = StatusMessage.Error(string(R.string.error_no_base_rom))) }
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = patchLauncher.downloadAndPatch(
                patchUrl = variant.downloadUrl,
                baseRomPath = basePath,
                hackId = hack.id,
                hackTitle = hack.title,
                versionName = variant.name,
                gfxPlugin = variant.plugin,
                coreChoice = sessionStore.coreOverride(hack.id),
                tracking = buildTracking(hack),
                onProgress = { text -> _uiState.update { it.copy(status = StatusMessage.Info(text)) } },
            )
            _uiState.update {
                when (result) {
                    is PatchLauncher.Result.Success ->
                        it.copy(isLoading = false, patchGeneration = it.patchGeneration + 1, status = StatusMessage.Success(string(R.string.status_launch_ok)))
                    is PatchLauncher.Result.Failure ->
                        it.copy(isLoading = false, status = StatusMessage.Error(result.message))
                }
            }
        }
    }

    private fun string(resId: Int): String = getApplication<Application>().getString(resId)

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras,
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ParallelLiteApp
                val c = app.container
                return LauncherViewModel(app, c.sessionStore, c.hackRepository, c.patchLauncher) as T
            }
        }
    }
}
