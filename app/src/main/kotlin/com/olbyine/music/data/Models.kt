package com.olbyine.music.data

data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val coverPath: String?,
    val lyrics: String?,
    val wordLyrics: String?,
    val translationLyrics: String?,
    val romanLyrics: String?,
    val lyricsSource: String?,
    val lyricsFormat: String?,
    val lyricsConfidence: Double?,
    val lyricsInstrumental: Boolean,
    val lyricsLookupAt: Long,
    val lyricsLookupStatus: String?,
    val favorite: Boolean,
    val addedAt: Long,
    val folderId: String? = null,
    val folderPath: String? = null,
    val storedLyrics: Boolean = false,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val year: Int = 0,
    val albumArtist: String? = null,
    val composer: String? = null,
    val composerCredit: String? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val sampleRate: Int = 0,
    val bitsPerSample: Int = 0,
    val bitrate: Int = 0,
    val artworkUrl: String? = null,
) {
    val hasLyrics: Boolean
        get() = lyricsInstrumental || storedLyrics || !lyrics.isNullOrBlank() || !wordLyrics.isNullOrBlank()

    val isOnline: Boolean get() = uri.startsWith("ncm://") || uri.startsWith("sc://") || uri.startsWith("kw://") || id.startsWith("nd:")

    val albumOwner: String
        get() = albumArtist?.takeIf(String::isNotBlank)
            ?: artist.split(" / ", "/", "、", ";", " & ", ",").firstOrNull()?.trim()?.takeIf(String::isNotBlank)
            ?: artist
}

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val tracks: List<Track>,
) {
    val cover: Track get() = tracks.firstOrNull { it.coverPath != null } ?: tracks.first()
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class ImportProgress(val done: Int, val total: Int, val phase: String)

data class StoredLyricsText(
    val lines: String?,
    val words: String?,
    val translation: String?,
    val romanization: String?,
)

data class ImportReport(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>,
    val filtered: Int = 0,
    val importedIds: List<String> = emptyList(),
) {
    val message: String
        get() = buildString {
            if (imported > 0) append("已导入 $imported 首")
            if (skipped > 0) {
                if (isNotEmpty()) append("，")
                append("跳过 $skipped 首重复歌曲")
            }
            if (filtered > 0) {
                if (isNotEmpty()) append("，")
                append("忽略 $filtered 个视频或短音频")
            }
            if (errors.isNotEmpty()) {
                if (isNotEmpty()) append("，")
                append("${errors.size} 个文件失败")
            }
            if (isEmpty()) append("没有找到可导入的音频")
        }
}

data class ImportOptions(
    val minDurationMs: Long = 30_000L,
    val skipDuplicates: Boolean = true,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class UiStyle(val label: String) {
    IOS("iOS"),
    COLOROS("ColorOS"),
    HYPEROS("澎湃 OS"),
    MATERIAL("原生 Android"),
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lyricFontSize: Float = 30f,
    val lyricLineGap: Float = 1.2f,
    val lyricInactiveOpacity: Float = 0.38f,
    val lyricOffsetMs: Long = 0L,
    val lyricAlignPosition: Float = 0.5f,
    val hapticsEnabled: Boolean = true,
    val statusBarLyricsEnabled: Boolean = false,
    val automaticLyricsEnabled: Boolean = false,
    val showLyricPreview: Boolean = true,
    val lyricFontWeight: Int = 700,
    val lyricFontPath: String? = null,
    val usbBitPerfectEnabled: Boolean = true,
    val liquidGlassEnabled: Boolean = false,
    val dynamicAccentEnabled: Boolean = true,
    val playerGlassButtons: Boolean = false,
    val glassPopups: Boolean = true,
    val glassHighlight: Float = 1f,
    val glassBlur: Float = 1f,
    val glassOpacity: Float = 1f,
    val glassRefraction: Float = 1f,
    val glassGyro: Boolean = true,
    val customBackgroundPath: String? = null,
    val customBackgroundBlur: Float = 0.35f,
    val customBackgroundDim: Float = 0.35f,
    val customBackgroundOnPlayer: Boolean = false,
    val motionCover: Boolean = true,
    val motionBackground: Boolean = false,
    val beatPulse: Boolean = true,
    val lyricBlur: Boolean = false,
    val lyricTranslation: Boolean = true,
    val lyricRomanization: Boolean = true,
    val lyricSubScale: Float = .5f,
    val lyricDoubleTapSeek: Boolean = true,
    val lyricMvStyle: String = "",
    val showSourceSwitch: Boolean = true,
    val floatingCoverButton: Boolean = true,
    val backgroundStyle: String = "ios",
    val layoutMode: String = "auto",
    val albumStyle: String = "grid",
    val uiStyle: UiStyle = UiStyle.IOS,
    val usbDirectDriver: Boolean = false,
    val importMinDurationSec: Int = 30,
    val importSkipDuplicates: Boolean = true,
    val autoCompleteMetadata: Boolean = true,
    val hapticAreas: Int = HapticArea.DEFAULT,
    val artworkBackdrop: Boolean = true,
    val playerFx: Int = PlayerFx.DEFAULT,
    val notificationLyrics: Boolean = false,
    val bluetoothLyrics: Boolean = false,
    val floatingLyrics: Boolean = false,
    val floatingLyricsMode: String = "karaoke",
    val floatingLyricsLocked: Boolean = false,
    val floatingLyricsColor: Int = 0,
    val transitionMode: TransitionMode = TransitionMode.OFF,
    val crossfadeSeconds: Int = 6,
    val floatOutput: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "flat",
    val eqBands: List<Float> = List(EqBands.size) { 0f },
    val eqPreamp: Float = 0f,
    val balance: Float = 0f,
    val monoAudio: Boolean = false,
    val usbBitDepth: Int = 0,
    val usbDither: Boolean = true,
    val usbBufferMs: Int = 240,
    val usbVolumeMode: UsbVolumeMode = UsbVolumeMode.HARDWARE,
    val usbMaxVolume: Int = 100,
    val neteaseOnline: Boolean = true,
    val neteaseDownloadQuality: String = "exhigh",
    val neteaseDownloadLyrics: Boolean = true,
    val neteaseDownloadCover: Boolean = false,
    val kuwoQuality: String = "320k",
    val autoUpdateCheck: Boolean = true,
    val neteaseQuality: String = "exhigh",
)

object HapticArea {
    const val PLAYBACK = 1
    const val SEEK = 2
    const val SCRUB = 4
    const val SWITCHES = 8
    const val MENUS = 16
    const val GESTURES = 32
    const val LYRICS = 64
    const val DEFAULT = PLAYBACK or SEEK or SCRUB or SWITCHES or MENUS or GESTURES or LYRICS

    val labels = listOf(
        PLAYBACK to "播放控制",
        SEEK to "拖动进度条",
        SCRUB to "字母索引与列表",
        SWITCHES to "开关与选项",
        MENUS to "菜单与弹窗",
        GESTURES to "手势与页面切换",
        LYRICS to "点按歌词跳转",
    )
}

object PlayerFx {
    const val TITLE_REVEAL = 1
    const val HEART_BURST = 2
    const val COVER_TILT = 4
    const val ELASTIC_SEEK = 8
    const val ROLLING_TIME = 16
    const val BADGE_SHINE = 32
    const val FILM_GRAIN = 64
    const val GRADUAL_BLUR = 128
    const val SHREDDER = 256
    const val GLASS_ICONS = 512
    const val BOUNCE_CARDS = 1024
    const val DEFAULT = TITLE_REVEAL or HEART_BURST or COVER_TILT or ELASTIC_SEEK or ROLLING_TIME or BADGE_SHINE or
        GRADUAL_BLUR or SHREDDER or GLASS_ICONS or BOUNCE_CARDS

    val labels = listOf(
        Triple(TITLE_REVEAL, "标题模糊显现", "切歌时歌名和歌手从模糊中逐字变清晰（BlurText）"),
        Triple(HEART_BURST, "收藏脉冲", "点红心时（播放页和各个列表里）先收成一个点，再弹回并迸出光环与火花（PulseHeart + ClickSpark）"),
        Triple(COVER_TILT, "封面倾斜反光", "按住封面时封面朝手指方向倾斜，手指处出现一道高光（TiltedCard + GlareHover）"),
        Triple(ELASTIC_SEEK, "弹性进度条", "拖进度条超过两端时像橡皮筋一样被拉长，松手后弹回（ElasticSlider）"),
        Triple(ROLLING_TIME, "时间数字滚动", "进度时间像里程表一样逐位滚动（Counter）"),
        Triple(BADGE_SHINE, "音质标签流光", "格式与 BIT-PERFECT 标签上不时扫过一道光（ShinyText）"),
        Triple(FILM_GRAIN, "胶片颗粒", "播放页背景叠一层细微的胶片噪点，也能减轻渐变色带（Noise）"),
        Triple(GRADUAL_BLUR, "底部渐进模糊", "页面滚到底栏下面时逐渐失焦，越靠近底边越模糊（GradualBlur）"),
        Triple(SHREDDER, "队列碎纸机", "播放队列里长按一首歌拖进底部的缝里，它会被切成纸条卷着落下并移出队列（Shredder）"),
        Triple(BOUNCE_CARDS, "弹跳封面", "歌单和文件夹页顶部的封面像一叠照片弹出、展开，按住一张会摆正抬起（BounceCards）"),
    )
}

enum class TransitionMode(val label: String, val description: String) {
    OFF("关闭", "歌曲之间无缝衔接"),
    CROSSFADE("淡入淡出", "上一首淡出的同时下一首淡入"),
    AUTOMIX("自动过渡", "分析结尾与开头，跳过静音，按歌曲自动决定过渡长度"),
}

enum class UsbVolumeMode(val label: String) {
    HARDWARE("DAC 硬件音量"),
    SOFTWARE("软件音量（32 位）"),
    FIXED("固定满音量（位完美）"),
}

object EqBands {
    val frequencies = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
    val size: Int get() = frequencies.size
    val labels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    val presets: List<Pair<String, List<Float>>> = listOf(
        "flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "bass" to listOf(6f, 5f, 4f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f),
        "treble" to listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2f, 3.5f, 4.5f, 5f),
        "vocal" to listOf(-2f, -1.5f, -0.5f, 1f, 2.5f, 3f, 2.5f, 1.5f, 0.5f, 0f),
        "pop" to listOf(-1f, 1f, 2.5f, 3f, 2f, 0f, -0.5f, -0.5f, 0.5f, 1f),
        "rock" to listOf(4f, 3f, 1.5f, -0.5f, -1f, 0f, 1.5f, 3f, 3.5f, 3.5f),
        "electronic" to listOf(4.5f, 4f, 1.5f, 0f, -1.5f, 1f, 0.5f, 1.5f, 3.5f, 4.5f),
        "classical" to listOf(3f, 2f, 1.5f, 0.5f, -0.5f, -0.5f, 0f, 1.5f, 2.5f, 3f),
        "acoustic" to listOf(3f, 2.5f, 2f, 1f, 1f, 1f, 2f, 2.5f, 2f, 1.5f),
        "loudness" to listOf(5f, 3.5f, 1f, 0f, -1f, -1f, 0f, 1f, 3f, 4f),
        "custom" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    )

    fun label(preset: String): String = when (preset) {
        "flat" -> "平直"
        "bass" -> "低音增强"
        "treble" -> "高音增强"
        "vocal" -> "人声"
        "pop" -> "流行"
        "rock" -> "摇滚"
        "electronic" -> "电子"
        "classical" -> "古典"
        "acoustic" -> "原声"
        "loudness" -> "响度"
        else -> "自定义"
    }
}

data class StoredLyrics(
    val lyrics: String = "",
    val wordLyrics: String = "",
    val translationLyrics: String = "",
    val romanLyrics: String = "",
    val source: String,
    val format: String,
    val confidence: Double? = null,
    val instrumental: Boolean = false,
)
