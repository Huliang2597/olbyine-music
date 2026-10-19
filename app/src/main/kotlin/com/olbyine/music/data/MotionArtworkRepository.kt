package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MotionArtwork(val square: String?, val tall: String?) {
    val isEmpty: Boolean get() = square == null && tall == null
}

class MotionArtworkRepository(context: Context, private val apple: AppleMusicClient) {
    private val preferences = context.getSharedPreferences("motion_artwork", Context.MODE_PRIVATE)
    private val memory = HashMap<String, MotionArtwork>()

    suspend fun forTrack(title: String, artist: String, album: String, durationMs: Long): MotionArtwork? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val key = OnlineMetadataRepository.hash("${artist.trim()}\u0000${album.ifBlank { title }.trim()}")
        memory[key]?.let { return@withContext it.takeUnless(MotionArtwork::isEmpty) }
        val cached = preferences.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (cached != null) {
            val result = MotionArtwork(
                cached.optString("square").takeIf(String::isNotBlank),
                cached.optString("tall").takeIf(String::isNotBlank),
            )
            val fresh = System.currentTimeMillis() - cached.optLong("at") < if (result.isEmpty) RETRY_EMPTY_MS else REFRESH_MS
            if (fresh) {
                memory[key] = result
                return@withContext result.takeUnless(MotionArtwork::isEmpty)
            }
        }
        val result = try {
            lookup(title, artist, album, durationMs)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return@withContext null
        }
        memory[key] = result
        preferences.edit().putString(
            key,
            JSONObject()
                .put("square", result.square.orEmpty())
                .put("tall", result.tall.orEmpty())
                .put("at", System.currentTimeMillis())
                .toString(),
        ).apply()
        result.takeUnless(MotionArtwork::isEmpty)
    }

    private suspend fun lookup(title: String, artist: String, album: String, durationMs: Long): MotionArtwork {
        val song = apple.findSong(title, artist, album, durationMs)
        val albumId = song?.let { apple.songRelations(it).first }
        val details = if (song != null && albumId != null) apple.album(albumId, song.storefront) else null
        if (details != null && !(details.motionSquare == null && details.motionTall == null)) {
            return MotionArtwork(details.motionSquare, details.motionTall)
        }
        if (album.isNotBlank()) {
            val wanted = OnlineMetadataRepository.normalize(album)
            val artistKey = OnlineMetadataRepository.normalize(artist)
            val match = apple.searchAlbums("$artist $album".trim(), 5).firstOrNull { (_, candidate) ->
                val name = OnlineMetadataRepository.normalize(candidate.name)
                (name == wanted || name.startsWith(wanted) || wanted.startsWith(name)) &&
                    (artistKey.isBlank() || OnlineMetadataRepository.normalize(candidate.artistName).let { it.contains(artistKey) || artistKey.contains(it) })
            }
            if (match != null) {
                val full = apple.album(match.second.id, match.first)
                if (full != null) return MotionArtwork(full.motionSquare, full.motionTall)
            }
        }
        return MotionArtwork(null, null)
    }

    private companion object {
        const val REFRESH_MS = 30L * 24 * 3_600_000
        const val RETRY_EMPTY_MS = 7L * 24 * 3_600_000
    }
}
