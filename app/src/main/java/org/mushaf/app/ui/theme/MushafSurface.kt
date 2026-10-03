package org.mushaf.app.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.data.settings.ThemeMode
import org.mushaf.app.ui.glass.GlassLook
import org.mushaf.app.ui.glass.LocalGlass
import org.mushaf.app.ui.glass.glassGround
import org.mushaf.app.ui.glass.rememberGlassLook
import org.koin.compose.koinInject

/**
 * The frame every window of the app draws in: the user's theme, glass over
 * Material You, and the page's ground with its ambient light under
 * everything.
 */
@Composable
fun ComponentActivity.MushafSurface(content: @Composable () -> Unit) {
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Status and navigation bar icons follow the app's theme, not only the
    // system's, so a forced light theme keeps dark icons.
    DisposableEffect(dark) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        )
        onDispose { }
    }

    MushafTheme(darkTheme = dark, pureBlack = settings.pureBlack, textScale = settings.textScale) {
        // Under glass the ground takes the phone's Material You colour, not only
        // its halos: a soft wash of its main tone under every window. Without
        // glass the zones carry that colour, so the ground stays plain to set
        // them apart.
        val scheme = MaterialTheme.colorScheme
        val ground = remember(scheme, dark) {
            scheme.primaryContainer.copy(alpha = if (dark) 0.22f else 0.38f).compositeOver(scheme.background)
        }
        val plain = rememberGlassLook(scheme, settings.glass)
        val look = remember(plain, ground) {
            plain?.let { GlassLook(it.dark, ground, it.halos, it.zoneTint, it.floatTint, it.accentTint) }
        }
        // Text and icons take the theme's colour in every window: without it
        // Compose draws them black, unreadable on a dark ground.
        CompositionLocalProvider(LocalGlass provides look, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize().glassGround(look, MaterialTheme.colorScheme.background)) {
                content()
            }
        }
    }
}
