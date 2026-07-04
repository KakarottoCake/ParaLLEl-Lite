package com.parallellite.launcher.ui.launcher

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parallellite.launcher.data.model.Hack
import com.parallellite.launcher.ui.launcher.components.FilterStatusBar
import com.parallellite.launcher.ui.launcher.components.HackDetailPanel
import com.parallellite.launcher.ui.launcher.components.HackGrid
import com.parallellite.launcher.ui.launcher.components.HeaderBar
import com.parallellite.launcher.ui.launcher.components.SettingsDialog
import com.parallellite.launcher.ui.launcher.components.VersionDialog
import com.parallellite.launcher.ui.login.LoginScreen
import com.parallellite.launcher.ui.theme.BrandColors
import java.io.File

@Composable
fun LauncherScreen(viewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()

    var hackToPatch by remember { mutableStateOf<Hack?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val baseRomPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val dest = File(context.filesDir, "baserom.z64")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            viewModel.setBaseRomPath(dest.absolutePath)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val path = absolutePathFromTreeUri(uri) ?: uri.path
        if (path != null) viewModel.setPatchedRomDir(path)
    }

    // Force the settings dialog open until an output directory has been chosen.
    val settingsVisible = showSettings || state.patchedRomDir.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BrandColors.BackgroundTop, BrandColors.Background)))
                .padding(16.dp),
        ) {
            HeaderBar(
                isSignedIn = state.isSignedIn,
                baseRomPath = state.baseRomPath,
                onLogout = viewModel::logout,
                onPickBaseRom = { baseRomPicker.launch("*/*") },
                onOpenSettings = { showSettings = true },
                modifier = Modifier.padding(bottom = 16.dp),
            )

            FilterStatusBar(
                filter = state.filter,
                status = state.status,
                isLoading = state.isLoading,
                onFilterSelected = viewModel::setFilter,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HackGrid(
                    hacks = state.filteredHacks,
                    selectedHackId = state.selectedHack?.id,
                    enabled = state.hasBaseRom,
                    thumbnailProvider = viewModel::thumbnailUrl,
                    onHackFocused = { viewModel.selectHack(it.id) },
                    onHackClicked = { hack -> onHackActivated(hack, viewModel) { hackToPatch = it } },
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                )

                val selected = state.selectedHack
                HackDetailPanel(
                    hack = selected,
                    isPatched = selected != null && viewModel.existingRom(selected) != null,
                    canPatch = state.hasBaseRom,
                    thumbnailProvider = viewModel::thumbnailUrl,
                    onPlay = { selected?.let { hack -> viewModel.existingRom(hack)?.let { viewModel.launchExisting(hack, it) } } },
                    onPatch = { hackToPatch = selected },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        hackToPatch?.let { hack ->
            VersionDialog(
                hack = hack,
                onDismiss = { hackToPatch = null },
                onConfirm = { variant ->
                    viewModel.patchAndLaunch(hack, variant)
                    hackToPatch = null
                },
            )
        }

        if (!state.isSignedIn) {
            LoginScreen(state = loginState, onLogin = viewModel::login)
        }
    }

    if (settingsVisible) {
        SettingsDialog(
            patchedRomDir = state.patchedRomDir,
            liveTrackingEnabled = state.liveTrackingEnabled,
            onLiveTrackingChange = { enabled ->
                viewModel.setLiveTracking(enabled)
                if (enabled) requestAllFilesAccess(context)
            },
            onPickFolder = { folderPicker.launch(null) },
            onDismiss = { showSettings = false },
        )
    }
}

/** Live tracking needs All-Files access to read RetroArch's save; send the user to grant it. */
private fun requestAllFilesAccess(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
    if (android.os.Environment.isExternalStorageManager()) return
    runCatching {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** If already patched, launch immediately; otherwise ask which variant to patch. */
private fun onHackActivated(hack: Hack, viewModel: LauncherViewModel, requestPatch: (Hack) -> Unit) {
    viewModel.selectHack(hack.id)
    val existing = viewModel.existingRom(hack)
    if (existing != null) viewModel.launchExisting(hack, existing) else requestPatch(hack)
}

private fun absolutePathFromTreeUri(uri: Uri): String? {
    val treeId = DocumentsContract.getTreeDocumentId(uri) ?: return null
    val split = treeId.split(":")
    if (split.size < 2) return null
    val (type, path) = split
    return if (type.equals("primary", ignoreCase = true)) {
        "${android.os.Environment.getExternalStorageDirectory().absolutePath}/$path"
    } else {
        "/storage/$type/$path"
    }
}
