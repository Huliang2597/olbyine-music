package com.olbyine.music.data

import java.util.Locale

internal data class CueTrack(
    val number: Int,
    val title: String?,
    val performer: String?,
    val fileName: String?,
    val startMs: Long,
)

internal data class CueSheet(
    val title: String?,
    val performer: String?,
    val tracks: List<CueTrack>,
) {
    companion object {
        fun decode(bytes: ByteArray): CueSheet? {
            val text = runCatching { TagCharset.decode(listOf(bytes)).first() }.getOrNull() ?: return null
            return parse(text)
        }

        fun parse(text: String): CueSheet? {
            var albumTitle: String? = null
            var albumPerformer: String? = null
            var fileName: String? = null
            val tracks = mutableListOf<CueTrack>()
            var pendingTitle: String? = null
            var pendingPerformer: String? = null
            var pendingNumber = 0
            var pendingStart = Long.MIN_VALUE
            var inTrack = false

            fun flush() {
                if (pendingNumber > 0 && pendingStart != Long.MIN_VALUE) {
                    val start = if (pendingStart < 0) -pendingStart - 1 else pendingStart
                    tracks += CueTrack(
                        number = pendingNumber,
                        title = pendingTitle,
                        performer = pendingPerformer,
                        fileName = fileName,
                        startMs = start,
                    )
                }
                pendingTitle = null
                pendingPerformer = null
                pendingNumber = 0
                pendingStart = Long.MIN_VALUE
            }

            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val space = line.indexOf(' ')
                val command = (if (space < 0) line else line.substring(0, space)).uppercase(Locale.ROOT)
                val rest = if (space < 0) "" else line.substring(space + 1).trim()
                when (command) {
                    "FILE" -> {
                        flush()
                        inTrack = false
                        fileName = unquote(rest.substringBeforeLast(' ').trim().ifEmpty { rest }).ifEmpty { null }
                    }
                    "TRACK" -> {
                        flush()
                        inTrack = true
                        pendingNumber = rest.substringBefore(' ').trim().toIntOrNull() ?: 0
                    }
                    "TITLE" -> if (inTrack) pendingTitle = unquote(rest) else albumTitle = unquote(rest)
                    "PERFORMER" -> if (inTrack) pendingPerformer = unquote(rest) else albumPerformer = unquote(rest)
                    "INDEX" -> {
                        val parts = rest.split(' ').filter(String::isNotBlank)
                        if (parts.size >= 2) {
                            val kind = parts[0].trim().toIntOrNull() ?: -1
                            if (kind == 1) {
                                framesToMs(parts[1])?.let { ms -> pendingStart = ms }
                            } else if (kind == 0 && pendingStart == Long.MIN_VALUE) {
                                framesToMs(parts[1])?.let { ms -> pendingStart = -ms - 1 }
                            }
                        }
                    }
                }
            }
            flush()
            if (tracks.isEmpty()) return null
            val normalized = tracks.sortedBy { it.startMs }
            return CueSheet(albumTitle, albumPerformer, normalized)
        }

        private fun unquote(value: String): String = value.trim().removeSurrounding("\"").trim()

        private fun framesToMs(stamp: String): Long? {
            val parts = stamp.split(':')
            if (parts.size !in 2..3) return null
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toLongOrNull() ?: return null
            val frames = if (parts.size == 3) parts[2].toLongOrNull() ?: return null else 0L
            return (minutes * 60 + seconds) * 1000 + frames * 1000 / 75
        }
    }
}
