package org.mushaf.app.feature.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.ContentScale
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlin.math.abs
import org.koin.core.context.GlobalContext
import org.mushaf.app.MainActivity
import org.mushaf.app.R
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.core.reading.Days
import org.mushaf.app.data.khatmah.Khatmah
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.remind.Reminder
import org.mushaf.app.data.settings.SettingsStore

/**
 * The home-screen widgets: the ayah of the day, the khatmah, reading on.
 * Drawn once a day by the system, and again when the app is left: nothing
 * runs for them in between.
 */
object Widgets {

    suspend fun refresh(context: Context) {
        runCatching {
            AyahWidget().updateAll(context)
            KhatmahWidget().updateAll(context)
            ReadingWidget().updateAll(context)
        }
    }

    /** Opens the app at [page]. */
    fun open(context: Context, page: Int?) = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .apply { if (page != null) putExtra(Reminder.PAGE, page) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    )

    fun dark(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** The ink of the phone's palette (Material You), for the words drawn as images. */
    fun ink(context: Context): Int =
        context.getColor(if (dark(context)) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900)

    fun accent(context: Context): Int =
        context.getColor(if (dark(context)) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)

    fun quranFont(context: Context, riwayah: Riwayah): Typeface? =
        ResourcesCompat.getFont(context, if (riwayah == Riwayah.WARSH) R.font.uthmanic_warsh else R.font.uthmanic_hafs)

    /** Arabic drawn in the mushaf's font, the widgets' text having none of their own. */
    fun arabic(text: String, font: Typeface, color: Int, width: Int, size: Float): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; this.color = color; textSize = size }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, 1.2f)
            .build()
        val bmp = Bitmap.createBitmap(width, layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(bmp))
        return bmp
    }

    /** A ring [fraction] full. */
    fun ring(fraction: Float, color: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * 0.12f
        val r = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; this.color = color }
        p.alpha = 50
        c.drawArc(r, 0f, 360f, false, p)
        p.alpha = 255
        c.drawArc(r, -90f, 360f * fraction.coerceIn(0f, 1f), false, p)
        return bmp
    }
}

@Composable
private fun Card(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(GlanceTheme.colors.widgetBackground).padding(14.dp).then(modifier),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium))
}

/**
 * The Quran's own supplications that open "Rabbana" (Our Lord), as the
 * known list of them gives them, each checked in the text to hold the
 * word, those that only go on with a du'a begun before (10:86, 40:9) and
 * those too long for a widget (2:286, 7:89, 60:4, 66:8) left out. Hafs
 * numbering.
 */
private val DUAS = listOf(
    "2:127", "2:128", "2:201", "2:250", "3:8", "3:9", "3:16", "3:53", "3:147", "3:191", "3:192", "3:193", "3:194",
    "4:75", "5:83", "5:114", "7:23", "7:47", "7:126", "10:85", "14:38", "14:40", "14:41", "18:10", "20:45", "23:109",
    "25:65", "25:74", "40:7", "40:8", "59:10", "60:5"
).map { AyahKey.parse(it)!! }

/** A du'a of the Quran a day: the same all day, the next one the next day. */
class AyahWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.get()
        val quran = koin.get<Quran>()
        val settings = koin.get<SettingsStore>()
        // One of the Quran's own supplications (the "Rabbana" du'as) a day, in turn.
        val hafs = DUAS[abs(Reminder.today() % DUAS.size).toInt()]
        val key = if (settings.current.riwayah == Riwayah.WARSH) quran.warshKey(hafs) else hafs
        val words = quran.page(quran.pageOf(key)).words.filter { it.key == key }.joinToString(" ") { it.text }
        val meaning = quran.english(key)
        val name = quran.surah(key.surah).title
        val page = quran.pageOf(key)
        val font = Widgets.quranFont(context, settings.current.riwayah)
        val image = font?.let { Widgets.arabic(words, it, Widgets.ink(context), 900, 64f) }
        provideContent {
            GlanceTheme {
                Card(GlanceModifier.clickable(Widgets.open(context, page))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.fillMaxWidth()) {
                        Label(context.getString(R.string.widget_dua))
                        Spacer(GlanceModifier.height(6.dp))
                        // The du'a takes the room left, scaled to fit; its meaning and reference always show.
                        if (image != null) Image(
                            ImageProvider(image),
                            contentDescription = words,
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        )
                        Text(
                            meaning,
                            maxLines = 2,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center)
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text("$name $key", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center))
                    }
                }
            }
        }
    }
}

/** The khatmah: how far, and today's pages. */
class KhatmahWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val khatmah = GlobalContext.get().get<Khatmah>()
        val plan = khatmah.plan.value
        val portion = plan?.takeIf { !it.finished }?.portion(khatmah.today())
        val fraction = plan?.let { it.read / it.total.toFloat() } ?: 0f
        val ring = Widgets.ring(fraction, Widgets.accent(context), 220)
        provideContent {
            GlanceTheme {
                Card(GlanceModifier.clickable(Widgets.open(context, portion?.fromPage))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(context.getString(R.string.khatmah))
                        Spacer(GlanceModifier.height(6.dp))
                        Box(contentAlignment = Alignment.Center) {
                            Image(ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.size(72.dp))
                            Text(if (fraction > 0f && fraction < 0.01f) "<1%" else "${(fraction * 100).toInt()}%", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        Text(
                            when {
                                plan == null -> context.getString(R.string.widget_start_one)
                                plan.finished -> context.getString(R.string.widget_read_whole)
                                portion!!.done -> context.getString(R.string.widget_today_read)
                                portion.toPage > portion.fromPage -> context.getString(R.string.widget_today_pages, portion.fromPage, portion.toPage)
                                else -> context.getString(R.string.widget_today_page, portion.fromPage)
                            },
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, textAlign = TextAlign.Center)
                        )
                    }
                }
            }
        }
    }
}

/** Reading on: where it stopped, the days in a row, and back to it in a tap. */
class ReadingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsStore>().current
        val quran = koin.get<Quran>()
        val page = settings.page
        val surah = runCatching { quran.surah(quran.firstAyah(page).surah).title }.getOrDefault("")
        val streak = Days.streak(settings.readDays, Reminder.today())
        provideContent {
            GlanceTheme {
                Card(GlanceModifier.clickable(Widgets.open(context, page))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(context.getString(R.string.widget_reading))
                        Spacer(GlanceModifier.height(6.dp))
                        Text(surah, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                        Text(context.getString(R.string.page_title, page), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, textAlign = TextAlign.Center))
                        if (streak > 0) Text(
                            context.resources.getQuantityString(R.plurals.days_in_row, streak, streak),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center)
                        )
                        Spacer(GlanceModifier.height(8.dp))
                        Box(
                            GlanceModifier.cornerRadius(18.dp).background(GlanceTheme.colors.primary).padding(horizontal = 16.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(context.getString(R.string.continue_btn), style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}

class AyahWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AyahWidget()
}

class KhatmahWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KhatmahWidget()
}

class ReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReadingWidget()
}
