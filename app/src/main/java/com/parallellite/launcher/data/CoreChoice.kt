package com.parallellite.launcher.data

/**
 * How to launch a hack. AUTO/PARALLEL/MUPEN run inside RetroArch (the value is the
 * libretro core, or null for "use the hack's recommended plugin"); M64PLUS_FZ
 * launches the standalone M64Plus FZ app via an ACTION_VIEW intent.
 */
enum class CoreChoice(val libName: String?, val isRetroArch: Boolean) {
    AUTO(null, true),
    PARALLEL("parallel_n64_libretro_android.so", true),
    MUPEN("mupen64plus_next_gles3_libretro_android.so", true),
    M64PLUS_FZ(null, false),
}
