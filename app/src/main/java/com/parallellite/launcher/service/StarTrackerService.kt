package com.parallellite.launcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Presentation
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
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

/**
 * Shows a manual star counter on every secondary display (e.g. the Ayn Thor's
 * second screen). The user adds/removes stars via the ongoing notification's
 * action buttons — no save-file reading. It self-heals: the ticker re-shows the
 * counter if a display drops and returns (task switching), and it keeps running
 * until the user taps "Stop" or launches another hack.
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

    private val ticker = object : Runnable {
        override fun run() {
            ensureDisplays()
            updateAll()
            handler.postDelayed(this, 1000)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = showOn(displayId)
        override fun onDisplayRemoved(displayId: Int) { presentations.remove(displayId)?.safeDismiss() }
        override fun onDisplayChanged(displayId: Int) { showOn(displayId) }
    }

    override fun onCreate() {
        super.onCreate()
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INC -> stars++
            ACTION_DEC -> stars = maxOf(0, stars - 1)
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> {
                title = intent?.getStringExtra(EXTRA_TITLE) ?: title
                thumbnailUrl = intent?.getStringExtra(EXTRA_THUMB)
                totalStars = intent?.getIntExtra(EXTRA_TOTAL, 0) ?: 0
                stars = 0
                startedAt = System.currentTimeMillis()
                handler.removeCallbacks(ticker)
                handler.post(ticker)
            }
        }
        promoteToForeground()
        ensureDisplays()
        updateAll()
        return START_STICKY
    }

    /** Runs as a foreground service so the OS won't kill it while the emulator runs. */
    private fun promoteToForeground() {
        try {
            val channelId = "star_tracker"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Star Tracker", NotificationManager.IMPORTANCE_LOW),
                )
            }
            val notification: Notification = Notification.Builder(this, channelId)
                .setContentTitle("$title — ★ $stars")
                .setContentText("Add stars with the buttons below")
                .setSmallIcon(android.R.drawable.star_on)
                .setOngoing(true)
                .addAction(0, "+1 Star", action(ACTION_INC, 1))
                .addAction(0, "-1", action(ACTION_DEC, 2))
                .addAction(0, "Stop", action(ACTION_STOP, 3))
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground", e)
        }
    }

    private fun action(name: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, StarTrackerService::class.java).setAction(name)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Ensure a counter is shown on every non-primary display (self-heals). */
    private fun ensureDisplays() {
        for (display in displayManager.displays) showOn(display.displayId)
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
                textSize = 72f
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
        private const val NOTIFICATION_ID = 42
        const val EXTRA_TITLE = "title"
        const val EXTRA_THUMB = "thumb"
        const val EXTRA_TOTAL = "total"
        const val ACTION_INC = "com.parallellite.launcher.INC"
        const val ACTION_DEC = "com.parallellite.launcher.DEC"
        const val ACTION_STOP = "com.parallellite.launcher.STOP"

        fun start(context: Context, title: String, thumbnailUrl: String?, totalStars: Int) {
            val intent = Intent(context, StarTrackerService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_THUMB, thumbnailUrl)
                putExtra(EXTRA_TOTAL, totalStars)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StarTrackerService::class.java))
        }
    }
}
