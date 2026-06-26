package com.example.frontend

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.app.Activity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.frontend.service.RomHackPipelineService
import com.example.frontend.service.SettingsStorageService
import com.example.emulator.metadata.RomhackingMetadataService
import com.example.emulator.metadata.RhdcHack
import com.example.frontend.ui.LoginScreen
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var pipelineService: RomHackPipelineService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pipelineService = RomHackPipelineService(this)
        setContent {
            SM64LauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LauncherScreen(pipelineService)
                }
            }
        }
    }
}

@Composable
fun SM64LauncherTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFFFFCC00),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(pipelineService: RomHackPipelineService) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sm64_launcher_prefs", Context.MODE_PRIVATE) }

    // ── State ─────────────────────────────────────────────────────────────────
    var sessionToken      by remember { mutableStateOf(prefs.getString("session_token", "") ?: "") }
    var baseRomPath       by remember { mutableStateOf(prefs.getString("base_rom_path", "") ?: "") }
    var hacks             by remember { mutableStateOf(emptyList<RhdcHack>()) }
    var selectedIndex     by remember { mutableStateOf(0) }
    var logMessage        by remember { mutableStateOf("Ready to patch & launch") }
    var isLoading         by remember { mutableStateOf(false) }
    var filterExpanded    by remember { mutableStateOf(false) }
    var selectedFilter    by remember { mutableStateOf("All Hack Lists") }
    var showVersionDialog by remember { mutableStateOf(false) }
    var hackToPatch       by remember { mutableStateOf<RhdcHack?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val settingsStorage = remember { SettingsStorageService(context) }
    var patchedRomDir by remember { mutableStateOf(settingsStorage.getPatchedRomDir() ?: "") }

    val filterOptions = listOf("All Hack Lists", "Want To Play", "In Progress", "Completed")
    val coroutineScope = rememberCoroutineScope()

    // ── Derived state ─────────────────────────────────────────────────────────
    val filteredHacks = remember(hacks, selectedFilter) {
        if (selectedFilter == "All Hack Lists") hacks
        else hacks.filter { hack ->
            hack.playlists.any {
                it.contains(selectedFilter, ignoreCase = true) || it.equals(selectedFilter, ignoreCase = true)
            }
        }
    }

    // ── Launchers ─────────────────────────────────────────────────────────────
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { e.printStackTrace() }
            val path = getAbsolutePathFromTreeUri(context, it) ?: it.path
            path?.let { p ->
                patchedRomDir = p
                settingsStorage.setPatchedRomDir(p)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val destinationFile = File(context.filesDir, "baserom.z64")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destinationFile.outputStream().use { output -> input.copyTo(output) }
                }
                baseRomPath = destinationFile.absolutePath
                prefs.edit().putString("base_rom_path", baseRomPath).apply()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled by OS */ }

    // ── Effects ───────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    LaunchedEffect(sessionToken) {
        if (sessionToken.isNotEmpty()) {
            isLoading = true
            logMessage = "Syncing cloud playlist..."
            try {
                val cloudPlaylist = RomhackingMetadataService.fetchUserCloudPlaylist(sessionToken)
                hacks = cloudPlaylist.hacks
                logMessage = "Playlist synchronized: ${hacks.size} hacks loaded."
            } catch (e: Exception) {
                e.printStackTrace()
                logMessage = "ERROR: Failed to sync playlist: ${e.message}"
                if (e.message?.contains("401") == true || e.message?.contains("Login") == true) {
                    sessionToken = ""
                    prefs.edit().remove("session_token").apply()
                }
            } finally {
                isLoading = false
            }
        } else {
            hacks = emptyList()
            logMessage = "Sign in to sync your playlist."
        }
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────
    val dismissDialog: () -> Unit = {
        showVersionDialog = false
        hackToPatch = null
    }

    val triggerPatchingPipeline: (RhdcHack, String, String) -> Unit = { hack, customUrl, customVersionName ->
        if (baseRomPath.isEmpty()) {
            logMessage = "ERROR: You must configure a clean base ROM first!"
        } else {
            isLoading = true
            logMessage = "Initiating patching flow..."
            pipelineService.processAndLaunch(
                patchUrl = customUrl,
                baseRomPath = baseRomPath,
                hackId = hack.id,
                hackName = hack.title,
                maxStars = hack.starCount.toIntOrNull() ?: 0,
                sessionToken = sessionToken,
                versionName = customVersionName,
                onProgress = { progressText -> logMessage = progressText },
                onComplete = { success, _, errorMsg ->
                    isLoading = false
                    logMessage = when {
                        success      -> "Launch successful!"
                        errorMsg != null -> "ERROR: $errorMsg"
                        else         -> "Pipeline failed."
                    }
                }
            )
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Color(0xFF1A1A1A), Color(0xFF0F0F0F))))
                .padding(16.dp)
        ) {
            // ── Header Row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ParaLLEl Lite",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCC00)
                    )
                    Text(
                        text = "This could wake up Ethan",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (sessionToken.isNotEmpty()) {
                        Button(
                            onClick = {
                                prefs.edit().remove("session_token").apply()
                                sessionToken = ""
                                hacks = emptyList()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "Logout", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (baseRomPath.isEmpty()) Color(0xFF8B0000) else Color(0xFF2E2E2E)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .width(320.dp)
                            .border(
                                width = 2.dp,
                                color = if (baseRomPath.isEmpty()) Color.Red else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Text(
                            text = if (baseRomPath.isEmpty()) "WARNING: Click to Set Base ROM (.z64)"
                                   else "Base ROM: ...${baseRomPath.takeLast(25)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // ── Filter + Status Row ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .focusable()
                        .clickable { filterExpanded = true }
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Filter: $selectedFilter", color = Color.White, fontSize = 14.sp)
                        Text(text = if (filterExpanded) "▲" else "▼", color = Color.Gray, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false }
                    ) {
                        filterOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedFilter = option
                                    filterExpanded = false
                                }
                            )
                        }
                    }
                }

                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFFFFCC00),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = logMessage,
                            color = if (logMessage.startsWith("ERROR")) Color.Red else Color.Green,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Main Content Row (Grid + Detail Panel) ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hack card grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredHacks) { index, hack ->
                        val isFocused = selectedIndex == index
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .onFocusChanged { state ->
                                    if (state.isFocused) selectedIndex = index
                                }
                                .clickable(enabled = baseRomPath.isNotEmpty()) {
                                    val targetFile = pipelineService.getPatchedRomFile(hack.title, hack.version)
                                    if (targetFile.exists()) {
                                        pipelineService.launchRetroArch(targetFile)
                                    } else {
                                        hackToPatch = hack
                                        showVersionDialog = true
                                    }
                                }
                                .focusable()
                                .background(
                                    color = if (isFocused) Color(0xFF2E2E2E) else Color(0xFF1E1E1E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) Color(0xFFFFCC00) else Color(0xFF333333),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = hack.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (isFocused) Color(0xFFFFCC00) else Color.White
                                )
                            }
                        }
                    }
                }

                // Detail panel
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxHeight().padding(16.dp)) {
                        val activeHack = filteredHacks.getOrNull(selectedIndex)
                        if (activeHack != null) {
                            Text(
                                text = activeHack.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFFFFCC00)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⭐ ${activeHack.starCount} Stars",
                                fontSize = 14.sp,
                                color = Color(0xFFFFCC00)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Version: ${activeHack.version}",
                                fontSize = 14.sp,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            val descriptionScroll = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(descriptionScroll)
                                    .padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = activeHack.description ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    lineHeight = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            val targetFile = pipelineService.getPatchedRomFile(activeHack.title, activeHack.version)
                            if (targetFile.exists()) {
                                Button(
                                    onClick = { pipelineService.launchRetroArch(targetFile) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Play", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        hackToPatch = activeHack
                                        showVersionDialog = true
                                    },
                                    enabled = baseRomPath.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCC00)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Patch / Download", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Version Picker Dialog ─────────────────────────────────────────────
        if (showVersionDialog && hackToPatch != null) {
            val variants = hackToPatch!!.fileVariants
            AlertDialog(
                onDismissRequest = { dismissDialog() },
                title = { Text("Select Patch Version", color = Color(0xFFFFCC00), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (variants.isEmpty()) {
                            Text("No patch versions available for this hack.", color = Color.LightGray)
                        } else {
                            var selectedVariantIndex by remember { mutableStateOf(0) }
                            val scrollState = rememberScrollState()
                            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(scrollState)) {
                                variants.forEachIndexed { vIndex, variant ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusable()
                                            .clickable { selectedVariantIndex = vIndex }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = (selectedVariantIndex == vIndex),
                                            onClick = { selectedVariantIndex = vIndex }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = variant.name, color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val chosenVariant = variants[selectedVariantIndex]
                                    triggerPatchingPipeline(hackToPatch!!, chosenVariant.downloadUrl, chosenVariant.name)
                                    dismissDialog()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCC00)),
                                modifier = Modifier.fillMaxWidth().focusable()
                            ) {
                                Text("Download & Patch", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { dismissDialog() },
                        modifier = Modifier.focusable()
                    ) {
                        Text("Cancel", color = Color(0xFFFFCC00))
                    }
                },
                containerColor = Color(0xFF1E1E1E)
            )
        }

        // ── Login overlay ─────────────────────────────────────────────────────
        if (sessionToken.isEmpty()) {
            LoginScreen(
                sessionToken = sessionToken,
                onSessionTokenChanged = { sessionToken = it },
                coroutineScope = coroutineScope
            )
        }
    }

    // ── Settings Dialog ───────────────────────────────────────────────────────
    val showSettings = showSettingsDialog || patchedRomDir.isEmpty()
    if (showSettings) {
        AlertDialog(
            onDismissRequest = {
                if (patchedRomDir.isNotEmpty()) showSettingsDialog = false
            },
            title = { Text("Paths Settings", color = Color(0xFFFFCC00), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Configure Output Directory for Patched ROMs:", color = Color.White, fontSize = 14.sp)
                    OutlinedTextField(
                        value = patchedRomDir,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Output Folder", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFCC00),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCC00)),
                        modifier = Modifier.fillMaxWidth().focusable()
                    ) {
                        Text("Select Folder", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (patchedRomDir.isNotEmpty()) showSettingsDialog = false },
                    enabled = patchedRomDir.isNotEmpty(),
                    modifier = Modifier.focusable()
                ) {
                    Text("Close", color = Color(0xFFFFCC00))
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

fun getAbsolutePathFromTreeUri(context: Context, uri: Uri): String? {
    val treeId = android.provider.DocumentsContract.getTreeDocumentId(uri) ?: return null
    val split = treeId.split(":")
    val type = split[0]
    return if ("primary".equals(type, ignoreCase = true)) {
        android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
    } else {
        "/storage/$type/${split[1]}"
    }
}
