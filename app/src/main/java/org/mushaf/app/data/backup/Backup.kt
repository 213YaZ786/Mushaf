package org.mushaf.app.data.backup

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.data.hifz.Hifz
import org.mushaf.app.data.hifz.HifzFile
import org.mushaf.app.data.khatmah.Khatmah
import org.mushaf.app.data.khatmah.KhatmahPlan
import org.mushaf.app.data.marks.Marks
import org.mushaf.app.data.marks.MarksFile
import org.mushaf.app.data.play.Children
import org.mushaf.app.data.play.Stars
import org.mushaf.app.data.settings.Settings
import org.mushaf.app.data.settings.SettingsStore

/** The file a backup is: what it is, when it was made, each of the reader's own files. */
@Serializable
data class BackupFile(val format: String, val version: Int, val made: Long, val files: Map<String, JsonElement>)

/**
 * The reader's own things in one file they keep where they want (the
 * phone, a card, their own cloud): bookmarks, notes and collections, hifz,
 * khatmah, the children's stars, the settings. Nothing is sent anywhere;
 * the file goes only where the reader saves it. A file read back is checked
 * whole before anything is replaced: a part that is not what it should be
 * refuses the file.
 */
class Backup(
    private val context: Context,
    private val settings: SettingsStore,
    private val marks: Marks,
    private val hifz: Hifz,
    private val khatmah: Khatmah,
    private val stars: Stars
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Each file kept, with the shape it must have. */
    private val parts: Map<String, KSerializer<*>> = mapOf(
        "settings.json" to Settings.serializer(),
        "marks.json" to MarksFile.serializer(),
        "marks-warsh.json" to MarksFile.serializer(),
        "hifz.json" to HifzFile.serializer(),
        "hifz-warsh.json" to HifzFile.serializer(),
        "khatmah.json" to KhatmahPlan.serializer(),
        "children.json" to Children.serializer()
    )

    /** Writes the backup to [out]: the files there are, as they are. */
    suspend fun write(out: OutputStream) = withContext(Dispatchers.IO) {
        val files = parts.keys.mapNotNull { name ->
            val f = File(context.filesDir, name)
            if (!f.exists()) null else runCatching { name to json.parseToJsonElement(f.readText()) }.getOrNull()
        }.toMap()
        val text = json.encodeToString(BackupFile.serializer(), BackupFile(FORMAT, 1, System.currentTimeMillis(), files))
        out.use { it.write(text.toByteArray()) }
    }

    /** Reads a backup from [input] and puts it in place; false when it is not one, then nothing changes. */
    suspend fun read(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        val text = input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 shl 10)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) return@withContext false
            }
            out.toString(Charsets.UTF_8.name())
        }
        val backup = runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrNull() ?: return@withContext false
        if (backup.format != FORMAT || backup.version != 1 || backup.files.isEmpty()) return@withContext false
        // Every part known, and each one of the shape the app reads.
        val checked = backup.files.mapValues { (name, element) ->
            val serializer = parts[name] ?: return@withContext false
            runCatching { json.decodeFromJsonElement(serializer, element) }.getOrNull() ?: return@withContext false
            element.toString()
        }
        for ((name, content) in checked) File(context.filesDir, name).writeTextAtomically(content)
        settings.reload()
        marks.use(settings.current.riwayah)
        marks.reload()
        hifz.use(settings.current.riwayah)
        hifz.reload()
        khatmah.reload()
        stars.reload()
        true
    }

    companion object {
        const val FORMAT = "mushaf-backup"
        /** Far more than any reader's notes; a bigger file is not a backup. */
        const val MAX_BYTES = 8 shl 20
    }
}
