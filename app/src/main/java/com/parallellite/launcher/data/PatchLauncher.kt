package com.parallellite.launcher.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.parallellite.launcher.data.remote.RomhackingApi
import com.parallellite.launcher.service.StarTrackerService
import com.parallellite.patching.PatchingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Downloads a BPS patch, applies it against the base ROM, stages the result to
 * shared storage (so RetroArch can read it), and deep-links into the ParaLLEl-N64
 * core. All heavy work runs on IO/Default dispatchers; callers supply the scope.
 */
class PatchLauncher(
    private val context: Context,
    private val api: RomhackingApi,
    private val sessionStore: SessionStore,
) {
    private val appContext = context.applicationContext

    sealed interface Result {
        data class Success(val rom: File) : Result
        data class Failure(val message: String) : Result
    }

    /** What the second-screen counter shows; null disables it. */
    data class TrackingInfo(
        val title: String,
        val thumbnailUrl: String?,
        val totalStars: Int,
    )

    /**
     * The file a previously-patched hack would occupy. When we have All-Files
     * access, this is the user's chosen output folder (or Download/SM64_Patches
     * as a default) so the ROM is visible; otherwise it falls back to internal
     * storage and gets staged to Download at launch time.
     */
    fun patchedRomFile(hackTitle: String, versionName: String): File {
        val name = "${sanitize(hackTitle)}_${sanitize(versionName)}.z64"
        return File(outputDir(), name)
    }

    /** Where patched ROMs are written: the user's folder, Download, or internal. */
    private fun outputDir(): File {
        if (hasAllFilesAccess()) {
            val chosen = sessionStore.patchedRomDir
            val dir = if (chosen.isNotEmpty()) File(chosen)
            else File(Environment.getExternalStorageDirectory(), "${Environment.DIRECTORY_DOWNLOADS}/$STAGE_DIR")
            if (dir.exists() || dir.mkdirs()) return dir
        }
        return appContext.filesDir
    }

    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun isInternal(file: File): Boolean =
        file.absolutePath.startsWith(appContext.filesDir.absolutePath)

    suspend fun downloadAndPatch(
        patchUrl: String,
        baseRomPath: String,
        hackId: String,
        hackTitle: String,
        versionName: String,
        gfxPlugin: String? = null,
        coreChoice: CoreChoice = CoreChoice.AUTO,
        tracking: TrackingInfo? = null,
        onProgress: (String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        try {
            onProgress("Downloading patch…")
            val cacheDir = appContext.cacheDir
            val download = File(cacheDir, "download_$hackId.tmp")
            api.download(patchUrl).byteStream().use { input ->
                FileOutputStream(download).use { output -> input.copyTo(output) }
            }

            onProgress("Resolving patch…")
            val bps = File(cacheDir, "patch_$hackId.bps")
            extractBps(download, bps)
            download.delete()

            onProgress("Preparing base ROM…")
            val base = resolveBaseRom(baseRomPath)

            onProgress("Applying BPS patch…")
            val output = patchedRomFile(hackTitle, versionName)
            val code = withContext(Dispatchers.Default) {
                PatchingEngine.applyBpsPatch(bps.absolutePath, base.file.absolutePath, output.absolutePath)
            }
            bps.delete()
            if (base.temporary) base.file.delete()

            if (code != 0) {
                return@withContext Result.Failure("Patching failed (code $code)")
            }
            // Verify the patched ROM actually landed before we launch anything.
            if (!output.exists() || output.length() < MIN_ROM_BYTES) {
                output.delete()
                return@withContext Result.Failure("Patched ROM was not created")
            }

            onProgress("Launching…")
            launch(output, tracking, gfxPlugin, coreChoice)
            Result.Success(output)
        } catch (e: Exception) {
            Log.e(TAG, "Patch pipeline failed", e)
            Result.Failure(e.message ?: "Unknown error")
        }
    }

    /** Extracts a `.bps` from a zip, or renames a raw patch into place. */
    private fun extractBps(source: File, target: File) {
        target.delete()
        if (isZip(source)) {
            ZipInputStream(source.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".bps", ignoreCase = true)) {
                        FileOutputStream(target).use { zis.copyTo(it) }
                        return
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            error("No .bps file found inside the zip archive")
        } else {
            source.copyTo(target, overwrite = true)
        }
    }

    private class BaseRom(val file: File, val temporary: Boolean)

    /** Decompresses the base ROM if it's zipped, otherwise uses it in place. */
    private fun resolveBaseRom(baseRomPath: String): BaseRom {
        val baseFile = File(baseRomPath)
        if (!baseFile.exists()) error("Base ROM not found")
        if (!isZip(baseFile)) return BaseRom(baseFile, temporary = false)

        ZipInputStream(baseFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory &&
                    (name.endsWith(".z64", ignoreCase = true) || name.endsWith(".v64", ignoreCase = true))
                ) {
                    val out = File(appContext.cacheDir, "base_${name.substringAfterLast('/')}")
                    FileOutputStream(out).use { zis.copyTo(it) }
                    return BaseRom(out, temporary = true)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        error("No .z64 or .v64 file found inside the base ROM archive")
    }

    /** Deletes a patched ROM from internal storage and its staged shared copy. */
    fun deletePatched(rom: File) {
        rom.delete()
        val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/$STAGE_DIR"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                appContext.contentResolver.delete(
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf("$relativeDir/", rom.name),
                )
            }
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$STAGE_DIR/${rom.name}").delete()
        }
    }

    /** Launches [rom] via the chosen target (RetroArch core, or the M64Plus FZ app). */
    fun launch(
        rom: File,
        tracking: TrackingInfo? = null,
        gfxPlugin: String? = null,
        coreChoice: CoreChoice = CoreChoice.AUTO,
    ) {
        // Start the manual second-screen counter (if requested) before we leave the
        // foreground, so the service launch is allowed. Works for any emulator.
        if (tracking != null) {
            StarTrackerService.start(appContext, tracking.title, tracking.thumbnailUrl, tracking.totalStars)
        }
        // OGRE-recommended hacks want the old Jabo/Rice-style renderer, which on
        // Android means M64Plus FZ (Rice/Glide64). Route them there on Auto.
        val autoOgre = coreChoice == CoreChoice.AUTO && gfxPlugin?.trim()?.equals("OGRE", ignoreCase = true) == true
        if (!coreChoice.isRetroArch || autoOgre) {
            launchM64PlusFz(rom)
        } else {
            launchRetroArch(rom, coreChoice.libName ?: coreLibFor(gfxPlugin))
        }
    }

    /**
     * Opens the standalone M64Plus FZ app on the patched ROM via ACTION_VIEW.
     * M64Plus can't read a content URI backed by our private storage, so we copy
     * the ROM to a real file in shared Downloads (needs All-Files access) and hand
     * it a URI backed by that file.
     */
    private fun launchM64PlusFz(rom: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            requestAllFilesAccess()
            return
        }
        // FileProvider's external-path can only serve primary shared storage. If the
        // ROM already lives there (the user's output folder), use it in place;
        // otherwise copy it into Download so M64Plus has a readable file.
        val primaryExternal = Environment.getExternalStorageDirectory().absolutePath
        val publicFile = if (rom.absolutePath.startsWith(primaryExternal) && !isInternal(rom)) {
            rom
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), STAGE_DIR)
            dir.mkdirs()
            File(dir, rom.name).also { runCatching { rom.copyTo(it, overwrite = true) } }
        }

        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", publicFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
            resolveM64PlusPackage()?.let { setPackage(it) }
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch M64Plus FZ", e)
        }
    }

    /** Sends the user to grant All-Files access (needed to stage ROMs for M64Plus FZ). */
    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) return
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun resolveM64PlusPackage(): String? =
        listOf("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita")
            .firstOrNull { pkg ->
                runCatching { appContext.packageManager.getPackageInfo(pkg, 0) }.isSuccess
            }

    /** Launches [rom] in RetroArch, staging it to shared storage only if needed. */
    private fun launchRetroArch(rom: File, coreLib: String) {
        // If the ROM is already in shared storage (the user's output folder),
        // RetroArch can read it directly; only internal files need staging.
        val stagedPath = if (isInternal(rom)) stageToDownloads(rom) else rom.absolutePath
        val pkg = resolveRetroArchPackage()
        val configPath = "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg"

        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", stagedPath)
            putExtra("LIBRETRO", "/data/data/$pkg/cores/$coreLib")
            putExtra("CONFIGFILE", configPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Writes the ROM into the public Downloads/SM64_Patches folder and returns a
     * filesystem path RetroArch can open. Uses MediaStore on API 29+ (scoped
     * storage) and a direct file on older devices.
     */
    private fun stageToDownloads(rom: File): String {
        val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/$STAGE_DIR"
        val publicPath = "${Environment.getExternalStorageDirectory().absolutePath}/$relativeDir/${rom.name}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            // Replace any prior copy so re-patching doesn't create duplicates.
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf("$relativeDir/", rom.name),
            )
            // IS_PENDING keeps the file hidden until the write is fully flushed, so
            // RetroArch can't cold-read a half-written ROM (which showed as a black
            // screen that "fixed itself" on a retry).
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, rom.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: error("Could not stage ROM to shared storage")
            resolver.openOutputStream(uri)?.use { out -> rom.inputStream().use { it.copyTo(out) } }
                ?: error("Could not open output stream for staged ROM")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), STAGE_DIR)
            if (!dir.exists()) dir.mkdirs()
            rom.copyTo(File(dir, rom.name), overwrite = true)
        }
        return publicPath
    }

    /**
     * Maps the hack's recommended RHDC graphics plugin to a RetroArch N64 core.
     * GLideN64-family plugins run best on Mupen64Plus-Next; ParaLLEl/Angrylion
     * (and unknown/none) fall back to the ParaLLEl-N64 core.
     */
    private fun coreLibFor(plugin: String?): String = when (plugin?.trim()?.lowercase()) {
        // On Android the Mupen64Plus-Next core ships as the GLES3 build.
        "gliden64", "glide64", "rice" -> "mupen64plus_next_gles3_libretro_android.so"
        // OGRE is Parallel-N64 + GLideN64 + the SM64-editor option (set in RetroArch).
        else -> "parallel_n64_libretro_android.so"
    }

    private fun resolveRetroArchPackage(): String = try {
        appContext.packageManager.getPackageInfo("com.retroarch.aarch64", 0)
        "com.retroarch.aarch64"
    } catch (e: Exception) {
        "com.retroarch"
    }

    private companion object {
        const val TAG = "PatchLauncher"
        const val STAGE_DIR = "SM64_Patches"
        // A valid N64 ROM is far bigger than this; used to reject empty/failed patches.
        const val MIN_ROM_BYTES = 1024L

        // Strip patch/archive extensions so a variant named "hack_v1.2.bps"
        // produces "hack_v1.2.z64", not the confusing "hack_v1.2.bps.z64".
        private val patchExtensions = Regex("""\.(bps|ips|ups|xdelta|zip|7z|rar)$""", RegexOption.IGNORE_CASE)

        fun sanitize(value: String): String =
            value.replace(patchExtensions, "").replace(Regex("[\\\\/:*?\"<>|]"), "_")

        fun isZip(file: File): Boolean {
            if (file.name.endsWith(".zip", ignoreCase = true)) return true
            if (file.length() < 4) return false
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            return header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        }
    }
}
