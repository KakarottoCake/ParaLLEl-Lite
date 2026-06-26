package com.example.patching

import android.util.Log

object PatchingEngine {
    init {
        try {
            System.loadLibrary("flips_patcher")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("PatchingEngine", "Failed to load native flips_patcher library", e)
        }
    }

    /**
     * Applies a BPS patch to an input ROM, generating the output patched ROM.
     * Returns:
     *   0 on success (bps_ok)
     *   1 on bps_to_output (applied patch to its own output)
     *   2 on bps_not_this (input is not the correct base file for this patch)
     *   3 on bps_broken (patch is broken/malformed)
     *   4 on bps_io (patch could not be read)
     *   5 on bps_identical
     *   6 on bps_too_big
     *   7 on bps_out_of_mem
     *   8 on bps_canceled
     */
    external fun applyBpsPatch(patchPath: String, inputRomPath: String, outputRomPath: String): Int
}
