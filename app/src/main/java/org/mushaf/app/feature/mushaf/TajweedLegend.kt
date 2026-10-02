package org.mushaf.app.feature.mushaf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.icon.AppIcons

/**
 * A rule of the colour-coded mushaf, with the colour each palette of the
 * tajweed fonts draws it in (light, and the dark one used on dark pages).
 * Rules and names as Quran.com's legend for the same fonts
 * (quran.com-frontend-next, TajweedBar), colours read from the fonts' CPAL.
 */
private class Rule(val name: String, val light: Long, val dark: Long)

private val RULES = listOf(
    Rule("Silent letter", 0xFFA5A5A5, 0xFF9D9999),
    Rule("Normal madd (2)", 0xFFCE9E00, 0xFFFFC1E0),
    Rule("Separated madd (2/4/6)", 0xFFFF7B00, 0xFFFF8E3B),
    Rule("Connected madd (4/5)", 0xFFF40000, 0xFFFF5E8E),
    Rule("Necessary madd (6)", 0xFFB50000, 0xFFE30000),
    Rule("Ghunna / ikhfa'", 0xFF09B000, 0xFF26B55D),
    Rule("Qalqala (echo)", 0xFF2FADFF, 0xFF00DEFF),
    Rule("Tafkhim (heavy)", 0xFF3F48E6, 0xFF3C84D5)
)

/** The colours of the tajweed pages and what each one asks of the reciter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TajweedLegend(dark: Boolean, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingPane(shape = RoundedCornerShape(28.dp), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tajweed colours", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FloatingAction(AppIcons.Close, "Close", onClose)
                }
                Spacer(Modifier.size(12.dp))
                // The rules wrap onto the next line on a narrow screen.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (r in RULES) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).background(Color(if (dark) r.dark else r.light), CircleShape))
                            Spacer(Modifier.size(8.dp))
                            Text(r.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
