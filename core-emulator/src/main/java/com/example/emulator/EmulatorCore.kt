package com.example.emulator

import android.util.Log

class EmulatorCore {
    companion object {
        init {
            try {
                System.loadLibrary("gopher64")
            } catch (e: UnsatisfiedLinkError) {
                try {
                    System.loadLibrary("gopher64_rust_core")
                } catch (ex: UnsatisfiedLinkError) {
                    Log.e("EmulatorCore", "Failed to load native gopher64 library", ex)
                }
            }
        }

        /**
         * Initializes the emulator backend engine.
         */
        @JvmStatic
        external fun initialize(): Boolean

        /**
         * Loads a game ROM from the specified path.
         */
        @JvmStatic
        external fun loadRom(romPath: String): Boolean

        /**
         * Executes a single frame step of the emulation loop.
         */
        @JvmStatic
        external fun stepFrame()

        /**
         * Sets the state of an emulated input button/axis for a specific player/port.
         */
        @JvmStatic
        external fun setInputState(port: Int, device: Int, index: Int, id: Int, value: Short)

        /**
         * Reset the emulated hardware console.
         */
        @JvmStatic
        external fun reset()

        /**
         * Deinitializes the emulator backend.
         */
        @JvmStatic
        external fun shutdown()
    }
}
