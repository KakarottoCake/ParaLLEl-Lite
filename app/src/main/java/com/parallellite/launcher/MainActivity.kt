package com.parallellite.launcher

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.parallellite.launcher.service.StarTrackerService
import com.parallellite.launcher.ui.launcher.LauncherScreen
import com.parallellite.launcher.ui.theme.ParallelLiteTheme

class MainActivity : ComponentActivity() {

    /**
     * Handhelds like the Ayn Thor report a built-in hardware keyboard, so Android
     * suppresses the on-screen keyboard. Override the config this Activity sees to
     * report "no keys", which lets the soft IME show normally on tap/focus.
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            keyboard = Configuration.KEYBOARD_NOKEYS
            hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onResume() {
        super.onResume()
        // Returning to the launcher means the play session ended — tear down the tracker.
        StarTrackerService.stop(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParallelLiteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LauncherScreen()
                }
            }
        }
    }
}
