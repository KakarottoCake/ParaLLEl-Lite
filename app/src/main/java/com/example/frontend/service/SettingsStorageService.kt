package com.example.frontend.service

import android.content.Context
import java.io.File

class SettingsStorageService(private val context: Context) {

    private val prefs = context.getSharedPreferences("sm64_launcher_prefs", Context.MODE_PRIVATE)

    fun getPatchedRomDir(): String? {
        return prefs.getString("patched_rom_dir", null)
    }

    fun setPatchedRomDir(path: String) {
        prefs.edit().putString("patched_rom_dir", path).apply()
    }
}
