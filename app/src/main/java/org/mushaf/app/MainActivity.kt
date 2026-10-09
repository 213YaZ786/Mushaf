package org.mushaf.app

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mushaf.app.feature.widget.Widgets
import org.koin.core.context.GlobalContext
import org.mushaf.app.core.quran.PAGES
import org.mushaf.app.feature.mushaf.Reader
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
        openPage(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPage(intent)
    }

    /** A page asked by the app's own widget: only a page number, only shown. */
    private fun openPage(intent: Intent?) {
        val page = intent?.getIntExtra(PAGE, 0) ?: 0
        if (page in 1..PAGES) GlobalContext.get().get<Reader>().go(page)
    }

    override fun onStart() {
        super.onStart()
        Shown.now = true
    }

    override fun onStop() {
        Shown.now = false
        // The widgets show what was just read.
        lifecycleScope.launch { Widgets.refresh(applicationContext) }
        super.onStop()
    }

    companion object {
        /** The page a widget opens. */
        const val PAGE = "org.mushaf.app.PAGE"
    }
}

/** Whether the app is on screen: out of sight, nothing is followed word by word. */
object Shown {
    @Volatile var now = false
}
