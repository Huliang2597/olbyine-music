package com.olbyine.music.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.olbyine.music.data.TransitionMode
import com.olbyine.music.playback.usb.UsbAudioDriver
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
internal class TransitionController(
    private val context: Context,
    private val createTailPlayer: () -> ExoPlayer,
    private val mainPlayer: () -> ExoPlayer?,
    private val mode: () -> TransitionMode,
    private val seconds: () -> Int,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val analyzer = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "olbyine-transition").apply { priority = Thread.MIN_PRIORITY } }
    private val shapes = LruCache<String, Shape>(48)
    private val pending = HashSet<String>()
    private var tail: ExoPlayer? = null
    private var phase = Phase.IDLE
    private var armedKey: String? = null
    private var plan: Plan? = null
    private var ticking = false

    private enum class Phase { IDLE, ARMED, HANDOFF, FADING }

    private data class Plan(val startAtMs: Long, val fadeMs: Long, val incomingOffsetMs: Long)

    data class Shape(val firstSoundMs: Long, val lastSoundMs: Long, val outroStartMs: Long)

    private val tick = object : Runnable {
        override fun run() {
            step()
            if (ticking) handler.postDelayed(this, if (phase == Phase.ARMED) 20L else 100L)
        }
    }

    fun onPlayerChanged() {
        val main = mainPlayer() ?: return
        if (!main.playWhenReady && (phase == Phase.HANDOFF || phase == Phase.FADING)) abort(main)
        val shouldTick = main.isPlaying || phase == Phase.FADING || phase == Phase.HANDOFF
        if (shouldTick && !ticking) {
            ticking = true
            handler.post(tick)
        } else if (!shouldTick) {
            ticking = false
            handler.removeCallbacks(tick)
        }
        if (mode() != TransitionMode.OFF) {
            main.currentMediaItem?.let(::analyzeLater)
            if (main.hasNextMediaItem()) main.getMediaItemAt(main.nextMediaItemIndex).let(::analyzeLater)
        }
    }

    fun onDiscontinuity() {
        if (phase == Phase.ARMED) disarm()
    }

    private fun abort(main: ExoPlayer) {
        handler.removeCallbacksAndMessages(null)
        ticking = false
        main.volume = 1f
        disarm()
    }

    fun release() {
        ticking = false
        handler.removeCallbacksAndMessages(null)
        tail?.release()
        tail = null
        analyzer.shutdownNow()
    }

    private fun available(main: Player): Boolean =
        mode() != TransitionMode.OFF && !UsbAudioDriver.isReady && main.repeatMode != Player.REPEAT_MODE_ONE &&
            main.hasNextMediaItem() && main.duration > MIN_SONG_MS

    private fun step() {
        val main = mainPlayer() ?: return
        when (phase) {
            Phase.IDLE -> {
                if (!main.isPlaying || !available(main)) return
                val current = main.currentMediaItem ?: return
                val next = main.getMediaItemAt(main.nextMediaItemIndex)
                val plan = plan(main.duration, current, next)
                val lead = if (current.localConfiguration?.uri?.scheme.let { it == "ncm" || it == "sc" || it == "http" || it == "https" }) ARM_LEAD_STREAM_MS else ARM_LEAD_MS
                if (main.currentPosition >= plan.startAtMs - lead && main.currentPosition < plan.startAtMs) {
                    arm(main, current, plan)
                }
            }
            Phase.ARMED -> {
                val plan = plan ?: return disarm()
                if (!main.isPlaying || !available(main) || main.currentMediaItem?.mediaId != armedKey) return disarm()
                if (main.currentPosition >= plan.startAtMs - TAIL_START_LEAD_MS) {
                    val ready = tail?.playbackState == Player.STATE_READY
                    when {
                        ready -> handOff(main, plan)
                        main.currentPosition > plan.startAtMs + TAIL_GRACE_MS -> disarm()
                    }
                }
            }
            Phase.HANDOFF, Phase.FADING -> Unit
        }
    }

    private fun arm(main: ExoPlayer, current: MediaItem, plan: Plan) {
        val player = tail ?: createTailPlayer().also { tail = it }
        player.volume = 0f
        player.setMediaItem(current, plan.startAtMs)
        player.prepare()
        player.playWhenReady = false
        armedKey = current.mediaId
        this.plan = plan
        phase = Phase.ARMED
    }

    private fun disarm() {
        tail?.let {
            it.stop()
            it.clearMediaItems()
        }
        armedKey = null
        plan = null
        phase = Phase.IDLE
    }

    private fun handOff(main: ExoPlayer, plan: Plan) {
        val tailPlayer = tail ?: return disarm()
        if (tailPlayer.playbackState != Player.STATE_READY) return disarm()
        phase = Phase.HANDOFF
        val drift = main.currentPosition - plan.startAtMs
        if (drift > 150) tailPlayer.seekTo(main.currentPosition + TAIL_START_LEAD_MS)
        tailPlayer.play()
        val requested = System.nanoTime()
        var flowing = 0L
        val handOver = object : Runnable {
            override fun run() {
                if (flowing == 0L) {
                    val advancing = tailPlayer.isPlaying && tailPlayer.currentPosition > plan.startAtMs + 5
                    if (!advancing && System.nanoTime() - requested < 600_000_000L) {
                        handler.postDelayed(this, 8L)
                        return
                    }
                    flowing = System.nanoTime()
                }
                val t = ((System.nanoTime() - flowing) / 1_000_000f / HANDOFF_FADE_MS).coerceIn(0f, 1f)
                main.volume = 1f - t
                tailPlayer.volume = t
                if (t < 1f) handler.postDelayed(this, 8L) else startCrossfade(main, tailPlayer, plan)
            }
        }
        handler.post(handOver)
    }

    private fun startCrossfade(main: ExoPlayer, tailPlayer: ExoPlayer, plan: Plan) {
        phase = Phase.FADING
        main.volume = 0f
        val nextIndex = main.nextMediaItemIndex
        if (nextIndex >= 0) {
            if (plan.incomingOffsetMs > 0) main.seekTo(nextIndex, plan.incomingOffsetMs) else main.seekToDefaultPosition(nextIndex)
        }
        val start = System.nanoTime()
        val fade = object : Runnable {
            override fun run() {
                val t = ((System.nanoTime() - start) / 1_000_000f / plan.fadeMs).coerceIn(0f, 1f)
                main.volume = sin(t * PI / 2).toFloat()
                tailPlayer.volume = cos(t * PI / 2).toFloat()
                if (t < 1f && tailPlayer.playbackState != Player.STATE_ENDED) {
                    handler.postDelayed(this, 16L)
                } else {
                    main.volume = 1f
                    disarm()
                }
            }
        }
        handler.post(fade)
    }

    private fun plan(durationMs: Long, current: MediaItem, next: MediaItem): Plan {
        val outgoing = shapes.get(current.mediaId)
        val incoming = shapes.get(next.mediaId)
        val end = outgoing?.lastSoundMs?.takeIf { it in (durationMs / 2)..durationMs } ?: durationMs
        val incomingOffset = incoming?.firstSoundMs?.coerceIn(0L, MAX_LEADING_SILENCE_MS) ?: 0L
        val fade = when (mode()) {
            TransitionMode.AUTOMIX -> {
                val outro = outgoing?.outroStartMs?.takeIf { it in (durationMs / 2) until end }
                (outro?.let { end - it } ?: AUTO_DEFAULT_MS).coerceIn(AUTO_MIN_MS, AUTO_MAX_MS)
            }
            else -> seconds().coerceIn(1, 12) * 1_000L
        }.coerceAtMost(durationMs / 3)
        return Plan(startAtMs = (end - fade).coerceAtLeast(durationMs / 2), fadeMs = fade, incomingOffsetMs = incomingOffset)
    }

    private fun analyzeLater(item: MediaItem) {
        val key = item.mediaId
        val uri = item.localConfiguration?.uri ?: return
        if (uri.scheme == "ncm" || uri.scheme == "sc" || shapes.get(key) != null || !pending.add(key)) return
        analyzer.execute {
            val shape = runCatching { measure(uri) }.getOrNull()
            handler.post {
                pending.remove(key)
                if (shape != null) shapes.put(key, shape)
            }
        }
    }

    private fun measure(uri: Uri): Shape? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else return null
            val head = decode(extractor, format, fromUs = 0L, toUs = HEAD_US.coerceAtMost(durationUs))
            val tailFrom = (durationUs - TAIL_US).coerceAtLeast(0L)
            val tail = decode(extractor, format, fromUs = tailFrom, toUs = durationUs)
            if (head.isEmpty() || tail.isEmpty()) return null
            val firstSound = head.indexOfFirst { it > SILENCE_DB }.takeIf { it >= 0 }?.let { it * WINDOW_MS } ?: 0L
            val lastIndex = tail.indexOfLast { it > SILENCE_DB }
            val lastSound = if (lastIndex >= 0) tailFrom / 1000 + (lastIndex + 1) * WINDOW_MS else durationUs / 1000
            val smoothed = tail.indices.map { i ->
                val from = (i - 10).coerceAtLeast(0)
                val to = (i + 10).coerceAtMost(tail.lastIndex)
                (from..to).map { tail[it] }.average()
            }
            val loud = smoothed.filter { it > SILENCE_DB }.sorted().let { if (it.isEmpty()) SILENCE_DB.toDouble() else it[it.size * 3 / 4] }
            val outroIndex = (lastIndex.coerceAtLeast(0) downTo 0).firstOrNull { smoothed[it] >= loud - 6.0 } ?: 0
            val outroStart = tailFrom / 1000 + outroIndex * WINDOW_MS
            return Shape(firstSound, lastSound, outroStart)
        } finally {
            extractor.release()
        }
    }

    private fun decode(extractor: MediaExtractor, format: MediaFormat, fromUs: Long, toUs: Long): FloatArray {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
        val codec = MediaCodec.createDecoderByType(mime)
        val levels = ArrayList<Float>()
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var windowSum = 0.0
            var windowCount = 0
            var windowFrames = (sampleRate * WINDOW_MS / 1000).toInt()
            val deadline = System.currentTimeMillis() + 4_000
            while (!outputDone && System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(5_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > toUs) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 5_000)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = codec.outputFormat
                        sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = output.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            output.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                        windowFrames = (sampleRate * WINDOW_MS / 1000).toInt()
                    }
                    out >= 0 -> {
                        if (info.presentationTimeUs >= fromUs && info.size > 0) {
                            val buffer = codec.getOutputBuffer(out)!!.order(ByteOrder.nativeOrder())
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val frameBytes = channels * if (floatPcm) 4 else 2
                            while (buffer.remaining() >= frameBytes) {
                                var frame = 0.0
                                for (c in 0 until channels) {
                                    val v = if (floatPcm) buffer.float.toDouble() else buffer.short / 32768.0
                                    frame += v * v
                                }
                                windowSum += frame / channels
                                windowCount++
                                if (windowCount >= windowFrames) {
                                    val rms = sqrt(windowSum / windowCount)
                                    levels += (20 * log10(rms.coerceAtLeast(1e-7))).toFloat()
                                    windowSum = 0.0
                                    windowCount = 0
                                }
                            }
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return levels.toFloatArray()
    }

    private companion object {
        const val MIN_SONG_MS = 30_000L
        const val ARM_LEAD_MS = 6_000L
        const val ARM_LEAD_STREAM_MS = 12_000L
        const val TAIL_GRACE_MS = 1_500L
        const val TAIL_START_LEAD_MS = 120L
        const val HANDOFF_FADE_MS = 70f
        const val MAX_LEADING_SILENCE_MS = 4_000L
        const val AUTO_DEFAULT_MS = 5_000L
        const val AUTO_MIN_MS = 2_500L
        const val AUTO_MAX_MS = 10_000L
        const val HEAD_US = 15_000_000L
        const val TAIL_US = 32_000_000L
        const val WINDOW_MS = 50L
        const val SILENCE_DB = -50f
    }
}
