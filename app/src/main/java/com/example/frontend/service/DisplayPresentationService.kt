package com.example.frontend.service

import android.app.Presentation
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class DisplayPresentationService : Service(), DisplayManager.DisplayListener {

    private lateinit var displayManager: DisplayManager
    private val presentations = mutableMapOf<Int, StarTrackerPresentation>()

    private val metadataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_UPDATE_METADATA) {
                val starCount = intent.getIntExtra(EXTRA_STAR_COUNT, 0)
                val maxStars = intent.getIntExtra(EXTRA_MAX_STARS, 120)
                val hackName = intent.getStringExtra(EXTRA_HACK_NAME) ?: "Unknown Hack"
                val elapsedTime = intent.getStringExtra(EXTRA_ELAPSED_TIME) ?: "00:00:00"

                Log.d(TAG, "Received metadata update: $hackName, Stars: $starCount/$maxStars, Time: $elapsedTime")
                updatePresentations(hackName, starCount, maxStars, elapsedTime)
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): DisplayPresentationService = this@DisplayPresentationService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(this, null)

        // Register Broadcast Receiver for emulator real-time memory updates
        val filter = IntentFilter(ACTION_UPDATE_METADATA)
        registerReceiver(metadataReceiver, filter)

        checkActiveDisplays()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        displayManager.unregisterDisplayListener(this)
        unregisterReceiver(metadataReceiver)
        dismissAllPresentations()
        super.onDestroy()
    }

    // DisplayManager.DisplayListener implementation
    override fun onDisplayAdded(displayId: Int) {
        Log.d(TAG, "Display added: $displayId")
        showPresentationForDisplayId(displayId)
    }

    override fun onDisplayRemoved(displayId: Int) {
        Log.d(TAG, "Display removed: $displayId")
        dismissPresentationForDisplayId(displayId)
    }

    override fun onDisplayChanged(displayId: Int) {
        Log.d(TAG, "Display changed: $displayId")
    }

    private fun checkActiveDisplays() {
        val category = DisplayManager.DISPLAY_CATEGORY_PRESENTATION
        val displays = displayManager.getDisplays(category)
        for (display in displays) {
            showPresentationForDisplay(display)
        }
    }

    private fun showPresentationForDisplayId(displayId: Int) {
        val display = displayManager.getDisplay(displayId) ?: return
        showPresentationForDisplay(display)
    }

    private fun showPresentationForDisplay(display: Display) {
        if (display.displayId == Display.DEFAULT_DISPLAY) return
        if (presentations.containsKey(display.displayId)) return

        Log.i(TAG, "Showing star tracker presentation on display: ${display.name}")
        val presentation = StarTrackerPresentation(this, display)
        try {
            presentation.show()
            presentations[display.displayId] = presentation
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.e(TAG, "Could not show presentation on display ${display.displayId}", e)
        }
    }

    private fun dismissPresentationForDisplayId(displayId: Int) {
        presentations.remove(displayId)?.let {
            try {
                it.dismiss()
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing presentation", e)
            }
        }
    }

    private fun dismissAllPresentations() {
        for (presentation in presentations.values) {
            try {
                presentation.dismiss()
            } catch (e: Exception) {
                // Ignore
            }
        }
        presentations.clear()
    }

    private fun updatePresentations(hackName: String, starCount: Int, maxStars: Int, elapsedTime: String) {
        for (presentation in presentations.values) {
            presentation.updateMetadata(hackName, starCount, maxStars, elapsedTime)
        }
    }

    /**
     * Presentation object rendering custom SM64 Star tracking viewport configurations.
     */
    class StarTrackerPresentation(context: Context, display: Display) : Presentation(context, display) {

        private lateinit var txtHackName: TextView
        private lateinit var txtStarCount: TextView
        private lateinit var txtTimer: TextView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // Dynamic layout creation optimized for secondary star tracker viewport
            val rootLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#121212")) // Premium Sleek Dark
                setPadding(48, 48, 48, 48)
            }

            txtHackName = TextView(context).apply {
                text = "No Active ROM"
                textSize = 28f
                setTextColor(Color.parseColor("#FFFFCC00")) // Mario Gold
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }

            txtStarCount = TextView(context).apply {
                text = "Stars: -- / --"
                textSize = 36f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 24)
            }

            txtTimer = TextView(context).apply {
                text = "Time: 00:00:00"
                textSize = 22f
                setTextColor(Color.LTGRAY)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }

            rootLayout.addView(txtHackName)
            rootLayout.addView(txtStarCount)
            rootLayout.addView(txtTimer)

            setContentView(rootLayout)
        }

        fun updateMetadata(hackName: String, starCount: Int, maxStars: Int, elapsedTime: String) {
            txtHackName.text = hackName
            txtStarCount.text = "Stars: $starCount / $maxStars"
            txtTimer.text = "Time: $elapsedTime"
        }
    }

    companion object {
        private const val TAG = "DisplayPresentationService"
        
        const val ACTION_UPDATE_METADATA = "com.example.emulator.UPDATE_METADATA"
        const val EXTRA_STAR_COUNT = "STAR_COUNT"
        const val EXTRA_MAX_STARS = "MAX_STARS"
        const val EXTRA_HACK_NAME = "HACK_NAME"
        const val EXTRA_ELAPSED_TIME = "ELAPSED_TIME"
    }
}
