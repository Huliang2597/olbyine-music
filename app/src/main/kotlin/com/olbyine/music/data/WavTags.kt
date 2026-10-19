package com.olbyine.music.data

import com.olbyine.music.playback.Id3
import java.io.InputStream

internal data class WavTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val year: Int,
    val track: Int,
) {
    companion object {
        fun read(input: InputStream): WavTags? {
            val head = input.readFully(12) ?: return null
            if (head.ascii(0) != "RIFF" || head.ascii(8) != "WAVE") return null
            val info = HashMap<String, ByteArray>()
            var id3: Map<String, Any>? = null
            var chunks = 0
            while (chunks++ < 256) {
                val header = input.readFully(8) ?: break
                val id = header.ascii(0)
                val size = header.uint32(4)
                when {
                    id == "LIST" && size in 4L..MAX_LIST -> {
                        val body = input.readFully(size.toInt()) ?: break
                        if (body.ascii(0) == "INFO") readInfo(body, info)
                    }
                    (id == "id3 " || id == "ID3 ") && size in 10L..MAX_ID3 -> {
                        id3 = Id3.frames(input.readFully(size.toInt()) ?: break)
                    }
                    else -> if (!input.skipFully(size)) break
                }
                if (size and 1L == 1L && !input.skipFully(1)) break
            }
            if (info.isEmpty() && id3 == null) return null
            val (infoTitle, infoArtist, infoAlbum) = TagCharset.decode(listOf(info["INAM"], info["IART"], info["IPRD"]))
                .map { it?.takeIf(String::isNotBlank) }
            val (id3Title, id3Artist, id3Album, id3AlbumArtist) = TagCharset.repair(
                listOf("TIT2", "TPE1", "TALB", "TPE2").map { (id3?.get(it) as? String)?.takeIf(String::isNotBlank) },
            )
            val year = info["ICRD"]?.let { String(it, Charsets.ISO_8859_1) }?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }
                ?: ((id3?.get("TDRC") ?: id3?.get("TYER")) as? String)?.take(4)?.toIntOrNull() ?: 0
            val track = (info["ITRK"] ?: info["IPRT"])?.let { String(it, Charsets.ISO_8859_1) }?.let { Regex("\\d{1,4}").find(it)?.value?.toIntOrNull() }
                ?: (id3?.get("TRCK") as? String)?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            return WavTags(
                title = infoTitle ?: id3Title,
                artist = infoArtist ?: id3Artist,
                album = infoAlbum ?: id3Album,
                albumArtist = id3AlbumArtist,
                year = year,
                track = track,
            )
        }

        private fun readInfo(body: ByteArray, into: MutableMap<String, ByteArray>) {
            var pos = 4
            while (pos + 8 <= body.size) {
                val id = body.ascii(pos)
                val size = body.uint32(pos + 4)
                val start = pos + 8
                if (size > body.size - start) break
                val end = start + size.toInt()
                if (id !in into) into[id] = body.copyOfRange(start, end)
                pos = end + (end and 1)
            }
        }

        private const val MAX_LIST = 1L shl 20
        private const val MAX_ID3 = 32L shl 20

        private fun ByteArray.ascii(at: Int) = String(this, at, 4, Charsets.ISO_8859_1)

        private fun ByteArray.uint32(at: Int): Long =
            (this[at].toLong() and 0xFF) or ((this[at + 1].toLong() and 0xFF) shl 8) or
                ((this[at + 2].toLong() and 0xFF) shl 16) or ((this[at + 3].toLong() and 0xFF) shl 24)

        private fun InputStream.readFully(count: Int): ByteArray? {
            if (count < 0) return null
            val out = ByteArray(count)
            var read = 0
            while (read < count) {
                val n = read(out, read, count - read)
                if (n < 0) return null
                read += n
            }
            return out
        }

        private fun InputStream.skipFully(count: Long): Boolean {
            var remaining = count
            while (remaining > 0) {
                val skipped = skip(remaining)
                if (skipped > 0) remaining -= skipped
                else if (remaining > 1L shl 20 || read() < 0) return false
                else remaining--
            }
            return true
        }
    }
}
