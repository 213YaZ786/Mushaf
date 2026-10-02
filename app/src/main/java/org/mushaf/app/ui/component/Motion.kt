package org.mushaf.app.ui.component

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the phone's animations are turned off (Accessibility, Remove
 * animations): the app's motion is skipped and everything shows at once.
 */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Quick out, soft landing: the app's glide. */
val Glide = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)
