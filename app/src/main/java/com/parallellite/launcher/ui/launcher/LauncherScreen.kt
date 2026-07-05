package com.parallellite.launcher.ui.launcher

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.parallellite.launcher.ui.launcher.components.SettingsDialog
import com.parallellite.launcher.ui.launcher.pages.HackDetailPage
import com.parallellite.launcher.ui.launcher.pages.HackListPage
import com.parallellite.launcher.ui.launcher.pages.VersionPickerPage
import com.parallellite.launcher.ui.login.LoginScreen
import com.parallellite.launcher.ui.theme.BrandColors
import java.io.File

@Composable
fun LauncherScreen(viewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()

    var page by remember { mutableStateOf<LauncherPage>(LauncherPage.List) }
    var showSettings by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored; the foreground-service notification shows regardless */ }

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

    // Sign-out returns to the list.
    LaunchedEffect(state.isSignedIn) { if (!state.isSignedIn) page = LauncherPage.List }

    // System back navigates up the page stack.
    BackHandler(enabled = state.isSignedIn && page != LauncherPage.List) {
        page = when (val p = page) {
            is LauncherPage.Versions -> LauncherPage.Detail(p.hackId)
            else -> LauncherPage.List
        }
    }

    val settingsVisible = showSettings || state.patchedRomDir.isEmpty()
    fun hackById(id: String): Hack? = state.hacks.firstOrNull { it.id == id }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BrandColors.BackgroundTop, BrandColors.Background))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            when (val p = page) {
                is LauncherPage.List -> HackListPage(
                    hacks = state.filteredHacks,
                    isSignedIn = state.isSignedIn,
                    baseRomPath = state.baseRomPath,
                    filter = state.filter,
                    status = state.status,
                    isLoading = state.isLoading,
                    thumbnailProvider = viewModel::thumbnailUrl,
                    onFilterSelected = viewModel::setFilter,
                    onLogout = viewModel::logout,
                    onPickBaseRom = { baseRomPicker.launch("*/*") },
                    onOpenSettings = { showSettings = true },
                    onHackClick = { page = LauncherPage.Detail(it.id) },
                )

                is LauncherPage.Detail -> {
                    val hack = hackById(p.hackId)
                    if (hack == null) {
                        LaunchedEffect(p) { page = LauncherPage.List }
                    } else {
                        HackDetailPage(
                            hack = hack,
                            isPatched = state.patchGeneration.let { _ -> viewModel.existingRom(hack) != null },
                            canPatch = state.hasBaseRom,
                            coreChoice = state.coreOverrideVersion.let { _ -> viewModel.coreOverrideFor(hack.id) },
                            onCoreChange = { viewModel.setCoreOverride(hack.id, it) },
                            onBack = { page = LauncherPage.List },
                            onPlay = { viewModel.existingRom(hack)?.let { viewModel.launchExisting(hack, it) } },
                            onPatch = { page = LauncherPage.Versions(hack.id) },
                            onUnpatch = { viewModel.unpatch(hack) },
                        )
                    }
                }

                is LauncherPage.Versions -> {
                    val hack = hackById(p.hackId)
                    if (hack == null) {
                        LaunchedEffect(p) { page = LauncherPage.List }
                    } else {
                        VersionPickerPage(
                            hack = hack,
                            onBack = { page = LauncherPage.Detail(hack.id) },
                            onPick = { variant ->
                                viewModel.patchAndLaunch(hack, variant)
                                page = LauncherPage.Detail(hack.id)
                            },
                        )
                    }
                }
            }
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
                if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onPickFolder = { folderPicker.launch(null) },
            onDismiss = { showSettings = false },
        )
    }
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
