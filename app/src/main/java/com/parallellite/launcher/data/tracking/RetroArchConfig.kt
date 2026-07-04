package com.parallellite.launcher.data.tracking

import android.os.Environment
import java.io.File

/**
 * Generates the RetroArch config used when live star tracking is enabled. It
 * redirects saves into a shared folder this app can read and shortens the
 * autosave interval so the `.srm` flushes to disk frequently enough to track.
 *
 * NOTE: passing this as the RetroArch `CONFIGFILE` replaces RetroArch's global
 * config for the session (per-core options live in their own file and survive).
 * Reading the resulting save requires All-Files access, so this path is only
 * used behind the opt-in toggle.
 */
object RetroArchConfig {

    private val root: File
        get() = File(Environment.getExternalStorageDirectory(), "Download/SM64_Patches")

    val saveDir: File get() = File(root, "saves")

    /** The `.srm` RetroArch will write for a staged ROM (named after the content). */
    fun saveFileFor(romFile: File): File {
        val base = romFile.nameWithoutExtension
        return File(saveDir, "$base.srm")
    }

    /**
     * Writes the tracking config and returns its absolute path, or null if it
     * couldn't be written (e.g. missing All-Files access).
     */
    fun writeConfig(): String? {
        return try {
            saveDir.mkdirs()
            val cfg = File(root, "parallel_lite_tracking.cfg")
            cfg.writeText(
                buildString {
                    appendLine("savefile_directory = \"${saveDir.absolutePath}\"")
                    appendLine("savefiles_in_content_dir = \"false\"")
                    appendLine("sort_savefiles_enable = \"false\"")
                    appendLine("sort_savefiles_by_content_enable = \"false\"")
                    appendLine("autosave_interval = \"10\"")
                    appendLine("config_save_on_exit = \"false\"")
                },
            )
            cfg.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
