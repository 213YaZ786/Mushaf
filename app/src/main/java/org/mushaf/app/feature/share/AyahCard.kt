package org.mushaf.app.feature.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The colours of a card: the phone's own (Material You), light or dark. */
data class CardColors(
    val background: Int,
    val glow1: Int,
    val glow2: Int,
    val glow3: Int,
    val glass: Int,
    val rim: Int,
    val ink: Int,
    val soft: Int
)

/**
 * An ayah as an image to send: the words in the mushaf's own font on a
 * pane of glass over a soft light, the meaning under them if wanted, the
 * reference, the app's name small. 1080 by 1350, as a chat shows it.
 */
object AyahCard {

    private const val W = 1080
    private const val H = 1350

    fun draw(arabic: String, quranFont: Typeface, meaning: String?, reference: String, colors: CardColors): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(colors.background)
        // The soft light behind the glass.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((x, y, r, col) in listOf(Glow(0.18f, 0.16f, 0.55f, colors.glow1), Glow(0.86f, 0.82f, 0.6f, colors.glow2), Glow(0.7f, 0.1f, 0.4f, colors.glow3))) {
            glow.shader = RadialGradient(W * x, H * y, W * r, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)
        }
        val side = 70f
        val inner = W - 2 * side - 2 * 56f
        val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = quranFont; color = colors.ink }
        val soft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL); color = colors.soft }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); color = colors.soft; textSize = 30f; letterSpacing = 0.08f
        }
        // The largest size at which the ayah, its meaning and the reference fit the pane.
        val room = H - 2 * 150f - 2 * 64f
        var size = 84f
        lateinit var ayah: StaticLayout
        var mean: StaticLayout? = null
        while (true) {
            ink.textSize = size
            soft.textSize = (size * 0.46f).coerceIn(28f, 40f)
            ayah = layout(arabic, ink, inner.toInt(), rtl = true, spacing = 1.25f)
            mean = meaning?.takeIf { it.isNotBlank() }?.let { layout(it, soft, inner.toInt(), rtl = false, spacing = 1.15f) }
            val total = ayah.height + (mean?.let { it.height + 120 } ?: 0) + 90
            if (total <= room || size <= 34f) break
            size -= 4f
        }
        val content = ayah.height + (mean?.let { it.height + 120 } ?: 0) + 90
        val paneH = content + 2 * 64f
        val top = (H - paneH) / 2f - 20f
        val pane = RectF(side, top, W - side, top + paneH)
        // The pane: a soft shadow, the glass, its rim.
        c.drawRoundRect(RectF(pane).apply { offset(0f, 18f) }, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(46, 0, 0, 0); maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
        })
        c.drawRoundRect(pane, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.glass })
        c.drawRoundRect(pane, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.rim; style = Paint.Style.STROKE; strokeWidth = 3f })
        var y = top + 64f
        c.save(); c.translate(side + 56f, y); ayah.draw(c); c.restore()
        y += ayah.height
        mean?.let {
            val mark = TextPaint(ink).apply { textSize = 56f; alpha = 120; textAlign = Paint.Align.CENTER }
            c.drawText("۞", W / 2f, y + 82f, mark)
            y += 120f
            c.save(); c.translate(side + 56f, y); it.draw(c); c.restore()
            y += it.height
        }
        label.textAlign = Paint.Align.CENTER
        c.drawText(reference.uppercase(), W / 2f, y + 70f, label)
        val brand = TextPaint(label).apply { textSize = 26f; letterSpacing = 0.2f; alpha = 110 }
        c.drawText("MUSHAF", W / 2f, H - 56f, brand)
        return bmp
    }

    private data class Glow(val x: Float, val y: Float, val r: Float, val color: Int)

    private fun layout(text: String, paint: TextPaint, width: Int, rtl: Boolean, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setLineSpacing(0f, spacing)
            .setIncludePad(true)
            .build()

    /**
     * Sends the card through the apps the reader picks. The image is written
     * to the app's cache and lent for reading only, to the app chosen.
     */
    suspend fun share(context: Context, bitmap: Bitmap, text: String) {
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "shared").apply { mkdirs() }.let { dir ->
                dir.listFiles()?.forEach { it.delete() }
                File(dir, "ayah.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
