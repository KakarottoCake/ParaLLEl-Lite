package com.parallellite.launcher.ui.launcher

import com.parallellite.launcher.data.model.Hack

/** Status line shown in the header; carries its own severity for colouring. */
sealed interface StatusMessage {
    data class Info(val text: String) : StatusMessage
    data class Success(val text: String) : StatusMessage
    data class Error(val text: String) : StatusMessage
}

data class LauncherUiState(
    val isSignedIn: Boolean = false,
    val hacks: List<Hack> = emptyList(),
    val filter: HackFilter = HackFilter.ALL,
    val selectedHackId: String? = null,
    val baseRomPath: String = "",
    val patchedRomDir: String = "",
    val liveTrackingEnabled: Boolean = false,
    val isLoading: Boolean = false,
    // Bumped whenever a ROM is patched or unpatched, so the UI re-checks which
    // hacks are already patched on disk.
    val patchGeneration: Int = 0,
    // Bumped when the user changes a hack's core override, so the picker re-reads.
    val coreOverrideVersion: Int = 0,
    val status: StatusMessage = StatusMessage.Info(""),
) {
    val filteredHacks: List<Hack> = filter.matchKey.let { key ->
        if (key == null) hacks
        else hacks.filter { hack -> hack.playlists.any { it.contains(key, ignoreCase = true) } }
    }

    val selectedHack: Hack? =
        filteredHacks.firstOrNull { it.id == selectedHackId } ?: filteredHacks.firstOrNull()

    val hasBaseRom: Boolean = baseRomPath.isNotEmpty()
}

/** State for the login overlay. */
data class LoginUiState(
    val isLoggingIn: Boolean = false,
    val error: String? = null,
)
