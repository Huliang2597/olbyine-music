package com.olbyine.music.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("native_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    fun reload() {
        _settings.value = read()
    }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setLyricFontSize(value: Float) = update {
        it.copy(lyricFontSize = value.coerceIn(20f, 48f))
    }

    fun setLyricLineGap(value: Float) = update {
        it.copy(lyricLineGap = value.coerceIn(1f, 2.4f))
    }

    fun setLyricInactiveOpacity(value: Float) = update {
        it.copy(lyricInactiveOpacity = value.coerceIn(0.16f, 0.72f))
    }

    fun setLyricOffsetMs(value: Long) = update {
        it.copy(lyricOffsetMs = value.coerceIn(-5_000L, 5_000L))
    }

    fun setLyricAlignPosition(value: Float) = update {
        it.copy(lyricAlignPosition = value.coerceIn(0.15f, 0.85f))
    }

    fun setHapticsEnabled(value: Boolean) = update { it.copy(hapticsEnabled = value) }

    fun setStatusBarLyricsEnabled(value: Boolean) = update { it.copy(statusBarLyricsEnabled = value) }

    fun setAutomaticLyricsEnabled(value: Boolean) = update { it.copy(automaticLyricsEnabled = value) }

    fun setLyricPreviewEnabled(value: Boolean) = update { it.copy(showLyricPreview = value) }

    fun setLyricFontWeight(value: Int) = update {
        it.copy(lyricFontWeight = value.coerceIn(100, 900))
    }

    fun setLyricFontPath(value: String?) = update { it.copy(lyricFontPath = value) }

    fun setUsbBitPerfectEnabled(value: Boolean) = update { it.copy(usbBitPerfectEnabled = value) }

    fun setLiquidGlassEnabled(value: Boolean) = update { it.copy(liquidGlassEnabled = value) }

    fun setDynamicAccentEnabled(value: Boolean) = update { it.copy(dynamicAccentEnabled = value) }

    fun setPlayerGlassButtons(value: Boolean) = update { it.copy(playerGlassButtons = value) }

    fun setGlassPopups(value: Boolean) = update { it.copy(glassPopups = value) }

    fun setGlassTuning(highlight: Float, blur: Float, opacity: Float, refraction: Float, gyro: Boolean) = update {
        it.copy(glassHighlight = highlight, glassBlur = blur, glassOpacity = opacity, glassRefraction = refraction, glassGyro = gyro)
    }

    fun setCustomBackground(path: String?) = update { it.copy(customBackgroundPath = path) }

    fun setCustomBackgroundBlur(value: Float) = update { it.copy(customBackgroundBlur = value.coerceIn(0f, 1f)) }

    fun setCustomBackgroundDim(value: Float) = update { it.copy(customBackgroundDim = value.coerceIn(0f, .8f)) }

    fun setCustomBackgroundOnPlayer(value: Boolean) = update { it.copy(customBackgroundOnPlayer = value) }

    fun setMotionCover(value: Boolean) = update { it.copy(motionCover = value) }

    fun setMotionBackground(value: Boolean) = update { it.copy(motionBackground = value) }

    fun setBeatPulse(value: Boolean) = update { it.copy(beatPulse = value) }

    fun setLyricBlur(value: Boolean) = update { it.copy(lyricBlur = value) }

    fun setLyricTranslation(value: Boolean) = update { it.copy(lyricTranslation = value) }

    fun setLyricRomanization(value: Boolean) = update { it.copy(lyricRomanization = value) }

    fun setLyricSubScale(value: Float) = update { it.copy(lyricSubScale = value.coerceIn(.3f, 1f)) }

    fun setFloatingLyrics(value: Boolean) = update { it.copy(floatingLyrics = value) }

    fun setFloatingLyricsMode(value: String) = update { it.copy(floatingLyricsMode = if (value == "scroll") "scroll" else "karaoke") }

    fun setFloatingLyricsLocked(value: Boolean) = update { it.copy(floatingLyricsLocked = value) }
    fun setFloatingLyricsColor(value: Int) = update { it.copy(floatingLyricsColor = value) }

    fun setLyricDoubleTapSeek(value: Boolean) = update { it.copy(lyricDoubleTapSeek = value) }
    fun setLyricMvStyle(value: String) = update { it.copy(lyricMvStyle = value) }

    fun setShowSourceSwitch(value: Boolean) = update { it.copy(showSourceSwitch = value) }


    fun setBackgroundStyle(value: String) = update { it.copy(backgroundStyle = if (value == "amll") "amll" else "ios") }

    fun setUiStyle(value: UiStyle) = update { it.copy(uiStyle = value) }

    fun setLayoutMode(value: String) = update { it.copy(layoutMode = value.takeIf { v -> v in LAYOUT_MODES } ?: "auto") }

    fun setAlbumStyle(value: String) = update { it.copy(albumStyle = value.takeIf { v -> v in ALBUM_STYLES } ?: "grid") }

    fun setUsbDirectDriver(value: Boolean) = update { it.copy(usbDirectDriver = value) }

    fun setImportMinDuration(seconds: Int) = update { it.copy(importMinDurationSec = seconds.coerceIn(0, 600)) }
    fun setImportSkipDuplicates(value: Boolean) = update { it.copy(importSkipDuplicates = value) }
    fun setAutoCompleteMetadata(value: Boolean) = update { it.copy(autoCompleteMetadata = value) }
    fun setHapticArea(area: Int, enabled: Boolean) = update {
        it.copy(hapticAreas = if (enabled) it.hapticAreas or area else it.hapticAreas and area.inv())
    }
    fun setArtworkBackdrop(value: Boolean) = update { it.copy(artworkBackdrop = value) }
    fun setPlayerFx(bit: Int, enabled: Boolean) = update {
        it.copy(playerFx = if (enabled) it.playerFx or bit else it.playerFx and bit.inv())
    }
    fun setNotificationLyrics(value: Boolean) = update { it.copy(notificationLyrics = value) }
    fun setBluetoothLyrics(value: Boolean) = update { it.copy(bluetoothLyrics = value) }
    fun setTransitionMode(value: TransitionMode) = update { it.copy(transitionMode = value) }
    fun setCrossfadeSeconds(value: Int) = update { it.copy(crossfadeSeconds = value.coerceIn(1, 12)) }
    fun setFloatOutput(value: Boolean) = update { it.copy(floatOutput = value) }
    fun setEqEnabled(value: Boolean) = update { it.copy(eqEnabled = value) }
    fun setEqPreset(name: String) = update { current ->
        val gains = EqBands.presets.firstOrNull { it.first == name }?.second
        if (name == "custom" || gains == null) current.copy(eqPreset = "custom") else current.copy(eqPreset = name, eqBands = gains)
    }
    fun setEqBand(index: Int, gainDb: Float) = update { current ->
        if (index !in current.eqBands.indices) return@update current
        current.copy(eqPreset = "custom", eqBands = current.eqBands.toMutableList().also { it[index] = gainDb.coerceIn(-12f, 12f) })
    }
    fun setEqPreamp(value: Float) = update { it.copy(eqPreamp = value.coerceIn(-12f, 6f)) }
    fun setBalance(value: Float) = update { it.copy(balance = value.coerceIn(-1f, 1f)) }
    fun setMonoAudio(value: Boolean) = update { it.copy(monoAudio = value) }
    fun setUsbBitDepth(value: Int) = update { it.copy(usbBitDepth = value.takeIf { v -> v in setOf(0, 16, 24, 32) } ?: 0) }
    fun setUsbDither(value: Boolean) = update { it.copy(usbDither = value) }
    fun setUsbBufferMs(value: Int) = update { it.copy(usbBufferMs = value.coerceIn(80, 1_000)) }
    fun setUsbVolumeMode(value: UsbVolumeMode) = update { it.copy(usbVolumeMode = value) }
    fun setUsbMaxVolume(value: Int) = update { it.copy(usbMaxVolume = value.coerceIn(10, 100)) }
    fun setNeteaseOnline(value: Boolean) = update { it.copy(neteaseOnline = value) }

    fun setNeteaseDownloadQuality(value: String) = update { it.copy(neteaseDownloadQuality = value) }

    fun setNeteaseDownloadLyrics(value: Boolean) = update { it.copy(neteaseDownloadLyrics = value) }

    fun setNeteaseDownloadCover(value: Boolean) = update { it.copy(neteaseDownloadCover = value) }

    fun setKuwoQuality(value: String) = update { it.copy(kuwoQuality = value) }

    fun setAutoUpdateCheck(value: Boolean) = update { it.copy(autoUpdateCheck = value) }
    fun setNeteaseQuality(value: String) = update { it.copy(neteaseQuality = value) }

    private fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        preferences.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putFloat(KEY_LYRIC_SIZE, next.lyricFontSize)
            .putFloat(KEY_LYRIC_GAP, next.lyricLineGap)
            .putFloat(KEY_LYRIC_OPACITY, next.lyricInactiveOpacity)
            .putLong(KEY_LYRIC_OFFSET, next.lyricOffsetMs)
            .putFloat(KEY_LYRIC_ALIGN, next.lyricAlignPosition)
            .putBoolean(KEY_HAPTICS, next.hapticsEnabled)
            .putBoolean(KEY_STATUS_BAR_LYRICS, next.statusBarLyricsEnabled)
            .putBoolean(KEY_AUTOMATIC_LYRICS, next.automaticLyricsEnabled)
            .putBoolean(KEY_LYRIC_PREVIEW, next.showLyricPreview)
            .putInt(KEY_LYRIC_WEIGHT, next.lyricFontWeight)
            .putString(KEY_LYRIC_FONT_PATH, next.lyricFontPath)
            .putBoolean(KEY_USB_BIT_PERFECT, next.usbBitPerfectEnabled)
            .putBoolean(KEY_LIQUID_GLASS, next.liquidGlassEnabled)
            .putBoolean(KEY_DYNAMIC_ACCENT, next.dynamicAccentEnabled)
            .putBoolean(KEY_PLAYER_GLASS_BUTTONS, next.playerGlassButtons)
            .putBoolean(KEY_GLASS_POPUPS, next.glassPopups)
            .putFloat("glass_highlight", next.glassHighlight)
            .putFloat("glass_blur", next.glassBlur)
            .putFloat("glass_opacity", next.glassOpacity)
            .putFloat("glass_refraction", next.glassRefraction)
            .putBoolean("glass_gyro", next.glassGyro)
            .putString(KEY_CUSTOM_BG, next.customBackgroundPath)
            .putFloat(KEY_CUSTOM_BG_BLUR, next.customBackgroundBlur)
            .putFloat(KEY_CUSTOM_BG_DIM, next.customBackgroundDim)
            .putBoolean(KEY_CUSTOM_BG_PLAYER, next.customBackgroundOnPlayer)
            .putBoolean(KEY_MOTION_COVER, next.motionCover)
            .putBoolean(KEY_MOTION_BACKGROUND, next.motionBackground)
            .putBoolean(KEY_BEAT_PULSE, next.beatPulse)
            .putBoolean(KEY_LYRIC_BLUR, next.lyricBlur)
            .putBoolean(KEY_LYRIC_TRANSLATION, next.lyricTranslation)
            .putBoolean(KEY_LYRIC_ROMANIZATION, next.lyricRomanization)
            .putFloat(KEY_LYRIC_SUB_SCALE, next.lyricSubScale)
            .putBoolean(KEY_FLOATING_LYRICS, next.floatingLyrics)
            .putString(KEY_FLOATING_LYRICS_MODE, next.floatingLyricsMode)
            .putBoolean(KEY_FLOATING_LYRICS_LOCKED, next.floatingLyricsLocked)
            .putInt(KEY_FLOATING_LYRICS_COLOR, next.floatingLyricsColor)
            .putBoolean(KEY_LYRIC_DOUBLE_TAP_SEEK, next.lyricDoubleTapSeek)
            .putString(KEY_LYRIC_MV_STYLE, next.lyricMvStyle)
            .putBoolean(KEY_SOURCE_SWITCH, next.showSourceSwitch)
            .putString(KEY_BACKGROUND_STYLE, next.backgroundStyle)
            .putString(KEY_UI_STYLE, next.uiStyle.name)
            .putString(KEY_LAYOUT_MODE, next.layoutMode)
            .putString(KEY_ALBUM_STYLE, next.albumStyle)
            .putBoolean(KEY_USB_DIRECT, next.usbDirectDriver)
            .putInt(KEY_IMPORT_MIN, next.importMinDurationSec)
            .putBoolean(KEY_IMPORT_DUPLICATES, next.importSkipDuplicates)
            .putBoolean(KEY_AUTO_METADATA, next.autoCompleteMetadata)
            .putInt(KEY_HAPTIC_AREAS, next.hapticAreas)
            .putBoolean(KEY_ARTWORK_BACKDROP, next.artworkBackdrop)
            .putInt(KEY_PLAYER_FX, next.playerFx)
            .putInt(KEY_PLAYER_FX_REV, PLAYER_FX_REV)
            .putBoolean(KEY_NOTIFICATION_LYRICS, next.notificationLyrics)
            .putBoolean(KEY_BLUETOOTH_LYRICS, next.bluetoothLyrics)
            .putString(KEY_TRANSITION, next.transitionMode.name)
            .putInt(KEY_CROSSFADE, next.crossfadeSeconds)
            .putBoolean(KEY_FLOAT_OUTPUT, next.floatOutput)
            .putBoolean(KEY_EQ_ENABLED, next.eqEnabled)
            .putString(KEY_EQ_PRESET, next.eqPreset)
            .putString(KEY_EQ_BANDS, next.eqBands.joinToString(","))
            .putFloat(KEY_EQ_PREAMP, next.eqPreamp)
            .putFloat(KEY_BALANCE, next.balance)
            .putBoolean(KEY_MONO, next.monoAudio)
            .putInt(KEY_USB_BIT_DEPTH, next.usbBitDepth)
            .putBoolean(KEY_USB_DITHER, next.usbDither)
            .putInt(KEY_USB_BUFFER, next.usbBufferMs)
            .putString(KEY_USB_VOLUME_MODE, next.usbVolumeMode.name)
            .putInt(KEY_USB_MAX_VOLUME, next.usbMaxVolume)
            .putBoolean(KEY_NETEASE_ONLINE, next.neteaseOnline)
            .putString(KEY_NETEASE_DOWNLOAD_QUALITY, next.neteaseDownloadQuality)
            .putBoolean(KEY_NETEASE_DOWNLOAD_LYRICS, next.neteaseDownloadLyrics)
            .putBoolean(KEY_NETEASE_DOWNLOAD_COVER, next.neteaseDownloadCover)
            .putString(KEY_KUWO_QUALITY, next.kuwoQuality)
            .putBoolean(KEY_AUTO_UPDATE, next.autoUpdateCheck)
            .putString(KEY_NETEASE_QUALITY, next.neteaseQuality)
            .apply()
        com.olbyine.music.playback.AudioEffects.update(next)
    }

    private fun f(key: String, fallback: Float): Float = runCatching { preferences.getFloat(key, fallback) }
        .getOrElse { runCatching { preferences.getInt(key, fallback.toInt()).toFloat() }.getOrDefault(fallback) }

    private fun i(key: String, fallback: Int): Int = runCatching { preferences.getInt(key, fallback) }
        .getOrElse { runCatching { preferences.getFloat(key, fallback.toFloat()).toInt() }.getOrDefault(fallback) }

    private fun l(key: String, fallback: Long): Long = runCatching { preferences.getLong(key, fallback) }
        .getOrElse { runCatching { preferences.getInt(key, fallback.toInt()).toLong() }.getOrDefault(fallback) }

    private fun b(key: String, fallback: Boolean): Boolean = runCatching { preferences.getBoolean(key, fallback) }
        .getOrElse { fallback }

    private fun s(key: String, fallback: String?): String? = runCatching { preferences.getString(key, fallback) }
        .getOrElse { fallback }

    private fun read(): UserSettings {
        val theme = runCatching {
            ThemeMode.valueOf(s(KEY_THEME, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        return UserSettings(
            themeMode = theme,
            lyricFontSize = f(KEY_LYRIC_SIZE, 30f),
            lyricLineGap = f(KEY_LYRIC_GAP, 1.2f).coerceIn(1f, 2.4f),
            lyricInactiveOpacity = f(KEY_LYRIC_OPACITY, 0.38f),
            lyricOffsetMs = l(KEY_LYRIC_OFFSET, 0L),
            lyricAlignPosition = f(KEY_LYRIC_ALIGN, .5f).coerceIn(.15f, .85f),
            hapticsEnabled = b(KEY_HAPTICS, true),
            statusBarLyricsEnabled = b(KEY_STATUS_BAR_LYRICS, false),
            automaticLyricsEnabled = b(KEY_AUTOMATIC_LYRICS, false),
            showLyricPreview = b(KEY_LYRIC_PREVIEW, true),
            lyricFontWeight = i(KEY_LYRIC_WEIGHT, 700).coerceIn(100, 900),
            lyricFontPath = s(KEY_LYRIC_FONT_PATH, null),
            usbBitPerfectEnabled = b(KEY_USB_BIT_PERFECT, true),
            liquidGlassEnabled = b(KEY_LIQUID_GLASS, false),
            dynamicAccentEnabled = b(KEY_DYNAMIC_ACCENT, true),
            playerGlassButtons = b(KEY_PLAYER_GLASS_BUTTONS, true),
            glassPopups = b(KEY_GLASS_POPUPS, true),
            glassHighlight = f("glass_highlight", 1f),
            glassBlur = f("glass_blur", 1f),
            glassOpacity = f("glass_opacity", 1f),
            glassRefraction = f("glass_refraction", 1f),
            glassGyro = b("glass_gyro", true),
            customBackgroundPath = s(KEY_CUSTOM_BG, null),
            customBackgroundBlur = f(KEY_CUSTOM_BG_BLUR, 0.35f),
            customBackgroundDim = f(KEY_CUSTOM_BG_DIM, 0.35f),
            customBackgroundOnPlayer = b(KEY_CUSTOM_BG_PLAYER, false),
            motionCover = b(KEY_MOTION_COVER, true),
            motionBackground = b(KEY_MOTION_BACKGROUND, false),
            beatPulse = b(KEY_BEAT_PULSE, true),
            lyricBlur = b(KEY_LYRIC_BLUR, false),
            lyricTranslation = b(KEY_LYRIC_TRANSLATION, true),
            lyricRomanization = b(KEY_LYRIC_ROMANIZATION, true),
            lyricSubScale = f(KEY_LYRIC_SUB_SCALE, .5f).coerceIn(.3f, 1f),
            floatingLyrics = b(KEY_FLOATING_LYRICS, false),
            floatingLyricsMode = s(KEY_FLOATING_LYRICS_MODE, null) ?: "karaoke",
            floatingLyricsLocked = b(KEY_FLOATING_LYRICS_LOCKED, false),
            floatingLyricsColor = i(KEY_FLOATING_LYRICS_COLOR, 0),
            lyricDoubleTapSeek = b(KEY_LYRIC_DOUBLE_TAP_SEEK, true),
            lyricMvStyle = s(KEY_LYRIC_MV_STYLE, null).orEmpty(),
            showSourceSwitch = b(KEY_SOURCE_SWITCH, true),
            backgroundStyle = s(KEY_BACKGROUND_STYLE, null) ?: "ios",
            usbDirectDriver = b(KEY_USB_DIRECT, false),
            uiStyle = runCatching { UiStyle.valueOf(s(KEY_UI_STYLE, null) ?: UiStyle.IOS.name) }.getOrDefault(UiStyle.IOS),
            layoutMode = s(KEY_LAYOUT_MODE, null)?.takeIf { it in LAYOUT_MODES } ?: "auto",
            albumStyle = s(KEY_ALBUM_STYLE, null)?.takeIf { it in ALBUM_STYLES } ?: "grid",
            importMinDurationSec = i(KEY_IMPORT_MIN, 30),
            importSkipDuplicates = b(KEY_IMPORT_DUPLICATES, true),
            autoCompleteMetadata = b(KEY_AUTO_METADATA, true),
            hapticAreas = i(KEY_HAPTIC_AREAS, HapticArea.DEFAULT),
            artworkBackdrop = b(KEY_ARTWORK_BACKDROP, true),
            playerFx = i(KEY_PLAYER_FX, PlayerFx.DEFAULT).let { saved ->
                if (i(KEY_PLAYER_FX_REV, 1) < 2) saved or PlayerFx.GRADUAL_BLUR or PlayerFx.SHREDDER or PlayerFx.GLASS_ICONS or PlayerFx.BOUNCE_CARDS else saved
            },
            notificationLyrics = b(KEY_NOTIFICATION_LYRICS, false),
            bluetoothLyrics = b(KEY_BLUETOOTH_LYRICS, false),
            transitionMode = runCatching { TransitionMode.valueOf(s(KEY_TRANSITION, null) ?: "OFF") }.getOrDefault(TransitionMode.OFF),
            crossfadeSeconds = i(KEY_CROSSFADE, 6),
            floatOutput = b(KEY_FLOAT_OUTPUT, false),
            eqEnabled = b(KEY_EQ_ENABLED, false),
            eqPreset = s(KEY_EQ_PRESET, null) ?: "flat",
            eqBands = s(KEY_EQ_BANDS, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
                ?.takeIf { it.size == EqBands.size } ?: List(EqBands.size) { 0f },
            eqPreamp = f(KEY_EQ_PREAMP, 0f),
            balance = f(KEY_BALANCE, 0f),
            monoAudio = b(KEY_MONO, false),
            usbBitDepth = i(KEY_USB_BIT_DEPTH, 0),
            usbDither = b(KEY_USB_DITHER, true),
            usbBufferMs = i(KEY_USB_BUFFER, 240),
            usbVolumeMode = runCatching { UsbVolumeMode.valueOf(s(KEY_USB_VOLUME_MODE, null) ?: "HARDWARE") }.getOrDefault(UsbVolumeMode.HARDWARE),
            usbMaxVolume = i(KEY_USB_MAX_VOLUME, 100),
            neteaseOnline = b(KEY_NETEASE_ONLINE, true),
            neteaseDownloadQuality = s(KEY_NETEASE_DOWNLOAD_QUALITY, null) ?: "exhigh",
            neteaseDownloadLyrics = b(KEY_NETEASE_DOWNLOAD_LYRICS, true),
            neteaseDownloadCover = b(KEY_NETEASE_DOWNLOAD_COVER, false),
            kuwoQuality = s(KEY_KUWO_QUALITY, "320k") ?: "320k",
            autoUpdateCheck = b(KEY_AUTO_UPDATE, true),
            neteaseQuality = s(KEY_NETEASE_QUALITY, null) ?: "exhigh",
        ).also { com.olbyine.music.playback.AudioEffects.update(it) }
    }

    companion object {
        const val KEY_IMPORT_MIN = "import_min_duration"
        const val KEY_IMPORT_DUPLICATES = "import_skip_duplicates"
        const val KEY_AUTO_METADATA = "auto_complete_metadata"
        const val KEY_HAPTIC_AREAS = "haptic_areas"
        const val KEY_ARTWORK_BACKDROP = "artwork_backdrop"
        const val KEY_PLAYER_FX = "player_fx"
        const val KEY_PLAYER_FX_REV = "player_fx_rev"
        const val PLAYER_FX_REV = 2
        const val KEY_NOTIFICATION_LYRICS = "notification_lyrics"
        const val KEY_BLUETOOTH_LYRICS = "bluetooth_lyrics"
        const val KEY_TRANSITION = "transition_mode"
        const val KEY_CROSSFADE = "crossfade_seconds"
        const val KEY_FLOAT_OUTPUT = "float_output"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_EQ_BANDS = "eq_bands"
        const val KEY_EQ_PREAMP = "eq_preamp"
        const val KEY_BALANCE = "balance"
        const val KEY_MONO = "mono_audio"
        const val KEY_USB_BIT_DEPTH = "usb_bit_depth"
        const val KEY_USB_DITHER = "usb_dither"
        const val KEY_USB_BUFFER = "usb_buffer_ms"
        const val KEY_USB_VOLUME_MODE = "usb_volume_mode"
        const val KEY_USB_MAX_VOLUME = "usb_max_volume"
        const val KEY_NETEASE_ONLINE = "netease_online"
        const val KEY_NETEASE_DOWNLOAD_QUALITY = "netease_download_quality"
        const val KEY_NETEASE_DOWNLOAD_LYRICS = "netease_download_lyrics"
        const val KEY_NETEASE_DOWNLOAD_COVER = "netease_download_cover"
        const val KEY_KUWO_QUALITY = "kuwo_quality"
        const val KEY_AUTO_UPDATE = "auto_update_check"
        const val KEY_NETEASE_QUALITY = "netease_quality"
        const val KEY_THEME = "theme"
        const val KEY_LYRIC_SIZE = "lyric_size"
        const val KEY_LYRIC_GAP = "lyric_gap"
        const val KEY_LYRIC_OPACITY = "lyric_opacity"
        const val KEY_LYRIC_OFFSET = "lyric_offset"
    const val KEY_LYRIC_ALIGN = "lyric_align"
        const val KEY_HAPTICS = "haptics"
        const val KEY_STATUS_BAR_LYRICS = "status_bar_lyrics"
        const val KEY_AUTOMATIC_LYRICS = "automatic_lyrics"
        const val KEY_LYRIC_PREVIEW = "lyric_preview"
        const val KEY_LYRIC_WEIGHT = "lyric_weight"
        const val KEY_LYRIC_FONT_PATH = "lyric_font_path"
        const val KEY_USB_BIT_PERFECT = "usb_bit_perfect_enabled"
        const val KEY_LIQUID_GLASS = "liquid_glass_enabled"
        const val KEY_DYNAMIC_ACCENT = "dynamic_accent_enabled"
        const val KEY_PLAYER_GLASS_BUTTONS = "player_glass_buttons_v2"
        const val KEY_GLASS_POPUPS = "glass_popups"
        const val KEY_CUSTOM_BG = "custom_background"
        const val KEY_CUSTOM_BG_BLUR = "custom_background_blur"
        const val KEY_CUSTOM_BG_DIM = "custom_background_dim"
        const val KEY_CUSTOM_BG_PLAYER = "custom_background_player"
        const val KEY_MOTION_COVER = "motion_cover"
        const val KEY_MOTION_BACKGROUND = "motion_background"
        const val KEY_BEAT_PULSE = "beat_pulse"
        const val KEY_LYRIC_BLUR = "lyric_blur"
        const val KEY_LYRIC_TRANSLATION = "lyric_translation"
        const val KEY_LYRIC_ROMANIZATION = "lyric_romanization"
        const val KEY_LYRIC_SUB_SCALE = "lyric_sub_scale"
        const val KEY_FLOATING_LYRICS = "floating_lyrics"
        const val KEY_FLOATING_LYRICS_MODE = "floating_lyrics_mode"
        const val KEY_FLOATING_LYRICS_LOCKED = "floating_lyrics_locked"
        const val KEY_FLOATING_LYRICS_COLOR = "floating_lyrics_color"
        const val KEY_LYRIC_DOUBLE_TAP_SEEK = "lyric_double_tap_seek"
        const val KEY_LYRIC_MV_STYLE = "lyric_mv_style"
        const val KEY_SOURCE_SWITCH = "player_source_switch"
        const val KEY_PLAY_SHUFFLE = "play_shuffle"
        const val KEY_PLAY_REPEAT = "play_repeat"
        const val KEY_BACKGROUND_STYLE = "background_style"
        const val KEY_UI_STYLE = "ui_style"
        const val KEY_LAYOUT_MODE = "layout_mode"
        const val KEY_ALBUM_STYLE = "album_style"
        val LAYOUT_MODES = setOf("auto", "phone", "tablet")
        val ALBUM_STYLES = setOf("grid", "coverflow", "sphere")
        const val KEY_USB_DIRECT = "usb_direct_driver"
    }
}
