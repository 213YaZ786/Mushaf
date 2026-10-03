package org.mushaf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mushaf.app.navigation.MushafApp
import org.mushaf.app.ui.theme.MushafSurface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MushafSurface { MushafApp() }
        }
    }

    override fun onStart() {
        super.onStart()
        Shown.now = true
    }

    override fun onStop() {
        Shown.now = false
        super.onStop()
    }
}

/** Whether the app is on screen: out of sight, nothing is followed word by word. */
object Shown {
    @Volatile var now = false
}
