package com.steampunk.brassworks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)        // bumps on resume so the app list refreshes
    private var homeSignal by mutableIntStateOf(0)  // bumps when Home is pressed
    private var active by mutableStateOf(false)     // true only while the home screen is in front

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SafeMode.begin(this) // counts this start, and steps in if the last few all failed
        Steam.load(this) // saved theme and gear speed, before anything is drawn
        setContent { SteampunkTheme { LauncherRoot(tick, homeSignal, active) } }
    }

    override fun onResume() {
        super.onResume()
        tick++
        active = true
    }

    override fun onPause() {
        active = false
        super.onPause()
    }

    @Deprecated("Only used for the app-widget permission and set-up screens")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AppWidgets.onResult(this, requestCode, resultCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal++
    }
}
