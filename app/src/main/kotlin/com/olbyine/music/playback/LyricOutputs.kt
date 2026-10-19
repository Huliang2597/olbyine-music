package com.olbyine.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import com.olbyine.music.OlbyineApplication
import com.olbyine.music.MainActivity
import com.olbyine.music.R
import com.olbyine.music.data.LyricLine
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.data.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
internal class LyricNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    @Volatile var lyric: String? = null

    init {
        setSmallIcon(R.drawable.ic_stat_olbyine)
    }

    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
        metadata.extras?.getString(EXTRA_TITLE) ?: super.getNotificationContentTitle(metadata)

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? =
        lyric?.takeIf(String::isNotBlank)
            ?: metadata.extras?.getString(EXTRA_ARTIST)
            ?: super.getNotificationContentText(metadata)

    companion object {
        const val EXTRA_TITLE = "olbyine.title"
        const val EXTRA_ARTIST = "olbyine.artist"
    }
}

@OptIn(UnstableApi::class)
internal class LyricOutputs(
    private val context: Context,
    private val notificationProvider: LyricNotificationProvider,
    private val refreshNotification: () -> Unit,
    private val player: () -> Player?,
) {
    private val app = context.applicationContext as OlbyineApplication
    private val handler = Handler(Looper.getMainLooper())
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private var document = LyricsDocument.Empty
    private var settings = UserSettings()
    private var lineIndex = Int.MIN_VALUE
    private var lineKey: String? = null
    private var widgetSung = -1
    private var widgetUpdatedAt = 0L
    private var widgetTrack: String? = null
    private var liveShown = false
    private var liveChip: String? = null
    private var livePostedAt = 0L
    private val coverExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var lyricTitledId: String? = null
    private var running = false
    private val floating = FloatingLyrics(context, player)

    private val tick = object : Runnable {
        override fun run() {
            update(force = false)
            if (player()?.isPlaying == true) handler.postDelayed(this, TICK_MS)
            else running = false
        }
    }

    fun start(scope: CoroutineScope) {
        active = this
        FloatingLyrics.active = floating
        floating.onModeChanged = { app.settings.setFloatingLyricsMode(it) }
        floating.onLockChanged = { app.settings.setFloatingLyricsLocked(it) }
        floating.onClosed = { app.settings.setFloatingLyrics(false) }
        ensureChannel()
        scope.launch {
            app.lyricsHub.document.collect {
                document = it
                floating.document = it
                floating.refresh()
                update(force = true)
            }
        }
        scope.launch {
            app.settings.settings.collect {
                val before = settings
                settings = it
                if (before.statusBarLyricsEnabled && !it.statusBarLyricsEnabled) cancelLive()
                if ((before.bluetoothLyrics || before.notificationLyrics) && !it.bluetoothLyrics && !it.notificationLyrics) restoreMetadata()
                floating.apply(it.floatingLyrics, it.floatingLyricsMode, it.floatingLyricsLocked, it.lyricOffsetMs, it.lyricTranslation, it.floatingLyricsColor)
                update(force = true)
            }
        }
    }

    fun onPlayerChanged() {
        update(force = true)
        floating.refresh()
        if (player()?.isPlaying == true && !running) {
            running = true
            handler.postDelayed(tick, TICK_MS)
        }
    }

    fun stop() {
        if (active === this) active = null
        if (FloatingLyrics.active === floating) FloatingLyrics.active = null
        floating.release()
        handler.removeCallbacks(tick)
        running = false
        cancelLive()
    }

    private fun update(force: Boolean) {
        val player = player() ?: return
        val item = player.currentMediaItem
        val id = item?.mediaId
        val playing = player.isPlaying
        val synced = id != null && document.trackId == id && document.isSynchronized && document.lines.isNotEmpty()
        val position = player.currentPosition + settings.lyricOffsetMs
        val index = if (synced) document.lineIndexAt(position) else -1
        val line = document.lines.getOrNull(index)?.takeIf { it.text.isNotBlank() }
        var next: LyricLine? = null
        if (synced) {
            for (k in index + 1 until document.lines.size) if (document.lines[k].text.isNotBlank()) { next = document.lines[k]; break }
        }
        val key = "$id|$index"
        if (force || key != lineKey) {
            lineKey = key
            lineIndex = index
            val text = line?.text
            val wanted = if (settings.notificationLyrics && playing) text else null
            if (wanted != notificationProvider.lyric) {
                notificationProvider.lyric = wanted
                refreshNotification()
            }
            if (settings.statusBarLyricsEnabled && playing && line != null) showLive(line, position, next?.text, item?.mediaMetadata)
            else cancelLive()
            val titled = lyricTitledId
            if (titled != null && titled != id) {
                restoreItem(player, titled)
                lyricTitledId = null
            }
            if (settings.bluetoothLyrics) {
                publishMetadata(player, text, line?.translation?.takeIf { settings.lyricTranslation })
                lyricTitledId = id
            } else if (settings.notificationLyrics) {
                publishMetadata(player, if (playing) text else null, null, asTitle = false)
                lyricTitledId = id
            }
            renderWidget(player, line, next, sung = sungCharacters(line, position), full = true)
            return
        }
        if (line != null && playing && liveShown && Build.VERSION.SDK_INT >= 36 &&
            SystemClock.uptimeMillis() - livePostedAt >= LIVE_MIN_INTERVAL_MS &&
            chipWindow(line, position) != liveChip
        ) {
            showLive(line, position, next?.text, item?.mediaMetadata)
        }
        if (line != null && line.words.isNotEmpty() && playing && hasWidgets) {
            val sung = sungCharacters(line, position)
            val now = System.currentTimeMillis()
            if (sung != widgetSung && now - widgetUpdatedAt >= WIDGET_WORD_INTERVAL_MS) {
                renderWidget(player, line, next, sung, full = false)
            }
        }
    }

    private fun sungCharacters(line: LyricLine?, position: Long): Int {
        line ?: return 0
        if (line.words.isEmpty()) return if (position >= line.startMs) line.text.length else 0
        var cursor = 0
        var sung = 0
        for (word in line.words) {
            val token = word.text.trim()
            val start = line.text.indexOf(token, cursor).takeIf { it >= 0 && token.isNotEmpty() } ?: cursor
            val end = (start + token.length).coerceAtMost(line.text.length)
            cursor = end
            if (position >= word.endMs) sung = end
            else if (position >= word.startMs) {
                val fraction = (position - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1)
                sung = start + ((end - start) * fraction).toInt()
                break
            } else break
        }
        return sung.coerceIn(0, line.text.length)
    }

    private fun ensureChannel() {
        if (notifications.getNotificationChannel(CHANNEL) != null) return
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notification_lyrics_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notification_lyrics_channel_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    private fun showLive(line: LyricLine, position: Long, next: String?, metadata: MediaMetadata?) {
        val text = line.text.trim()
        val chip = chipWindow(line, position)
        liveChip = chip
        livePostedAt = SystemClock.uptimeMillis()
        val title = metadata?.extras?.getString(LyricNotificationProvider.EXTRA_TITLE) ?: metadata?.title?.toString().orEmpty()
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(if (Build.VERSION.SDK_INT >= 36) R.drawable.ic_stat_olbyine_live else R.drawable.ic_stat_olbyine)
            .setContentTitle(text)
            .setContentText(next ?: title)
            .setTicker(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openApp())
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(chip)
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        if (isXiaomi) addFocusParams(builder, text, chip, title, metadata)
        val notification = builder.build()
        notification.flags = notification.flags or FLYME_ALWAYS_SHOW_TICKER or FLYME_ONLY_UPDATE_TICKER
        runCatching { notifications.notify(LIVE_ID, notification) }
        liveShown = true
    }

    private val isXiaomi = Build.MANUFACTURER.lowercase().let { it == "xiaomi" || it == "redmi" || it == "poco" }
    private var focusCoverKey: String? = null
    private var focusCover: Bitmap? = null

    private fun addFocusParams(builder: Notification.Builder, line: String, chip: String, title: String, metadata: MediaMetadata?) {
        val key = metadata?.artworkUri?.toString()
        if (key != focusCoverKey) {
            focusCoverKey = key
            focusCover = metadata?.artworkUri?.takeIf { it.scheme == "file" }?.path?.let(::roundedCover)
        }
        val cover = focusCover
        val picKey = "miui.focus.pic_cover"
        val pic = if (cover != null) org.json.JSONObject().put("type", 1).put("pic", picKey) else null
        val island = org.json.JSONObject().put("islandProperty", 1)
            .put(
                "bigIslandArea",
                org.json.JSONObject().put(
                    "imageTextInfoLeft",
                    org.json.JSONObject().put("type", 1).apply { if (pic != null) put("picInfo", pic) }
                        .put("textInfo", org.json.JSONObject().put("title", chip)),
                ),
            )
            .put("smallIslandArea", org.json.JSONObject().apply { if (pic != null) put("picInfo", pic) })
        val params = org.json.JSONObject()
            .put("protocol", 1)
            .put("business", "music")
            .put("enableFloat", false)
            .put("updatable", true)
            .put("ticker", line)
            .put("aodTitle", line)
            .put("baseInfo", org.json.JSONObject().put("type", 2).put("title", line).put("content", title))
            .put("param_island", island)
        if (pic != null) {
            params.put("tickerPic", picKey).put("aodPic", picKey).put("picInfo", pic)
            builder.extras.putBundle("miui.focus.pics", android.os.Bundle().apply {
                putParcelable(picKey, android.graphics.drawable.Icon.createWithBitmap(cover))
            })
        }
        builder.extras.putString("miui.focus.param", org.json.JSONObject().put("param_v2", params).toString())
    }

    private fun cancelLive() {
        if (!liveShown) return
        notifications.cancel(LIVE_ID)
        liveShown = false
        liveChip = null
    }

    private fun chipWindow(line: LyricLine, position: Long): String {
        val raw = line.text
        val lead = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val text = raw.trim()
        if (chipWidth(text) <= LIVE_CHIP_WIDTH) return text
        val sung = if (line.words.isNotEmpty()) {
            sungCharacters(line, position) - lead
        } else {
            val end = if (line.endMs > line.startMs) line.endMs else line.startMs + 4_000L
            ((position - line.startMs).toFloat() / (end - line.startMs) * text.length).toInt()
        }.coerceIn(0, text.length - 1)
        val pieces = chipPieces(text)
        val piece = pieces.lastOrNull { it.first <= sung } ?: pieces.first()
        return text.substring(piece.first, piece.second).trim()
    }

    private fun chipPieces(text: String): List<Pair<Int, Int>> {
        val tokens = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < text.length) {
            val start = i
            if (charWidth(text[i]) == 2) {
                i++
                while (i < text.length && text[i].isWhitespace()) i++
            } else {
                while (i < text.length && !text[i].isWhitespace() && charWidth(text[i]) == 1) i++
                while (i < text.length && text[i].isWhitespace()) i++
            }
            if (i == start) i++
            tokens += start to i
        }
        val pieces = ArrayList<Pair<Int, Int>>()
        var pieceStart = -1
        var pieceEnd = -1
        fun flush() {
            if (pieceStart >= 0) pieces += pieceStart to pieceEnd
            pieceStart = -1
        }
        for ((start, end) in tokens) {
            val word = text.substring(start, end).trimEnd()
            if (chipWidth(word) > LIVE_CHIP_WIDTH) {
                flush()
                var cut = start
                while (cut < end) {
                    var stop = cut
                    var width = 0
                    while (stop < end && width + charWidth(text[stop]) <= LIVE_CHIP_WIDTH) width += charWidth(text[stop++])
                    if (stop == cut) stop++
                    pieces += cut to stop
                    cut = stop
                }
                continue
            }
            if (pieceStart >= 0 && chipWidth(text.substring(pieceStart, start) + word) > LIVE_CHIP_WIDTH) flush()
            if (pieceStart < 0) pieceStart = start
            pieceEnd = end
        }
        flush()
        return pieces.ifEmpty { listOf(0 to text.length) }
    }

    private fun chipWidth(text: String): Int = text.sumOf { charWidth(it) }

    private fun charWidth(c: Char): Int = when (c.code) {
        in 0x1100..0x115F, in 0x2E80..0xA4CF, in 0xAC00..0xD7A3, in 0xF900..0xFAFF,
        in 0xFE30..0xFE4F, in 0xFF00..0xFF60, in 0xFFE0..0xFFE6 -> 2
        else -> 1
    }

    private fun publishMetadata(player: Player, lyric: String?, translation: String?, asTitle: Boolean = true) {
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        val originalTitle = metadata.extras?.getString(LyricNotificationProvider.EXTRA_TITLE) ?: metadata.title?.toString().orEmpty()
        val originalArtist = metadata.extras?.getString(LyricNotificationProvider.EXTRA_ARTIST) ?: metadata.artist?.toString().orEmpty()
        val title = if (asTitle) lyric?.takeIf(String::isNotBlank) ?: originalTitle else originalTitle
        val artist = when {
            lyric.isNullOrBlank() -> originalArtist
            !asTitle -> lyric
            else -> translation?.takeIf(String::isNotBlank) ?: "$originalTitle · $originalArtist"
        }
        if (metadata.title?.toString() == title && metadata.artist?.toString() == artist) return
        val extras = android.os.Bundle(metadata.extras ?: android.os.Bundle.EMPTY).apply {
            putString(LyricNotificationProvider.EXTRA_TITLE, originalTitle)
            putString(LyricNotificationProvider.EXTRA_ARTIST, originalArtist)
        }
        val duration = player.duration.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET }
        val updated = metadata.buildUpon().setTitle(title).setDisplayTitle(title).setArtist(artist).setExtras(extras)
            .apply { if (duration != null) setDurationMs(duration) }.build()
        runCatching { player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(updated).build()) }
    }

    private fun restoreMetadata() {
        val player = player() ?: return
        player.currentMediaItem?.mediaId?.let { restoreItem(player, it) }
        lyricTitledId = null
    }

    private fun restoreItem(player: Player, mediaId: String) {
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == mediaId } ?: return
        val item = player.getMediaItemAt(index)
        val extras = item.mediaMetadata.extras ?: return
        val title = extras.getString(LyricNotificationProvider.EXTRA_TITLE) ?: return
        val artist = extras.getString(LyricNotificationProvider.EXTRA_ARTIST)
        if (item.mediaMetadata.title?.toString() == title) return
        val restored = item.mediaMetadata.buildUpon().setTitle(title).setDisplayTitle(title).setArtist(artist).build()
        runCatching { player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(restored).build()) }
    }

    private var hasWidgets = true

    private fun renderWidget(player: Player, line: LyricLine?, next: LyricLine?, sung: Int, full: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, LyricsWidgetProvider::class.java))
        hasWidgets = ids.isNotEmpty()
        if (ids.isEmpty()) return
        if (!full && power?.isInteractive == false) return
        widgetSung = sung
        widgetUpdatedAt = System.currentTimeMillis()
        val views = RemoteViews(context.packageName, R.layout.widget_lyrics)
        views.setTextViewText(R.id.widget_lyric, styledLine(line, sung))
        if (!full) {
            manager.partiallyUpdateAppWidget(ids, views)
            return
        }
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val title = metadata?.extras?.getString(LyricNotificationProvider.EXTRA_TITLE) ?: metadata?.title?.toString()
        val artist = metadata?.extras?.getString(LyricNotificationProvider.EXTRA_ARTIST) ?: metadata?.artist?.toString()
        views.setTextViewText(R.id.widget_title, title ?: context.getString(R.string.widget_idle_title))
        views.setTextViewText(R.id.widget_artist, artist ?: context.getString(R.string.widget_idle_subtitle))
        views.setTextViewText(R.id.widget_next_line, next?.text.orEmpty())
        views.setImageViewResource(R.id.widget_toggle, if (player.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        val trackKey = item?.mediaId
        if (trackKey != widgetTrack) {
            widgetTrack = trackKey
            val artwork = metadata?.artworkUri
            val cover = artwork?.takeIf { it.scheme == "file" }?.path?.let(::roundedCover)
            if (cover != null) views.setImageViewBitmap(R.id.widget_cover, cover)
            else views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)
            if (cover == null && artwork != null && (artwork.scheme == "https" || artwork.scheme == "http")) {
                val url = artwork.toString()
                coverExecutor.execute {
                    val bitmap = com.olbyine.music.ui.remoteArtworkFile(url)?.absolutePath?.let(::roundedCover) ?: return@execute
                    handler.post {
                        if (widgetTrack != trackKey) return@post
                        val update = RemoteViews(context.packageName, R.layout.widget_lyrics)
                        update.setImageViewBitmap(R.id.widget_cover, bitmap)
                        manager.partiallyUpdateAppWidget(ids, update)
                    }
                }
            }
        }
        LyricsWidgetProvider.bindActions(context, views)
        manager.partiallyUpdateAppWidget(ids, views)
    }

    fun refreshWidgets() {
        handler.post {
            widgetTrack = null
            update(force = true)
        }
    }

    private fun styledLine(line: LyricLine?, sung: Int): CharSequence {
        val text = line?.text ?: return ""
        val spannable = SpannableString(text)
        val split = sung.coerceIn(0, text.length)
        if (split > 0) spannable.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (split < text.length) spannable.setSpan(ForegroundColorSpan(0x73FFFFFF), split, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun roundedCover(path: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_PX && bounds.outHeight / (sample * 2) >= COVER_PX) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val output = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = COVER_PX.toFloat() / minOf(decoded.width, decoded.height)
        val shader = BitmapShader(decoded, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate((COVER_PX - decoded.width * scale) / 2f, (COVER_PX - decoded.height * scale) / 2f)
            })
        }
        canvas.drawRoundRect(RectF(0f, 0f, COVER_PX.toFloat(), COVER_PX.toFloat()), COVER_PX * .2f, COVER_PX * .2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        decoded.recycle()
        output
    }.getOrNull()

    private fun openApp(): PendingIntent = MainActivity.openPlayerIntent(context)

    companion object {
        @Volatile internal var active: LyricOutputs? = null
        private const val CHANNEL = "lyrics_status"
        private const val LIVE_ID = 0x4C59
        private const val TICK_MS = 120L
        private const val WIDGET_WORD_INTERVAL_MS = 220L
        private const val LIVE_CHIP_WIDTH = 12
        private const val LIVE_MIN_INTERVAL_MS = 450L
        private const val COVER_PX = 160
        private const val FLYME_ALWAYS_SHOW_TICKER = 0x1000000
        private const val FLYME_ONLY_UPDATE_TICKER = 0x2000000
    }
}

class LyricsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_lyrics)
        bindActions(context, views)
        manager.updateAppWidget(appWidgetIds, views)
        LyricOutputs.active?.refreshWidgets()
    }

    companion object {
        internal fun bindActions(context: Context, views: RemoteViews) {
            fun action(name: String, code: Int) = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, WidgetActionReceiver::class.java).setAction(name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_previous, action(WidgetActionReceiver.PREVIOUS, 1))
            views.setOnClickPendingIntent(R.id.widget_toggle, action(WidgetActionReceiver.TOGGLE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, action(WidgetActionReceiver.NEXT, 3))
            views.setOnClickPendingIntent(
                android.R.id.background,
                MainActivity.openPlayerIntent(context),
            )
        }
    }
}

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PINNED) {
            pinnedAt = SystemClock.uptimeMillis()
            android.widget.Toast.makeText(context, "已添加歌词小组件", android.widget.Toast.LENGTH_SHORT).show()
            LyricOutputs.active?.refreshWidgets()
            return
        }
        val playback = (context.applicationContext as? OlbyineApplication)?.playback ?: return
        val pending = goAsync()
        when (intent.action) {
            TOGGLE -> playback.togglePlayPause()
            NEXT -> playback.skipNext()
            PREVIOUS -> playback.skipPrevious()
        }
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, 1_500)
    }

    companion object {
        const val TOGGLE = "com.olbyine.music.widget.TOGGLE"
        const val NEXT = "com.olbyine.music.widget.NEXT"
        const val PREVIOUS = "com.olbyine.music.widget.PREVIOUS"
        const val PINNED = "com.olbyine.music.widget.PINNED"
        @Volatile private var pinnedAt = 0L

        fun pinnedRecently(): Boolean = pinnedAt != 0L && SystemClock.uptimeMillis() - pinnedAt < 10_000

        fun pinnedCallback(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            9,
            Intent(context, WidgetActionReceiver::class.java).setAction(PINNED),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
    }
}
