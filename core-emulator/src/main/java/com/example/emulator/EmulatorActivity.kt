package com.example.emulator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout

class EmulatorActivity : Activity(), SurfaceHolder.Callback {

    private var isRunning = false
    private var loopThread: Thread? = null
    private var mockHookThread: Thread? = null
    
    private var starCount = 0
    private var maxStars = 120
    private var hackName = "Super Mario 64"
    private var elapsedSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        val surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@EmulatorActivity)
        }
        root.addView(surfaceView)
        setContentView(root)

        val romPath = intent.getStringExtra("ROM_PATH")
        hackName = intent.getStringExtra("HACK_NAME") ?: "Super Mario 64"
        maxStars = intent.getIntExtra("MAX_STARS", 120)

        if (romPath != null) {
            Log.i(TAG, "Initializing Gopher64 with ROM: $romPath")
            EmulatorCore.initialize()
            EmulatorCore.loadRom(romPath)
        } else {
            Log.e(TAG, "No ROM path provided to EmulatorActivity!")
            finish()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        loopThread = Thread {
            while (isRunning) {
                EmulatorCore.stepFrame()
                Thread.sleep(16)
            }
        }.apply { start() }

        // Start mock memory hook thread to broadcast live star updates every 500ms
        startMockMemoryHook()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        loopThread?.join()
        loopThread = null
        
        mockHookThread?.join()
        mockHookThread = null
    }

    private fun startMockMemoryHook() {
        mockHookThread = Thread {
            while (isRunning && starCount < maxStars) {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                }
                
                starCount++
                elapsedSeconds = starCount * 5 // 5 seconds elapsed per star increment
                
                val hours = elapsedSeconds / 3600
                val minutes = (elapsedSeconds % 3600) / 60
                val seconds = elapsedSeconds % 60
                val timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                // Fire explicit metadata broadcast intent matching DisplayPresentationService definitions
                val intent = Intent("com.example.emulator.UPDATE_METADATA").apply {
                    putExtra("STAR_COUNT", starCount)
                    putExtra("MAX_STARS", maxStars)
                    putExtra("HACK_NAME", hackName)
                    putExtra("ELAPSED_TIME", timeStr)
                }
                sendBroadcast(intent)
            }
        }.apply { start() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        mapInput(keyCode, value = 1)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        mapInput(keyCode, value = 0)
        return true
    }

    private fun mapInput(keyCode: Int, value: Short) {
        val libretroId = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER -> 8
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> 0
            KeyEvent.KEYCODE_DPAD_UP -> 4
            KeyEvent.KEYCODE_DPAD_DOWN -> 5
            KeyEvent.KEYCODE_DPAD_LEFT -> 6
            KeyEvent.KEYCODE_DPAD_RIGHT -> 7
            KeyEvent.KEYCODE_BUTTON_START -> 3
            KeyEvent.KEYCODE_BUTTON_SELECT -> 2
            else -> -1
        }
        if (libretroId != -1) {
            EmulatorCore.setInputState(port = 0, device = 1, index = 0, id = libretroId, value = value)
        }
    }

    override fun onDestroy() {
        EmulatorCore.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EmulatorActivity"
    }
}
