package com.olbyine.music.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class AudioTags(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val trackNumber: Int,
    val year: Int,
    val cover: ByteArray?,
)

internal object AudioTagWriter {
    fun supports(extension: String): Boolean = extension == "mp3" || extension == "flac"

    fun write(target: File, extension: String, tags: AudioTags): Boolean = runCatching {
        when (extension) {
            "mp3" -> writeMp3(target, tags)
            "flac" -> writeFlac(target, tags)
            else -> false
        }
    }.getOrDefault(false)

    private fun writeMp3(target: File, tags: AudioTags): Boolean {
        val id3 = buildId3(tags)
        val existing = existingId3Size(target)
        val temp = File(target.parentFile, target.name + ".tag")
        RandomAccessFile(target, "r").use { input ->
            if (existing > 0) input.seek(existing.toLong())
            RandomAccessFile(temp, "rw").use { output ->
                output.write(id3)
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        return replace(temp, target)
    }

    private fun existingId3Size(target: File): Int = runCatching {
        RandomAccessFile(target, "r").use { file ->
            val header = ByteArray(10)
            if (file.read(header) != 10) return 0
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return 0
            val size = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
            val footer = if ((header[5].toInt() and 0x10) != 0) 10 else 0
            10 + size + footer
        }
    }.getOrDefault(0)

    private fun buildId3(tags: AudioTags): ByteArray {
        val frames = ArrayList<ByteArray>()
        frames += textFrame("TIT2", tags.title)
        frames += textFrame("TPE1", tags.artist)
        frames += textFrame("TALB", tags.album)
        frames += textFrame("TPE2", tags.albumArtist.ifBlank { tags.artist })
        if (tags.trackNumber > 0) frames += textFrame("TRCK", tags.trackNumber.toString())
        if (tags.year > 0) frames += textFrame("TDRC", tags.year.toString())
        frames += textFrame("TLAN", "chi")
        tags.cover?.let { frames += apicFrame(it) }
        val body = frames.fold(ByteArray(0)) { acc, frame -> acc + frame }
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 3
        header[4] = 0
        header[5] = 0
        val size = body.size
        header[6] = ((size shr 21) and 0x7F).toByte()
        header[7] = ((size shr 14) and 0x7F).toByte()
        header[8] = ((size shr 7) and 0x7F).toByte()
        header[9] = (size and 0x7F).toByte()
        return header + body
    }

    private fun textFrame(id: String, value: String): ByteArray {
        if (value.isBlank()) return ByteArray(0)
        val text = byteArrayOf(0x01) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            value.toByteArray(Charsets.UTF_16LE)
        val header = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
        header.put(id.toByteArray(Charsets.US_ASCII))
        header.putInt(text.size)
        header.put(byteArrayOf(0, 0))
        return header.array() + text
    }

    private fun apicFrame(cover: ByteArray): ByteArray {
        val mime = if (isPng(cover)) "image/png" else "image/jpeg"
        val payload = ByteArray(1 + mime.length + 1 + 1 + 1 + cover.size)
        var at = 0
        payload[at++] = 0x00
        mime.toByteArray(Charsets.ISO_8859_1).copyInto(payload, at)
        at += mime.length
        payload[at++] = 0x00
        payload[at++] = 0x03
        payload[at++] = 0x00
        cover.copyInto(payload, at)
        val header = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
        header.put("APIC".toByteArray(Charsets.US_ASCII))
        header.putInt(payload.size)
        header.put(byteArrayOf(0, 0))
        return header.array() + payload
    }

    private fun isPng(bytes: ByteArray): Boolean = bytes.size > 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()

    private fun writeFlac(target: File, tags: AudioTags): Boolean {
        RandomAccessFile(target, "r").use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4) return false
            if (magic[0] != 'f'.code.toByte() || magic[1] != 'L'.code.toByte() || magic[2] != 'a'.code.toByte() || magic[3] != 'C'.code.toByte()) return false
            val blocks = ArrayList<Pair<Int, ByteArray>>()
            var last = false
            while (!last) {
                val head = ByteArray(4)
                if (input.read(head) != 4) break
                last = (head[0].toInt() and 0x80) != 0
                val type = head[0].toInt() and 0x7F
                val length = ((head[1].toInt() and 0xFF) shl 16) or
                    ((head[2].toInt() and 0xFF) shl 8) or
                    (head[3].toInt() and 0xFF)
                val data = ByteArray(length)
                if (input.read(data) != length) return false
                if (type == 4 || type == 6) continue
                blocks += type to data
            }
            val audioStart = input.filePointer
            blocks += 4 to vorbisComment(tags)
            tags.cover?.let { blocks += 6 to pictureBlock(it) }
            val temp = File(target.parentFile, target.name + ".tag")
            RandomAccessFile(temp, "rw").use { output ->
                output.write(magic)
                blocks.forEachIndexed { index, (type, data) ->
                    val head = ByteArray(4)
                    head[0] = (type or if (index == blocks.lastIndex) 0x80 else 0).toByte()
                    head[1] = ((data.size shr 16) and 0xFF).toByte()
                    head[2] = ((data.size shr 8) and 0xFF).toByte()
                    head[3] = (data.size and 0xFF).toByte()
                    output.write(head)
                    output.write(data)
                }
                input.seek(audioStart)
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
            return replace(temp, target)
        }
    }

    private fun vorbisComment(tags: AudioTags): ByteArray {
        val vendor = "Olbyine".toByteArray(Charsets.UTF_8)
        val fields = ArrayList<String>()
        if (tags.title.isNotBlank()) fields += "TITLE=${tags.title}"
        if (tags.artist.isNotBlank()) fields += "ARTIST=${tags.artist}"
        if (tags.album.isNotBlank()) fields += "ALBUM=${tags.album}"
        val albumArtist = tags.albumArtist.ifBlank { tags.artist }
        if (albumArtist.isNotBlank()) fields += "ALBUMARTIST=$albumArtist"
        if (tags.trackNumber > 0) fields += "TRACKNUMBER=${tags.trackNumber}"
        if (tags.year > 0) fields += "DATE=${tags.year}"
        val buffer = ByteBuffer.allocate(8 + vendor.size + 4 + fields.sumOf { 4 + it.toByteArray(Charsets.UTF_8).size })
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(vendor.size)
        buffer.put(vendor)
        buffer.putInt(fields.size)
        fields.forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            buffer.putInt(bytes.size)
            buffer.put(bytes)
        }
        return buffer.array()
    }

    private fun pictureBlock(cover: ByteArray): ByteArray {
        val mime = (if (isPng(cover)) "image/png" else "image/jpeg").toByteArray(Charsets.US_ASCII)
        val buffer = ByteBuffer.allocate(4 + 4 + mime.size + 4 + 4 + 16 + 4 + cover.size)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(3)
        buffer.putInt(mime.size)
        buffer.put(mime)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(cover.size)
        buffer.put(cover)
        return buffer.array()
    }

    private fun replace(temp: File, target: File): Boolean {
        val backup = File(target.parentFile, target.name + ".bak")
        if (!target.renameTo(backup)) return false
        return if (temp.renameTo(target)) {
            backup.delete()
            true
        } else {
            backup.renameTo(target)
            false
        }
    }
}
