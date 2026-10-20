package com.olbyine.music.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsHub(
    private val library: MusicLibraryRepository,
    private val netease: NeteaseClient,
    private val kuwo: KuwoClient,
    scope: CoroutineScope,
) {
    private val currentId = MutableStateFlow<String?>(null)
    private val overrides = MutableStateFlow<Map<String, StoredLyrics>>(emptyMap())

    fun setOverride(mediaId: String, lyrics: StoredLyrics) {
        onlineCache.remove(mediaId)
        overrides.value = overrides.value + (mediaId to lyrics)
    }
    private val onlineCache = android.util.LruCache<String, LyricsDocument>(40)

    fun setCurrent(mediaId: String?) {
        currentId.value = mediaId
    }

    val document: StateFlow<LyricsDocument> = combine(currentId, library.lyricsRevision, overrides) { id, revision, picked -> Triple(id, revision, picked[id]) }
        .distinctUntilChanged()
        .mapLatest { (id, _, picked) ->
            if (id != null && picked != null) {
                withContext(Dispatchers.Default) { parse(picked.lyrics, picked.wordLyrics, picked.translationLyrics, picked.romanLyrics, id) }
            } else load(id)
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, LyricsDocument.Empty)

    private suspend fun load(id: String?): LyricsDocument {
        if (id == null) return LyricsDocument.Empty
        if (id.startsWith("ncm:")) {
            onlineCache.get(id)?.let { return it }
            val songId = id.removePrefix("ncm:").toLongOrNull() ?: return LyricsDocument.Empty.copy(trackId = id)
            val stored = runCatching { netease.lyrics(songId) }.getOrNull() ?: return LyricsDocument.Empty.copy(trackId = id)
            return withContext(Dispatchers.Default) {
                parse(stored.lyrics, stored.wordLyrics, stored.translationLyrics, stored.romanLyrics, id)
            }.also { onlineCache.put(id, it) }
        }
        if (id.startsWith("kw:")) {
            onlineCache.get(id)?.let { return it }
            val songId = id.removePrefix("kw:").toLongOrNull() ?: return LyricsDocument.Empty.copy(trackId = id)
            val stored = runCatching { kuwo.lyrics(songId) }.getOrNull() ?: return LyricsDocument.Empty.copy(trackId = id)
            return withContext(Dispatchers.Default) {
                parse(stored.lyrics, stored.wordLyrics, stored.translationLyrics, stored.romanLyrics, id)
            }.also { onlineCache.put(id, it) }
        }
        val text = library.lyricsText(id) ?: return LyricsDocument.Empty.copy(trackId = id)
        return withContext(Dispatchers.Default) { parse(text.lines, text.words, text.translation, text.romanization, id) }
    }

    private fun parse(lines: String?, words: String?, translation: String?, romanization: String?, id: String): LyricsDocument {
        val document = LyricsParser.parseBundle(lines, words, translation, romanization)
        val ttml = listOfNotNull(words, lines).firstOrNull { TTML_ROOT.containsMatchIn(it) }
        return document.copy(rawTtml = ttml, trackId = id)
    }

    companion object {
        private val TTML_ROOT = Regex("<(?:\\w+:)?tt(?:\\s|>)", RegexOption.IGNORE_CASE)
    }
}
