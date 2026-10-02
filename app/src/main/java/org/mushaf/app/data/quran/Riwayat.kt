package org.mushaf.app.data.quran

import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.data.hifz.Hifz
import org.mushaf.app.data.marks.Marks
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.feature.hifz.HifzSession
import org.mushaf.app.feature.listen.Listen
import org.mushaf.app.feature.mushaf.Reader

/**
 * Changing the riwayah read: what plays stops, the other mushaf opens on
 * the passage being read, and the reader's marks and hifz of that riwayah
 * come back (each riwayah numbers its ayat and lays out its pages its own way).
 */
class Riwayat(
    private val settings: SettingsStore,
    private val quran: Quran,
    private val reader: Reader,
    private val marks: Marks,
    private val hifz: Hifz,
    private val session: HifzSession,
    private val listen: Listen
) {
    suspend fun change(to: Riwayah) {
        if (settings.current.riwayah == to) return
        listen.stop()
        session.stop()
        // The passage being read, numbered as Hafs, before the change.
        val here = quran.hafsKeys(quran.firstAyah(reader.page.value)).first()
        settings.update { it.copy(riwayah = to) }
        marks.use(to)
        hifz.use(to)
        val there = if (to == Riwayah.WARSH) quran.warshKey(here) else here
        val page = quran.pageOf(there)
        reader.shown(page)
        reader.go(page)
    }
}
