package com.parallellite.launcher.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.parallellite.launcher.data.remote.RomhackingApi
import com.parallellite.launcher.data.tracking.RetroArchConfig
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
) {
    private val appContext = context.applicationContext

    sealed interface Result {
        data class Success(val rom: File) : Result
        data class Failure(val message: String) : Result
    }

    /** Everything the second-screen tracker needs; null disables live tracking. */
    data class TrackingInfo(
        val title: String,
        val thumbnailUrl: String?,
        val totalStars: Int,
        val layoutJson: String?,
    )

    /** The internal-storage file a previously patched hack would occupy. */
    fun patchedRomFile(hackTitle: String, versionName: String): File {
        val name = "${sanitize(hackTitle)}_${sanitize(versionName)}.z64"
        return File(appContext.filesDir, name)
    }

    suspend fun downloadAndPatch(
        patchUrl: String,
        baseRomPath: String,
        hackId: String,
        hackTitle: String,
        versionName: String,
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

            onProgress("Launching RetroArch…")
            launch(output, tracking)
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

    /** Stages [rom] to shared Downloads and launches it in RetroArch. */
    fun launch(rom: File, tracking: TrackingInfo? = null) {
        val stagedPath = stageToDownloads(rom)
        val pkg = resolveRetroArchPackage()

        // With live tracking we hand RetroArch a config that redirects saves to a
        // folder we can read; requires All-Files access, so fall back to the
        // default config (and no tracking) if we can't write it.
        val canTrack = tracking != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        val trackingConfig = if (canTrack) RetroArchConfig.writeConfig() else null
        val configPath = trackingConfig ?: "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg"

        // Start the tracker before leaving foreground so the service launch is allowed.
        if (tracking != null && trackingConfig != null) {
            StarTrackerService.start(
                context = appContext,
                title = tracking.title,
                thumbnailUrl = tracking.thumbnailUrl,
                totalStars = tracking.totalStars,
                saveFilePath = RetroArchConfig.saveFileFor(rom).absolutePath,
                layoutJson = tracking.layoutJson,
            )
        }

        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", stagedPath)
            putExtra("LIBRETRO", "/data/data/$pkg/cores/parallel_n64_libretro_android.so")
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
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, rom.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            }
            val uri = resolver.insert(collection, values)
                ?: error("Could not stage ROM to shared storage")
            resolver.openOutputStream(uri)?.use { out -> rom.inputStream().use { it.copyTo(out) } }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), STAGE_DIR)
            if (!dir.exists()) dir.mkdirs()
            rom.copyTo(File(dir, rom.name), overwrite = true)
        }
        return publicPath
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

        fun sanitize(value: String): String =
            value.replace(".zip", "", ignoreCase = true).replace(Regex("[\\\\/:*?\"<>|]"), "_")

        fun isZip(file: File): Boolean {
            if (file.name.endsWith(".zip", ignoreCase = true)) return true
            if (file.length() < 4) return false
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            return header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        }
    }
}
