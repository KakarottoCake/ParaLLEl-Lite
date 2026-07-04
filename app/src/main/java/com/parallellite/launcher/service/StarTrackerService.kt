package com.parallellite.launcher.service

import android.app.Presentation
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.parallellite.launcher.data.tracking.StarLayout
import java.io.File

/**
 * Renders a minimal live star counter on every secondary display (e.g. the Ayn
 * Thor's second screen). Watches the RetroArch `.srm` and re-decodes the star
 * count whenever it flushes to disk. Started when a hack launches with live
 * tracking enabled; stopped when the user returns to the app.
 */
class StarTrackerService : Service() {

    private lateinit var displayManager: DisplayManager
    private val presentations = mutableMapOf<Int, TrackerPresentation>()
    private val handler = Handler(Looper.getMainLooper())

    private var title = "Super Mario 64"
    private var thumbnailUrl: String? = null
    private var totalStars = 0
    private var stars = 0
    private var startedAt = 0L

    private var saveFile: File? = null
    private var layout: StarLayout = StarLayout.vanillaSm64()
    private var observer: FileObserver? = null

    private val ticker = object : Runnable {
        override fun run() {
            updateAll()
            handler.postDelayed(this, 1000)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = showOn(displayId)
        override fun onDisplayRemoved(displayId: Int) { presentations.remove(displayId)?.safeDismiss() }
        override fun onDisplayChanged(displayId: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            title = intent.getStringExtra(EXTRA_TITLE) ?: title
            thumbnailUrl = intent.getStringExtra(EXTRA_THUMB)
            totalStars = intent.getIntExtra(EXTRA_TOTAL, 0)
            val savePath = intent.getStringExtra(EXTRA_SAVE_PATH)
            layout = intent.getStringExtra(EXTRA_LAYOUT_JSON)
                ?.let { runCatching { StarLayout.parse(it) }.getOrNull() }
                ?: StarLayout.vanillaSm64()
            startedAt = System.currentTimeMillis()
            stars = 0

            if (savePath != null) startWatching(File(savePath))
            for (display in displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
                showOn(display.displayId)
            }
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
        return START_STICKY
    }

    private fun startWatching(file: File) {
        saveFile = file
        decode()
        val dir = file.parentFile ?: return
        dir.mkdirs()
        @Suppress("DEPRECATION")
        observer = object : FileObserver(dir.absolutePath, CLOSE_WRITE or MODIFY or CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && path == file.name) decode()
            }
        }.also { it.startWatching() }
    }

    private fun decode() {
        val file = saveFile ?: return
        runCatching {
            if (file.exists()) {
                val decoded = layout.countStars(file.readBytes())
                handler.post {
                    stars = decoded
                    updateAll()
                }
            }
        }.onFailure { Log.w(TAG, "Failed to decode save", it) }
    }

    private fun showOn(displayId: Int) {
        val display = displayManager.getDisplay(displayId) ?: return
        if (display.displayId == Display.DEFAULT_DISPLAY || presentations.containsKey(displayId)) return
        val presentation = TrackerPresentation(this, display)
        try {
            presentation.show()
            presentations[displayId] = presentation
            presentation.render()
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.e(TAG, "Cannot present on display $displayId", e)
        }
    }

    private fun updateAll() = presentations.values.forEach { it.render() }

    private fun elapsed(): String {
        val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(0)
        return String.format("%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        observer?.stopWatching()
        displayManager.unregisterDisplayListener(displayListener)
        presentations.values.forEach { it.safeDismiss() }
        presentations.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun TrackerPresentation.safeDismiss() = runCatching { dismiss() }

    /** The second-screen view: box art, title, big star counter, session timer. */
    inner class TrackerPresentation(context: Context, display: Display) : Presentation(context, display) {
        private lateinit var art: ImageView
        private lateinit var titleView: TextView
        private lateinit var starView: TextView
        private lateinit var timerView: TextView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#0F0F0F"))
                setPadding(64, 64, 64, 64)
            }
            art = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(480, 270).apply { bottomMargin = 32 }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            titleView = TextView(context).apply {
                textSize = 30f
                setTextColor(Color.parseColor("#FFCC00"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            starView = TextView(context).apply {
                textSize = 64f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 16)
            }
            timerView = TextView(context).apply {
                textSize = 24f
                setTextColor(Color.parseColor("#BDBDBD"))
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }
            root.addView(art)
            root.addView(titleView)
            root.addView(starView)
            root.addView(timerView)
            setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        fun render() {
            if (!::titleView.isInitialized) return
            titleView.text = title
            starView.text = if (totalStars > 0) "★ $stars / $totalStars" else "★ $stars"
            timerView.text = elapsed()
            thumbnailUrl?.let { art.load(it) }
        }
    }

    companion object {
        private const val TAG = "StarTrackerService"
        const val EXTRA_TITLE = "title"
        const val EXTRA_THUMB = "thumb"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_SAVE_PATH = "save_path"
        const val EXTRA_LAYOUT_JSON = "layout_json"

        fun start(
            context: Context,
            title: String,
            thumbnailUrl: String?,
            totalStars: Int,
            saveFilePath: String,
            layoutJson: String?,
        ) {
            val intent = Intent(context, StarTrackerService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_THUMB, thumbnailUrl)
                putExtra(EXTRA_TOTAL, totalStars)
                putExtra(EXTRA_SAVE_PATH, saveFilePath)
                putExtra(EXTRA_LAYOUT_JSON, layoutJson)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StarTrackerService::class.java))
        }
    }
}
