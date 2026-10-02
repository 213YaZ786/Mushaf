package org.mushaf.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.feature.meaning.MeaningScreen
import org.mushaf.app.feature.mushaf.Reader
import org.mushaf.app.feature.tafsir.TafsirScreen
import org.mushaf.app.feature.translations.TranslationsScreen
import org.mushaf.app.BuildConfig
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.feature.about.AboutScreen
import org.mushaf.app.feature.index.IndexScreen
import org.mushaf.app.feature.mushaf.MushafScreen
import org.mushaf.app.feature.settings.SettingsScreen
import org.mushaf.app.ui.component.UpdatePrompt

private object Routes {
    const val MUSHAF = "mushaf"
    const val INDEX = "index"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val MEANING = "meaning/{s}/{a}"
    const val TAFSIR = "tafsir/{s}/{a}"
    const val TRANSLATIONS = "translations"
    fun meaning(k: AyahKey) = "meaning/${k.surah}/${k.ayah}"
    fun tafsir(k: AyahKey) = "tafsir/${k.surah}/${k.ayah}"
}

private fun NavBackStackEntry.key(): AyahKey =
    AyahKey(arguments?.getInt("s") ?: 1, arguments?.getInt("a") ?: 1)

private val keyArgs = listOf(navArgument("s") { type = NavType.IntType }, navArgument("a") { type = NavType.IntType })

private const val NAV_MS = 260

/** The app's screens. It opens on the mushaf, at the page left last. */
@Composable
fun MushafApp() {
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.MUSHAF,
        // Opening scales up from slightly small, going back scales down, the
        // same motion as the other apps.
        enterTransition = {
            scaleIn(initialScale = 0.94f, animationSpec = tween(NAV_MS)) + fadeIn(animationSpec = tween(NAV_MS))
        },
        exitTransition = { fadeOut(animationSpec = tween(NAV_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(NAV_MS)) },
        popExitTransition = {
            scaleOut(targetScale = 0.94f, animationSpec = tween(NAV_MS)) + fadeOut(animationSpec = tween(NAV_MS))
        },
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Routes.MUSHAF) {
            MushafScreen(
                onOpenIndex = { navController.navigate(Routes.INDEX) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenMeaning = { k -> navController.navigate(Routes.meaning(k)) },
                onOpenTafsir = { k -> navController.navigate(Routes.tafsir(k)) }
            )
        }
        composable(Routes.MEANING, arguments = keyArgs) { entry ->
            val reader: Reader = koinInject()
            val quran: Quran = koinInject()
            val scope = rememberCoroutineScope()
            Readable {
                MeaningScreen(
                    start = entry.key(),
                    onBack = { navController.popBackStack() },
                    onOpenTranslations = { navController.navigate(Routes.TRANSLATIONS) },
                    onOpenTafsir = { k -> navController.navigate(Routes.tafsir(k)) },
                    onOpenInMushaf = { k ->
                        scope.launch {
                            reader.go(quran.pageOf(k), k)
                            navController.popBackStack(Routes.MUSHAF, inclusive = false)
                        }
                    }
                )
            }
        }
        composable(Routes.TAFSIR, arguments = keyArgs) { entry ->
            ReadableScroll { TafsirScreen(entry.key(), onBack = { navController.popBackStack() }) }
        }
        composable(Routes.TRANSLATIONS) {
            Readable { TranslationsScreen(onBack = { navController.popBackStack() }) }
        }
        composable(Routes.INDEX) {
            Readable { IndexScreen(onBack = { navController.popBackStack() }) }
        }
        composable(Routes.SETTINGS) {
            ReadableScroll {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenTranslations = { navController.navigate(Routes.TRANSLATIONS) }
                )
            }
        }
        composable(Routes.ABOUT) {
            ReadableScroll { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
    // After the NavHost, so it takes the back gesture before the NavHost's own.
    PlainBack(navController)
    if (!BuildConfig.DEBUG) UpdatePrompt(settings.updates, BuildConfig.VERSION_NAME)
}
