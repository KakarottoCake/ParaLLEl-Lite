package com.example.frontend.service

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import com.example.patching.PatchingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.net.HttpURLConnection
import java.net.URL

class RomHackPipelineService(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Downloads a BPS patch from [patchUrl], applies it to [baseRomPath],
     * writes the result to app-private internal storage, then launches Gopher64.
     */
    fun processAndLaunch(
        patchUrl: String,
        baseRomPath: String,
        hackId: String,
        hackName: String,
        maxStars: Int,
        sessionToken: String,
        versionName: String,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, File?, String?) -> Unit
    ) {
        coroutineScope.launch {
            try {
                // ── 1. Download patch ──────────────────────────────────────────
                onProgress("Downloading patch...")
                val cacheDir = context.cacheDir
                val isZipUrl = patchUrl.endsWith(".zip", ignoreCase = true)
                val downloadTarget = if (isZipUrl) {
                    File(cacheDir, "temp_download_$hackId.zip")
                } else {
                    File(cacheDir, "temp_download_$hackId.tmp")
                }

                downloadFile(patchUrl, downloadTarget, sessionToken) { progress ->
                    onProgress("Downloading patch: ${(progress * 100).toInt()}%")
                }

                // ── 2. Resolve / extract BPS file ──────────────────────────────
                val tempBpsFile = File(cacheDir, "temp_patch_$hackId.bps")
                var isZip = isZipUrl
                if (!isZip && downloadTarget.exists() && downloadTarget.length() >= 4) {
                    val header = ByteArray(4)
                    downloadTarget.inputStream().use { it.read(header) }
                    if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) isZip = true
                }

                if (isZip) {
                    onProgress("Extracting ZIP patch...")
                    val zipFile = if (isZipUrl) downloadTarget else {
                        val z = File(cacheDir, "temp_download_$hackId.zip")
                        if (z.exists()) z.delete()
                        downloadTarget.renameTo(z)
                        z
                    }

                    var bpsExtracted = false
                    ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".bps", ignoreCase = true)) {
                                FileOutputStream(tempBpsFile).use { zis.copyTo(it) }
                                bpsExtracted = true
                                zis.closeEntry()
                                break
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                    if (zipFile.exists()) zipFile.delete()
                    if (!bpsExtracted) throw Exception("No .bps file found inside the zip archive")
                } else {
                    if (downloadTarget.exists()) {
                        if (tempBpsFile.exists()) tempBpsFile.delete()
                        downloadTarget.renameTo(tempBpsFile)
                    }
                }

                // ── 3. Resolve output file (app-private internal storage) ──────
                onProgress("Applying BPS patch...")
                val outputName = "${hackName}_$versionName"
                    .replace(".zip", "", ignoreCase = true)
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val outputRomFile = File(context.filesDir, "$outputName.z64")

                // ── 4. Optionally decompress base ROM ──────────────────────────
                var actualBaseRomPath = baseRomPath
                val baseRomFile = File(baseRomPath)
                var isBaseRomZip = baseRomPath.endsWith(".zip", ignoreCase = true)
                if (!isBaseRomZip && baseRomFile.exists() && baseRomFile.length() >= 4) {
                    val header = ByteArray(4)
                    baseRomFile.inputStream().use { it.read(header) }
                    if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) isBaseRomZip = true
                }

                var tempDecompressedBase: File? = null
                if (isBaseRomZip) {
                    onProgress("Extracting Base ROM from archive...")
                    ZipInputStream(FileInputStream(baseRomFile)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory &&
                                (entry.name.endsWith(".z64", ignoreCase = true) ||
                                 entry.name.endsWith(".v64", ignoreCase = true))
                            ) {
                                val decompressed = File(context.cacheDir, entry.name)
                                FileOutputStream(decompressed).use { zis.copyTo(it) }
                                tempDecompressedBase = decompressed
                                actualBaseRomPath = decompressed.absolutePath
                                zis.closeEntry()
                                break
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                    if (tempDecompressedBase == null) {
                        throw Exception("No .z64 or .v64 file found inside the base ROM archive")
                    }
                }

                // ── 5. Apply BPS patch ─────────────────────────────────────────
                val result = withContext(Dispatchers.Default) {
                    PatchingEngine.applyBpsPatch(
                        tempBpsFile.absolutePath,
                        actualBaseRomPath,
                        outputRomFile.absolutePath
                    )
                }

                tempDecompressedBase?.let { if (it.exists()) it.delete() }
                if (tempBpsFile.exists()) tempBpsFile.delete()

                if (result == 0) {
                    onProgress("Patch applied successfully! Launching RetroArch...")
                    launchRetroArch(outputRomFile)
                    onComplete(true, outputRomFile, null)
                } else {
                    Log.e(TAG, "Patching failed with code: $result")
                    onComplete(false, null, "Patching failed (Code: $result)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in pipeline", e)
                onComplete(false, null, e.message)
            }
        }
    }

    /**
     * Streams a file from [urlStr] to [destinationFile], following redirects and
     * forwarding [sessionToken] as a Bearer Authorization header.
     */
    private suspend fun downloadFile(
        urlStr: String,
        destinationFile: File,
        sessionToken: String,
        onProgressUpdate: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        var currentUrl = urlStr
        var cookies: String? = null
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                if (sessionToken.isNotEmpty()) setRequestProperty("Authorization", "Bearer $sessionToken")
                if (!cookies.isNullOrEmpty()) setRequestProperty("Cookie", cookies)
                connect()
            }

            val responseCode = connection.responseCode
            connection.getHeaderField("Set-Cookie")?.let { cookies = it }

            if (responseCode in listOf(
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_SEE_OTHER, 307, 308
                )
            ) {
                val location = connection.getHeaderField("Location")
                    ?: throw Exception("Redirect without Location header (HTTP $responseCode)")
                connection.disconnect()
                currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    URL(URL(currentUrl), location).toString()
                }
                redirectCount++
                continue
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()
                throw Exception("Server returned HTTP $responseCode: $errorMsg")
            }

            val fileLength = connection.contentLength
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) onProgressUpdate(total.toFloat() / fileLength)
                        output.write(buffer, 0, count)
                    }
                }
            }
            connection.disconnect()
            return@withContext
        }
        throw Exception("Too many redirects (max $MAX_REDIRECTS exceeded)")
    }

    /**
     * Returns the expected output file for a previously patched hack
     * from app-private internal storage.
     */
    fun getPatchedRomFile(hackName: String, versionName: String): File {
        val sanitize: (String) -> String = { s ->
            s.replace(".zip", "", ignoreCase = true).replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }
        val outputName = "${sanitize(hackName)}_${sanitize(versionName)}"
        return File(context.filesDir, "$outputName.z64")
    }

    /**
     * Copies [patchedRomFile] to public Downloads storage and launches it in RetroArch
     * via an absolute file path so the emulator can stream it natively without URI restrictions.
     */
    fun launchRetroArch(patchedRomFile: File) {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SM64_Patches")
        if (!publicDir.exists()) publicDir.mkdirs()

        val publicRomFile = File(publicDir, patchedRomFile.name)
        patchedRomFile.copyTo(publicRomFile, overwrite = true)

        val is64Bit = try {
            context.packageManager.getPackageInfo("com.retroarch.aarch64", 0)
            true
        } catch (e: Exception) {
            false
        }

        val pkgName = if (is64Bit) "com.retroarch.aarch64" else "com.retroarch"
        val corePath = "/data/data/$pkgName/cores/parallel_n64_libretro_android.so"
        val configPath = "/storage/emulated/0/Android/data/$pkgName/files/retroarch.cfg"

        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(pkgName, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", publicRomFile.absolutePath)
            putExtra("LIBRETRO", corePath)
            putExtra("CONFIGFILE", configPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "RomHackPipelineService"
        private const val MAX_REDIRECTS = 5
    }
}
