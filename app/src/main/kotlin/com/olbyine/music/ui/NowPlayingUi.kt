@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.olbyine.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.olbyine.music.MainUiState
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.data.LyricsSearchSource
import com.olbyine.music.data.Track
import com.olbyine.music.playback.PlaybackState
import com.olbyine.music.playback.UsbBitPerfectStatus
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class PlayMode(val label: String, val icon: ImageVector) {
    SEQUENTIAL("顺序播放", Icons.AutoMirrored.Rounded.PlaylistPlay),
    REPEAT_ALL("列表循环", Icons.Rounded.Repeat),
    REPEAT_ONE("单曲循环", Icons.Rounded.RepeatOne),
    SHUFFLE("随机播放", Icons.Rounded.Shuffle),
}

internal fun PlaybackState.playMode(): PlayMode = when {
    shuffleEnabled -> PlayMode.SHUFFLE
    repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
    repeatMode == Player.REPEAT_MODE_ALL -> PlayMode.REPEAT_ALL
    else -> PlayMode.SEQUENTIAL
}

internal val LocalPlayerBackdrop = androidx.compose.runtime.staticCompositionLocalOf<BackdropSource?> { null }

internal data class ListeningInfo(
    val appName: String?,
    val access: Boolean,
    val searching: Boolean,
    val message: String?,
)

@Composable
internal fun PlayerPage(
    state: MainUiState,
    lyrics: LyricsDocument,
    listening: ListeningInfo?,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onLyrics: () -> Unit,
    onInfo: () -> Unit,
    onFavorite: () -> Unit,
    onPlayMode: (PlayMode) -> Unit,
    onSpeed: (Float) -> Unit,
    onSearchLyrics: (LyricsSearchSource) -> Unit,
    onImportLyrics: () -> Unit,
    onMatchMetadata: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onOpenLibrary: () -> Unit,
    onListening: (Boolean) -> Unit,
    onGrantAccess: () -> Unit,
    onOpenExternalApp: () -> Unit,
    shared: SharedArtworkState,
    pageActive: Boolean,
    onTransition: (com.olbyine.music.data.TransitionMode) -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onGoToAlbum: () -> Unit = {},
    onShareLyric: () -> Unit = {},
    onRemoveFromLibrary: () -> Unit = {},
    onShareSong: () -> Unit = {},
    onListenTogether: (() -> Unit)? = null,
    topExtra: (@Composable () -> Unit)? = null,
) {
    val track = state.currentTrack
    var queueVisible by remember { mutableStateOf(false) }
    val openQueue = LocalQueuePanel.current
    val openModeMenu = rememberPlayModeMenu(state.playback.playMode(), onPlayMode)
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { shared.pageCoordinates = it }) {
        val shortLandscape = maxWidth > maxHeight && maxHeight < 500.dp
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (shortLandscape) Spacer(Modifier.height(6.dp)) else PlayerTopBar(
                eyebrow = when {
                    listening != null -> "正在监听" + (listening.appName?.let { " · $it" } ?: "")
                    track != null -> "正在播放"
                    else -> "播放器"
                },
                title = track?.album?.takeIf(String::isNotBlank) ?: if (track != null && listening == null) "本地音乐" else "",
            )
            if (topExtra != null) {
                Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), contentAlignment = Alignment.Center) { topExtra() }
            }
            when {
                listening != null && !listening.access -> {
                    AccessCard(onGrantAccess, Modifier.weight(1f))
                    SourceRow(listening = true, onListening = onListening)
                }
                track == null -> {
                    if (listening != null) {
                        EmptyState(
                            icon = Icons.Rounded.Headphones,
                            title = "没有检测到正在播放的应用",
                            message = "在网易云音乐、Spotify、QQ 音乐等应用里开始播放，这里会同步显示封面、进度和逐字歌词",
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.MusicNote,
                            title = "还没有在播放的歌曲",
                            message = "去音乐库挑一首，或切到“监听”看其他播放器的歌词",
                            modifier = Modifier.weight(1f),
                        ) {
                            PillButton("前往音乐库", onOpenLibrary, icon = Icons.Rounded.LibraryMusic)
                        }
                    }
                    if (showsSourceSwitch(state, listening != null)) SourceRow(listening = listening != null, onListening = onListening)
                }
                else -> PlayerBody(
                    state = state,
                    track = track,
                    lyrics = lyrics,
                    listening = listening,
                    modifier = Modifier.weight(1f),
                    shared = shared,
                    pageActive = pageActive,
                    onTogglePlay = onTogglePlay,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    onLyrics = onLyrics,
                    onFavorite = onFavorite,
                    onModeButton = openModeMenu,
                    onOpenLibrary = onOpenLibrary,
                    onQueue = { openQueue?.invoke() ?: run { queueVisible = true } },
                    onListening = onListening,
                    onOpenExternalApp = onOpenExternalApp,
                    onTransition = onTransition,
                    menu = {
                        PlayerMenu(
                            enabled = listening == null,
                            hasLyrics = track.hasLyrics,
                            onSearchLyrics = onSearchLyrics,
                            onImportLyrics = onImportLyrics,
                            speed = if (listening == null) state.playback.playbackSpeed else null,
                            onSpeed = onSpeed,
                            showLyricTools = listening == null,
                            quickActions = { close ->
                                val songId = track.id.removePrefix("ncm:").toLongOrNull()?.takeIf { track.id.startsWith("ncm:") }
                                val scId = track.id.removePrefix("sc:").toLongOrNull()?.takeIf { track.id.startsWith("sc:") }
                                val openComments = LocalNeteaseComments.current
                                val openScComments = LocalScComments.current
                                MenuQuickAction(Icons.Rounded.Lyrics, "歌词") { close(); onLyrics() }
                                if (listening == null) {
                                    MenuQuickAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "加入歌单") { close(); onAddToPlaylist() }
                                    MenuQuickAction(Icons.Rounded.Share, "分享") { close(); onShareSong() }
                                    when {
                                        songId != null && openComments != null ->
                                            MenuQuickAction(Icons.Rounded.ChatBubbleOutline, "评论") { close(); openComments(songId, track.title) }
                                        scId != null && openScComments != null ->
                                            MenuQuickAction(Icons.Rounded.ChatBubbleOutline, "评论") { close(); openScComments(scId, track.title) }
                                        else -> MenuQuickAction(Icons.Rounded.Person, "信息") { close(); onInfo() }
                                    }
                                } else {
                                    MenuQuickAction(Icons.Rounded.Person, "信息") { close(); onInfo() }
                                }
                            },
                        ) { close ->
                            if (listening == null) {
                                GlassMenuItem("歌手与专辑", { close(); onInfo() }, icon = Icons.Rounded.Person)
                                GlassMenuItem("前往专辑", { close(); onGoToAlbum() }, icon = Icons.Rounded.Album, enabled = !track.id.startsWith("sc:"))
                                GlassMenuItem("分享歌词", { close(); onShareLyric() }, icon = Icons.Rounded.FormatQuote)
                                val watchMv = LocalWatchMv.current
                                if (watchMv != null && track.id.startsWith("ncm:")) {
                                    GlassMenuItem("观看 MV", { close(); watchMv(track) }, icon = Icons.Rounded.OndemandVideo)
                                }
                                if (onListenTogether != null) {
                                    GlassMenuItem("网易云一起听", { close(); onListenTogether() }, icon = Icons.Rounded.Headphones)
                                }
                                GlassMenuItem("匹配封面与信息", { close(); onMatchMetadata() }, icon = Icons.Rounded.Image)
                                if (!track.isOnline) {
                                    GlassMenuItem("从音乐库移除", { close(); onRemoveFromLibrary() }, icon = Icons.Rounded.DeleteOutline)
                                }
                            } else {
                                GlassMenuItem("打开${listening.appName ?: "播放器"}", { close(); onOpenExternalApp() },
                                    icon = Icons.AutoMirrored.Rounded.OpenInNew)
                            }
                        }
                    },
                )
            }
        }
    }
    if (queueVisible) {
        QueueSheet(
            queue = state.queue,
            playback = state.playback,
            onDismiss = { queueVisible = false },
            onPlay = { index -> onPlayQueueIndex(index) },
        )
    }
}

@Composable
private fun PlayerBody(
    state: MainUiState,
    track: Track,
    lyrics: LyricsDocument,
    listening: ListeningInfo?,
    modifier: Modifier,
    shared: SharedArtworkState,
    pageActive: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onLyrics: () -> Unit,
    onFavorite: () -> Unit,
    onModeButton: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    onOpenLibrary: () -> Unit,
    onQueue: () -> Unit,
    onListening: (Boolean) -> Unit,
    onOpenExternalApp: () -> Unit,
    onTransition: (com.olbyine.music.data.TransitionMode) -> Unit,
    menu: @Composable () -> Unit,
) {
    val showPreview = state.settings.showLyricPreview && !LocalAppLayout.current.splitPlayer
    val glass = state.settings.playerGlassButtons && LocalStyleSpec.current.style != com.olbyine.music.data.UiStyle.MATERIAL
    androidx.compose.runtime.CompositionLocalProvider(LocalPlayerPageActive provides pageActive) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth > maxHeight && maxHeight < 460.dp) {
            LandscapePlayerBody(
                state, track, listening, maxHeight, shared, glass, onTogglePlay, onPrevious, onNext, onSeek, onLyrics,
                onFavorite, onModeButton, onOpenLibrary, onQueue, onListening, onOpenExternalApp, onTransition, menu,
            )
            return@BoxWithConstraints
        }
        val reserved = 300.dp + if (showPreview) 62.dp else 0.dp
        val coverSize = minOf(maxWidth - 56.dp, maxHeight - reserved, 400.dp).coerceAtLeast(if (maxHeight < 560.dp) 56.dp else 150.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            PlayerArtwork(
                track = track,
                size = coverSize,
                playing = state.playback.isPlaying,
                shared = shared,
                onTap = onLyrics,
                direction = rememberSkipDirection(state.playback.currentIndex, state.playback.mediaItemCount),
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(18.dp))
            TrackTitleRow(track, if (listening == null) onFavorite else null)
            Spacer(Modifier.height(14.dp))
            SeekBar(
                playback = state.playback,
                fallbackDuration = track.durationMs,
                onSeek = onSeek,
            ) { PlaybackBadges(state, track, listening) }
            Spacer(Modifier.height(6.dp))
            TransportControls(
                playback = state.playback,
                glass = glass,
                onToggle = onTogglePlay,
                onPrevious = onPrevious,
                onNext = onNext,
                onModeButton = onModeButton,
                modeEnabled = listening == null,
                trailing = { SideGlyph(Icons.Rounded.LibraryMusic, "音乐库", onOpenLibrary, glass) },
            )
            AnimatedVisibility(
                visible = showPreview,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(130)) + shrinkVertically(tween(180)),
            ) {
                if (listening != null && lyrics.lines.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (listening.searching) "正在匹配歌词…" else listening.message ?: "暂无歌词",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    PlayerLyricPreview(
                        document = lyrics,
                        trackId = track.id,
                        playback = state.playback,
                        offsetMs = state.settings.lyricOffsetMs,
                        active = pageActive,
                        onOpen = onLyrics,
                        showTranslation = state.settings.lyricTranslation,
                    )
                }
            }
            UtilityRow(
                listening = listening != null,
                queueSize = state.queue.size,
                onQueue = onQueue,
                onListening = onListening,
                onOpenExternalApp = onOpenExternalApp,
                transition = state.settings.transitionMode,
                transitionBlocked = state.settings.usbDirectDriver,
                onTransition = onTransition,
                menu = menu,
                showSource = showsSourceSwitch(state, listening != null),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
    }
}

@Composable
private fun PlaybackBadges(state: MainUiState, track: Track, listening: ListeningInfo?) {
    val shine = Modifier.shine(playerFx(com.olbyine.music.data.PlayerFx.BADGE_SHINE) && LocalPlayerPageActive.current)
    if (listening != null) {
        Badge(listening.appName ?: "外部播放器", color = MaterialTheme.colorScheme.primary, filled = true)
    } else {
        QualityBadge(track, shine)
        val driver by com.olbyine.music.playback.usb.UsbAudioDriver.state.collectAsState()
        if (driver.status == com.olbyine.music.playback.usb.UsbDriverStatus.STREAMING) {
            Spacer(Modifier.width(6.dp))
            Badge(
                if (driver.bitPerfect) "USB 直连 · BIT-PERFECT" else "USB 直连",
                shine,
                color = MaterialTheme.colorScheme.primary,
                filled = true,
            )
        } else when (state.usbBitPerfectState.status) {
            UsbBitPerfectStatus.ACTIVE -> {
                Spacer(Modifier.width(6.dp))
                Badge("BIT-PERFECT", shine, color = MaterialTheme.colorScheme.primary, filled = true)
            }
            UsbBitPerfectStatus.NATIVE_RATE -> {
                Spacer(Modifier.width(6.dp))
                Badge("USB 原采样率", shine, color = MaterialTheme.colorScheme.primary, filled = true)
            }
            else -> Unit
        }
    }
}

@Composable
private fun LandscapePlayerBody(
    state: MainUiState,
    track: Track,
    listening: ListeningInfo?,
    height: Dp,
    shared: SharedArtworkState,
    glass: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onLyrics: () -> Unit,
    onFavorite: () -> Unit,
    onModeButton: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    onOpenLibrary: () -> Unit,
    onQueue: () -> Unit,
    onListening: (Boolean) -> Unit,
    onOpenExternalApp: () -> Unit,
    onTransition: (com.olbyine.music.data.TransitionMode) -> Unit,
    menu: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cover = minOf(height - 20.dp, maxWidth * .40f).coerceIn(96.dp, 380.dp)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(.95f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                PlayerArtwork(track = track, size = cover, playing = state.playback.isPlaying, shared = shared, onTap = onLyrics, direction = rememberSkipDirection(state.playback.currentIndex, state.playback.mediaItemCount))
            }
            Spacer(Modifier.width(28.dp))
            Column(
                Modifier.weight(1.05f).widthIn(max = 480.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                if (height > 300.dp) {
                    Overline(
                        when {
                            listening != null -> "正在监听" + (listening.appName?.let { " · $it" } ?: "")
                            else -> track.album.takeIf(String::isNotBlank) ?: "正在播放"
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                TrackTitleRow(track, if (listening == null) onFavorite else null)
                Spacer(Modifier.height(if (height > 300.dp) 14.dp else 6.dp))
                SeekBar(playback = state.playback, fallbackDuration = track.durationMs, onSeek = onSeek) {
                    PlaybackBadges(state, track, listening)
                }
                TransportControls(
                    playback = state.playback,
                    glass = glass,
                    onToggle = onTogglePlay,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onModeButton = onModeButton,
                    modeEnabled = listening == null,
                    playSize = if (height > 300.dp) 62.dp else 54.dp,
                    trailing = { SideGlyph(Icons.Rounded.LibraryMusic, "音乐库", onOpenLibrary, glass) },
                )
                UtilityRow(
                    listening = listening != null,
                    queueSize = state.queue.size,
                    onQueue = onQueue,
                    onListening = onListening,
                    onOpenExternalApp = onOpenExternalApp,
                    transition = state.settings.transitionMode,
                    transitionBlocked = state.settings.usbDirectDriver,
                    onTransition = onTransition,
                    menu = menu,
                    showSource = showsSourceSwitch(state, listening != null),
                )
            }
        }
    }
}

@Composable
internal fun PlayerTopBar(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(width = 38.dp, height = 5.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .32f)),
        )
    }
    Column(
        modifier.fillMaxWidth().height(52.dp).padding(horizontal = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Overline(eyebrow, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f))
        if (title.isNotBlank()) {
            Text(
                title,
                Modifier.unlessReducedMotion { basicMarquee() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PlayerMenu(
    enabled: Boolean,
    hasLyrics: Boolean,
    onSearchLyrics: (LyricsSearchSource) -> Unit,
    onImportLyrics: () -> Unit,
    onOpenLibrary: (() -> Unit)? = null,
    searching: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    speed: Float? = null,
    onSpeed: (Float) -> Unit = {},
    showLyricTools: Boolean = true,
    quickActions: (@Composable androidx.compose.foundation.layout.RowScope.(close: () -> Unit) -> Unit)? = null,
    extraItems: @Composable (close: () -> Unit) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GhostIconButton(
            Icons.Rounded.MoreHoriz,
            "更多选项",
            { expanded = true },
            size = 46.dp,
            iconSize = 26.dp,
            tint = tint,
        )
        OlbyineDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val close = { expanded = false }
            androidx.compose.runtime.CompositionLocalProvider(LocalMenuDense provides true) {
                if (quickActions != null) {
                    MenuQuickRow { quickActions(close) }
                    MenuDivider()
                }
                if (speed != null) {
                    SpeedRow(speed, onSpeed)
                    MenuDivider()
                }
                extraItems(close)
                if (showLyricTools) {
                    MenuDivider()
                    MenuSubmenu(if (searching) "正在搜索歌词…" else "歌词", Icons.Rounded.Lyrics) {
                        GlassMenuItem(
                            if (hasLyrics) "替换本地歌词" else "导入本地歌词",
                            { close(); onImportLyrics() },
                            icon = Icons.Rounded.UploadFile,
                            enabled = enabled,
                        )
                        GlassMenuItem(
                            "在线匹配歌词",
                            { close(); onSearchLyrics(LyricsSearchSource.ALL) },
                            icon = Icons.Rounded.CloudDownload,
                            enabled = enabled && !searching,
                        )
                        GlassMenuItem(
                            "AMLL 逐字歌词库",
                            { close(); onSearchLyrics(LyricsSearchSource.AMLL) },
                            icon = Icons.Rounded.Lyrics,
                            enabled = enabled && !searching,
                        )
                        GlassMenuItem(
                            "UniLyric 多平台搜索",
                            { close(); onSearchLyrics(LyricsSearchSource.UNILYRIC) },
                            icon = Icons.Rounded.CloudDownload,
                            enabled = enabled && !searching,
                        )
                    }
                }
                if (onOpenLibrary != null) {
                    MenuDivider()
                    GlassMenuItem("打开音乐库", { close(); onOpenLibrary() }, icon = Icons.Rounded.LibraryMusic)
                }
            }
        }
    }
}

@Composable
internal fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)),
    )
}

@Composable
private fun SpeedRow(speed: Float, onSpeed: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyledIcon(Icons.Rounded.Speed, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("播放速度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SPEEDS.forEach { value ->
                val selected = abs(value - speed) < .001f
                val background by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
                    label = "speed-chip",
                )
                Box(
                    Modifier
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(background)
                        .pressable(label = "${value.formatSpeed()} 倍速") { onSpeed(value) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${value.formatSpeed()}×",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberPlayModeMenu(current: PlayMode, onPlayMode: (PlayMode) -> Unit): (androidx.compose.ui.layout.LayoutCoordinates) -> Unit {
    val host = LocalGlassPopupHost.current
    val key = remember { Any() }
    val currentMode by rememberUpdatedState(current)
    val select by rememberUpdatedState(onPlayMode)
    return remember(host) {
        { anchor ->
            host?.openAt(key, anchor, onDismiss = { host.close(key) }) {
                PlayMode.entries.forEach { mode ->
                    GlassMenuItem(
                        mode.label,
                        { host.close(key); select(mode) },
                        icon = mode.icon,
                        selected = mode == currentMode,
                    )
                }
            }
        }
    }
}

@Composable
internal fun playerButtonGlass(cornerRadius: Dp): GlassStyle =
    playerGlass(LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.IOS, cornerRadius)

@Composable
internal fun playerGlass(liquid: Boolean, cornerRadius: Dp): GlassStyle = if (liquid) {
    GlassStyle(
        cornerRadius = cornerRadius,
        blur = 8.dp,
        tint = Color.White.copy(alpha = .10f),
        saturation = 1.4f,
        contrast = 1.1f,
        lens = true,
        refractionHeight = 14.dp,
        refractionAmount = 18.dp,
        dispersion = true,
        depthEffect = true,
        highlight = Color.White.copy(alpha = .85f),
        highlightWidth = 0.9.dp,
        shadow = 12.dp,
        fallback = Color.White.copy(alpha = .14f),
    )
} else {
    GlassStyle(
        cornerRadius = cornerRadius,
        blur = 24.dp,
        tint = Color.Black.copy(alpha = .28f),
        saturation = 1.5f,
        highlight = Color.White.copy(alpha = .18f),
        shadow = 10.dp,
        fallback = Color.Black.copy(alpha = .5f),
    )
}

private val SOURCES = listOf(false to "本地", true to "监听")

private fun sourceIcon(listening: Boolean) = if (listening) Icons.Rounded.Headphones else Icons.Rounded.PhoneAndroid

@Composable
internal fun SourceSwitch(listening: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    when (LocalStyleSpec.current.switch) {
        SwitchKind.LIQUID -> LiquidSourceSwitch(listening, onChange, modifier)
        SwitchKind.MATERIAL -> MaterialSourceSwitch(listening, onChange, modifier)
        SwitchKind.COLOROS -> FlatSourceSwitch(listening, onChange, modifier, segment = 68.dp, height = 36.dp, inset = 3.dp)
        SwitchKind.HYPEROS -> FlatSourceSwitch(listening, onChange, modifier, segment = 74.dp, height = 40.dp, inset = 4.dp)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MaterialSourceSwitch(listening: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val muted = remember(colors.primary) { mutedAccent(colors.primary) }
    val ink = colors.onBackground
    SingleChoiceSegmentedButtonRow(modifier) {
        SOURCES.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == listening,
                onClick = { if (value != listening) onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, SOURCES.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = muted.first,
                    activeContentColor = muted.second,
                    activeBorderColor = ink.copy(alpha = .32f),
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = ink.copy(alpha = .72f),
                    inactiveBorderColor = ink.copy(alpha = .32f),
                ),
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Composable
private fun FlatSourceSwitch(
    listening: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier,
    segment: Dp,
    height: Dp,
    inset: Dp,
) {
    val density = LocalDensity.current
    val spec: androidx.compose.animation.core.AnimationSpec<Float> =
        if (LocalReduceMotion.current) androidx.compose.animation.core.snap() else spring(dampingRatio = .8f, stiffness = 520f)
    val position by animateFloatAsState(if (listening) 1f else 0f, spec, label = "source-flat")
    val ink = MaterialTheme.colorScheme.onBackground
    val onThumb = Color(0xFF1C1C1E)
    Box(
        modifier
            .width(segment * 2 + inset * 2)
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = .12f))
            .padding(inset),
    ) {
        Box(
            Modifier
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer { translationX = position * with(density) { segment.toPx() } }
                .shadow(2.dp, CircleShape)
                .background(Color.White.copy(alpha = .94f), CircleShape),
        )
        SourceSegments(listening, onChange, segment) { value ->
            val on = if (value) position else 1f - position
            androidx.compose.ui.graphics.lerp(ink.copy(alpha = .62f), onThumb, on)
        }
    }
}

@Composable
private fun SourceSegments(listening: Boolean, onChange: (Boolean) -> Unit, segment: Dp, tint: (Boolean) -> Color) {
    Row(Modifier.fillMaxHeight()) {
        SOURCES.forEach { (value, label) ->
            val selected = value == listening
            val color = tint(value)
            Row(
                Modifier
                    .width(segment)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClickLabel = label,
                    ) { if (!selected) onChange(value) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(sourceIcon(value), null, Modifier.size(15.dp), tint = color)
                Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun LiquidSourceSwitch(listening: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val source = LocalPlayerBackdrop.current
    val density = LocalDensity.current
    val segment = 72.dp
    val inset = 3.dp
    val position by animateFloatAsState(
        if (listening) 1f else 0f,
        spring(dampingRatio = .74f, stiffness = 420f),
        label = "source-switch",
    )
    val ink = MaterialTheme.colorScheme.onBackground
    val track = remember {
        GlassStyle(
            cornerRadius = 19.dp,
            blur = 10.dp,
            tint = Color.White.copy(alpha = .06f),
            saturation = 1.6f,
            highlight = Color.White.copy(alpha = .35f),
            fallback = Color.White.copy(alpha = .12f),
        )
    }
    val lens = remember {
        GlassStyle(
            cornerRadius = 16.dp,
            blur = 2.dp,
            tint = Color.White.copy(alpha = .16f),
            saturation = 1.9f,
            lens = true,
            refractionHeight = 10.dp,
            refractionAmount = 12.dp,
            dispersion = true,
            highlight = Color.White.copy(alpha = .9f),
            shadow = 6.dp,
            fallback = Color.White.copy(alpha = .22f),
        )
    }
    Box(
        modifier
            .width(segment * 2 + inset * 2)
            .height(38.dp)
            .glass(source, track)
            .padding(inset),
    ) {
        Box(
            Modifier
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = position * with(density) { segment.toPx() }
                    val travel = 1f - abs(position - .5f) * 2f
                    scaleX = 1f + .12f * travel
                    scaleY = 1f - .06f * travel
                }
                .glass(source, lens),
        )
        SourceSegments(listening, onChange, segment) { value -> ink.copy(alpha = if (value == listening) 1f else .6f) }
    }
}

private fun showsSourceSwitch(state: MainUiState, listening: Boolean): Boolean =
    listening || (state.settings.showSourceSwitch && state.currentTrack?.isOnline != true)

@Composable
private fun SourceRow(listening: Boolean, onListening: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
        SourceSwitch(listening, onListening)
    }
}

@Composable
private fun AccessCard(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StyledIcon(Icons.Rounded.NotificationsActive, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("需要“通知使用权”", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "Android 只允许有通知使用权的应用读取其他播放器的歌名和进度。Olbyine 只用它同步歌词，不读取任何通知内容。\n在系统页面里打开 Olbyine 的开关，返回后自动生效。",
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = .75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PillButton("前往系统设置", onGrant, icon = Icons.Rounded.NotificationsActive)
    }
}

@Composable
private fun PlayerArtwork(
    track: Track,
    size: Dp,
    playing: Boolean,
    shared: SharedArtworkState,
    onTap: () -> Unit,
    direction: Int = 1,
) {
    val reduce = LocalReduceMotion.current
    val scaleState = animateFloatAsState(
        targetValue = if (playing) 1f else .9f,
        animationSpec = spring(dampingRatio = .86f, stiffness = 260f),
        label = "cover-breath",
    )
    shared.scale = scaleState
    val shadowAlpha by animateFloatAsState(if (playing) .55f else .25f, tween(400), label = "cover-shadow")
    val glow = MaterialTheme.colorScheme.primary
    val currentTap by rememberUpdatedState(onTap)
    val tilt = rememberTiltGlare(playerFx(com.olbyine.music.data.PlayerFx.COVER_TILT))
    AnimatedContent(
        targetState = track,
        contentKey = { it.coverPath ?: it.id },
        transitionSpec = {
            if (reduce) fadeIn(tween(OlbyineTokens.Motion.routeMs)) togetherWith fadeOut(tween(160))
            else (
                fadeIn(tween(OlbyineTokens.Motion.routeMs)) +
                    scaleIn(initialScale = .94f, animationSpec = tween(OlbyineTokens.Motion.routeMs, easing = OlbyineTokens.Motion.easeOut)) +
                    androidx.compose.animation.slideInHorizontally(tween(OlbyineTokens.Motion.routeMs + 60, easing = OlbyineTokens.Motion.easeOut)) { (it * .14f * direction).toInt() }
                ).togetherWith(
                fadeOut(tween(160)) +
                    androidx.compose.animation.slideOutHorizontally(tween(220, easing = OlbyineTokens.Motion.easeOut)) { -(it * .08f * direction).toInt() },
            )
        },
        label = "playing-artwork",
    ) { shown ->
        val motion = LocalMotionArtwork.current
        Box(
            Modifier
                .onGloballyPositioned { coordinates ->
                    val page = shared.pageCoordinates
                    if (page != null && page.isAttached && coordinates.isAttached) {
                        shared.bounds = page.localBoundingBoxOf(coordinates, clipBounds = false)
                    }
                }
                .size(size)
                .graphicsLayer {
                    val s = scaleState.value
                    scaleX = s
                    scaleY = s
                    alpha = if (shared.transitioning()) 0f else 1f
                }
                .tiltLayer(tilt)
                .shadow(
                    elevation = 30.dp,
                    shape = RoundedCornerShape(OlbyineTokens.Corner.artwork),
                    ambientColor = glow.copy(alpha = shadowAlpha),
                    spotColor = Color.Black.copy(alpha = shadowAlpha),
                )
                .clip(RoundedCornerShape(OlbyineTokens.Corner.artwork))
                .tiltOverlay(tilt)
                .pointerInput(shown.id) {
                    detectTapGestures(onTap = { currentTap() })
                },
        ) {
            TrackArtwork(shown, Modifier.fillMaxSize(), corner = 0.dp, decodeEdge = LARGE_COVER_EDGE)
            val cover = motion?.cover
            if (cover != null) {
                MotionVideo(cover, playing = motion.playing, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun TrackTitleRow(track: Track, onFavorite: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            RevealText(
                track.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textModifier = Modifier.unlessReducedMotion { basicMarquee(iterations = 2, initialDelayMillis = 1_800) },
            )
            Spacer(Modifier.height(2.dp))
            RevealText(
                track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .66f),
                modifier = Modifier.fillMaxWidth(),
                delayMs = 90L,
                textModifier = Modifier.unlessReducedMotion { basicMarquee(iterations = 2, initialDelayMillis = 1_800) },
            )
        }
        if (onFavorite != null) {
            Spacer(Modifier.width(10.dp))
            val openNcm = LocalNeteaseComments.current
            val openSc = LocalScComments.current
            val comments: (() -> Unit)? = when {
                track.id.startsWith("ncm:") -> track.id.removePrefix("ncm:").toLongOrNull()?.let { id -> openNcm?.let { { it(id, track.title) } } }
                track.id.startsWith("sc:") -> track.id.removePrefix("sc:").toLongOrNull()?.let { id -> openSc?.let { { it(id, track.title) } } }
                else -> null
            }
            if (comments != null) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).pressable(pressedScale = .8f, label = "评论", onClick = comments),
                    contentAlignment = Alignment.Center,
                ) {
                    StyledIcon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = .72f))
                }
            }
            val lookup = LocalStreamLikeCount.current
            val base by androidx.compose.runtime.produceState<Long?>(null, track.id) {
                value = if (lookup != null && track.isOnline) runCatching { lookup(track) }.getOrNull() else null
            }
            val likedAtLoad = remember(track.id, base) { track.favorite }
            val count = base?.let { it + (if (track.favorite) 1 else 0) - (if (likedAtLoad) 1 else 0) }?.coerceAtLeast(0)
            if (track.isOnline) {
                val tint = MaterialTheme.colorScheme.onBackground
                HeartWithMenu(
                    track = { track },
                    liked = track.favorite,
                    onToggle = onFavorite,
                    count = count,
                    likedColor = MaterialTheme.colorScheme.primary,
                    idleColor = tint.copy(alpha = .72f),
                    textColor = tint.copy(alpha = .72f),
                    iconSize = 26.dp,
                    minSize = 46.dp,
                    showQueueActions = false,
                )
            } else {
                FavoriteButton(track.favorite, onFavorite, tint = MaterialTheme.colorScheme.onBackground, count = count)
            }
        }
    }
}

internal val LocalWatchMv = androidx.compose.runtime.staticCompositionLocalOf<((Track) -> Unit)?> { null }

internal val LocalStreamLikeCount = androidx.compose.runtime.staticCompositionLocalOf<(suspend (Track) -> Long?)?> { null }

@Composable
internal fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, tint: Color, size: Dp = 46.dp, count: Long? = null) {
    PulseHeart(
        liked = favorite,
        onToggle = onClick,
        count = count,
        likedColor = MaterialTheme.colorScheme.primary,
        idleColor = tint.copy(alpha = .72f),
        textColor = tint.copy(alpha = .72f),
        iconSize = 26.dp,
        minSize = size,
    )
}

@Composable
internal fun SeekBar(
    playback: PlaybackState,
    fallbackDuration: Long,
    onSeek: (Long) -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    centerContent: @Composable RowScope.() -> Unit = {},
) {
    val duration = playback.durationMs.takeIf { it > 0L } ?: fallbackDuration.coerceAtLeast(0L)
    var dragFraction by remember(playback.currentMediaId) { mutableStateOf<Float?>(null) }
    val clock = rememberPlaybackClock(playback, enabled = dragFraction == null)
    val pendingSeek = playback.pendingSeekMs
    val position: () -> Long = {
        dragFraction?.let { (it * duration).roundToLong() } ?: pendingSeek ?: clock.value
    }
    val shownSecond by remember(duration, pendingSeek, clock) { derivedStateOf { position() / 1_000L } }
    val dragging = dragFraction != null
    val thickness by animateDpAsState(if (dragging) 10.dp else 5.dp, spring(dampingRatio = .7f, stiffness = 700f), label = "seek-thickness")
    val currentSeek by rememberUpdatedState(onSeek)
    val currentDuration by rememberUpdatedState(duration)
    val shownMs = shownSecond * 1_000L
    val haptics = rememberHaptics()
    val elastic = playerFx(com.olbyine.music.data.PlayerFx.ELASTIC_SEEK)
    var stretch by remember { mutableFloatStateOf(0f) }
    var stretchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val stretchLimit = with(LocalDensity.current) { 34.dp.toPx() }
    val springBack: () -> Unit = {
        val from = stretch
        stretchJob?.cancel()
        stretchJob = if (from == 0f) null else scope.launch {
            androidx.compose.animation.core.animate(from, 0f, animationSpec = spring(dampingRatio = .42f, stiffness = 420f)) { value, _ ->
                stretch = value
            }
        }
    }
    fun pull(x: Float, width: Int) {
        if (!elastic) return
        val over = when {
            x < 0f -> x
            x > width -> x - width
            else -> 0f
        }
        stretchJob?.cancel()
        stretch = rubberband(over, stretchLimit)
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .excludeFromSheetGestures()
                .semantics {
                    contentDescription = "播放进度"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        if (duration > 0L) (shownMs.toFloat() / duration).coerceIn(0f, 1f) else 0f, 0f..1f,
                    )
                    setProgress { value ->
                        currentSeek((value.coerceIn(0f, 1f) * currentDuration).roundToLong()); true
                    }
                }
                .pointerInput(duration > 0L) {
                    if (duration <= 0L) return@pointerInput
                    detectTapGestures { offset ->
                        haptics.click(com.olbyine.music.data.HapticArea.SEEK)
                        currentSeek(((offset.x / size.width).coerceIn(0f, 1f) * currentDuration).roundToLong())
                    }
                }
                .pointerInput(duration > 0L) {
                    if (duration <= 0L) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            haptics.light(com.olbyine.music.data.HapticArea.SEEK)
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            pull(change.position.x, size.width)
                            val next = (change.position.x / size.width).coerceIn(0f, 1f)
                            val previous = dragFraction
                            if (previous != null && ((previous * 10).toInt() != (next * 10).toInt() || (next == 0f || next == 1f) && previous != next)) {
                                haptics.tick(com.olbyine.music.data.HapticArea.SEEK)
                            }
                            dragFraction = next
                        },
                        onDragEnd = {
                            haptics.click(com.olbyine.music.data.HapticArea.SEEK)
                            dragFraction?.let { currentSeek((it * currentDuration).roundToLong()) }
                            dragFraction = null
                            springBack()
                        },
                        onDragCancel = {
                            dragFraction = null
                            springBack()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                val fraction = if (duration > 0L) (position().toFloat() / duration).coerceIn(0f, 1f) else 0f
                val pulled = stretch
                val thin = 1f - .35f * (abs(pulled) / stretchLimit).coerceIn(0f, 1f)
                val bar = (thickness.toPx() * thin).coerceIn(1f, size.height)
                val top = (size.height - bar) / 2f
                val radius = CornerRadius(bar / 2f)
                val left = minOf(pulled, 0f)
                val width = size.width + abs(pulled)
                drawRoundRect(color = contentColor.copy(alpha = .18f), topLeft = Offset(left, top), size = Size(width, bar), cornerRadius = radius)
                drawRoundRect(
                    color = contentColor.copy(alpha = if (dragging) 1f else .92f),
                    topLeft = Offset(left, top),
                    size = Size(width * fraction, bar),
                    cornerRadius = radius,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            val timeColor = contentColor.copy(alpha = if (dragging) .95f else .6f)
            val remaining = (duration - shownMs).coerceAtLeast(0L)
            RollingTime(formatDuration(shownMs), shownMs, timeStyle, timeColor, Modifier.width(64.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                centerContent()
            }
            RollingTime("-" + formatDuration(remaining), remaining, timeStyle, timeColor, Modifier.width(64.dp), alignEnd = true)
        }
    }
}

@Composable
internal fun TransportControls(
    playback: PlaybackState,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onModeButton: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    glass: Boolean = false,
    modeEnabled: Boolean = true,
    playSize: Dp = 76.dp,
    trailing: @Composable () -> Unit = { Spacer(Modifier.size(46.dp)) },
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val kind = LocalStyleSpec.current.transport
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (modeEnabled) ModeButton(playback.playMode(), glass, onModeButton) else Spacer(Modifier.size(46.dp))
        if (glass && kind == TransportKind.GLYPHS) {
            TransportCapsule(playback, onToggle, onPrevious, onNext, height = playSize * .92f)
        } else {
            GlyphButton(Icons.Rounded.FastRewind, "上一首", onPrevious, size = playSize * .82f, iconSize = playSize * .52f, tint = ink)
            if (kind == TransportKind.GLYPHS) {
                GlyphButton(
                    icon = null,
                    description = if (playback.isPlaying) "暂停" else "播放",
                    onClick = onToggle,
                    size = playSize,
                    iconSize = playSize * .66f,
                    tint = ink,
                ) {
                    PlayPauseGlyph(playback, playSize * .7f, ink)
                }
            } else {
                SolidPlayButton(kind, playback, onToggle, playSize)
            }
            GlyphButton(Icons.Rounded.FastForward, "下一首", onNext, size = playSize * .82f, iconSize = playSize * .52f, tint = ink)
        }
        trailing()
    }
}

internal fun mutedAccent(accent: Color): Pair<Color, Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    val container = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], minOf(hsv[1] * .55f, .30f), .70f)))
    val content = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], minOf(hsv[1] * .25f, .14f), 1f)))
    return container.copy(alpha = .34f) to content
}

@Composable
private fun SolidPlayButton(kind: TransportKind, playback: PlaybackState, onToggle: () -> Unit, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val shape = if (kind == TransportKind.TONAL_SQUARE) RoundedCornerShape(size * .34f) else CircleShape
    val muted = remember(colors.primary) { mutedAccent(colors.primary) }
    val container = when (kind) {
        TransportKind.TONAL_SQUARE -> muted.first
        TransportKind.ACCENT_CIRCLE -> colors.primary
        else -> Color.White
    }
    val content = when (kind) {
        TransportKind.TONAL_SQUARE -> muted.second
        TransportKind.ACCENT_CIRCLE -> colors.onPrimary
        else -> Color.Black
    }
    val description = if (playback.isPlaying) "暂停" else "播放"
    Box(
        Modifier
            .size(width = if (kind == TransportKind.TONAL_SQUARE) size * 1.18f else size * .96f, height = size * .96f)
            .graphicsLayer {
                val scale = 1f - .1f * press.value
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (kind == TransportKind.TONAL_SQUARE) 0.dp else 12.dp, shape)
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onClick = onToggle,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseGlyph(playback, size * .5f, content)
    }
}

@Composable
private fun PlayPauseGlyph(playback: PlaybackState, size: Dp, tint: Color) {
    if (playback.isBuffering) {
        OlbyineSpinner(Modifier.size(size * .5f), color = tint, strokeWidth = 2.5.dp)
    } else {
        AnimatedContent(
            targetState = playback.isPlaying,
            transitionSpec = {
                (fadeIn(tween(160, easing = OlbyineTokens.Motion.easeOut)) +
                    scaleIn(initialScale = .8f, animationSpec = appleSpring(response = .22f)))
                    .togetherWith(fadeOut(tween(90)) + scaleOut(targetScale = .6f, animationSpec = tween(120, easing = OlbyineTokens.Motion.easeOut)))
            },
            label = "play-state",
        ) { isPlaying ->
            StyledIcon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(size).then(morphBlur()), tint = tint)
        }
    }
}

@Composable
private fun TransportCapsule(
    playback: PlaybackState,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    height: Dp,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val source = LocalPlayerBackdrop.current
    val previous = remember { MutableInteractionSource() }
    val play = remember { MutableInteractionSource() }
    val next = remember { MutableInteractionSource() }
    val pressPrevious = rememberPressProgress(previous)
    val pressPlay = rememberPressProgress(play)
    val pressNext = rememberPressProgress(next)
    val lift = remember { derivedStateOf { maxOf(pressPrevious.value, pressPlay.value, pressNext.value) } }
    val style = playerButtonGlass(height / 2)
    val dynamics = remember { GlassDynamics(lensProgress = { 1f + .5f * lift.value }) }
    Row(
        Modifier
            .height(height)
            .graphicsLayer {
                val s = 1f + .035f * lift.value
                scaleX = s
                scaleY = s
            }
            .glass(source, style, dynamics = dynamics)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleGlyph(previous, "上一首", onPrevious, width = height * .78f, height = height) {
            StyledIcon(Icons.Rounded.FastRewind, null, Modifier.size(height * .42f), tint = ink)
        }
        CapsuleGlyph(play, if (playback.isPlaying) "暂停" else "播放", onToggle, width = height * .96f, height = height) {
            PlayPauseGlyph(playback, height * .56f, ink)
        }
        CapsuleGlyph(next, "下一首", onNext, width = height * .78f, height = height) {
            StyledIcon(Icons.Rounded.FastForward, null, Modifier.size(height * .42f), tint = ink)
        }
    }
}

@Composable
private fun CapsuleGlyph(
    interaction: MutableInteractionSource,
    description: String,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    content: @Composable () -> Unit,
) {
    val press = rememberPressProgress(interaction)
    Box(
        Modifier
            .width(width)
            .height(height)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(minOf(width, height) * .82f)
                .graphicsLayer {
                    val glow = press.value.coerceIn(0f, 1f)
                    alpha = glow
                    val s = .7f + .3f * glow
                    scaleX = s
                    scaleY = s
                }
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = 0f)),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier.graphicsLayer {
                val scale = 1f - .14f * press.value
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun ModeButton(
    mode: PlayMode,
    glass: Boolean,
    onOpen: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val active = mode != PlayMode.SEQUENTIAL
    val tint by animateColorAsState(if (active) accent else ink.copy(alpha = .62f), label = "mode-tint")
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    Box(
        Modifier
            .size(46.dp)
            .onGloballyPositioned { coords = it }
            .then(if (glass) Modifier.glass(LocalPlayerBackdrop.current, playerButtonGlass(23.dp)) else Modifier)
            .clip(CircleShape)
            .pressable(label = "播放模式：${mode.label}") { coords?.let(onOpen) },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(160, easing = OlbyineTokens.Motion.easeOut)) +
                    scaleIn(initialScale = .8f, animationSpec = appleSpring(response = .22f)))
                    .togetherWith(fadeOut(tween(100)))
            },
            label = "mode-icon",
        ) { current ->
            Icon(current.icon, null, Modifier.size(23.dp).then(morphBlur()), tint = tint)
        }
    }
}

@Composable
internal fun SideGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    glass: Boolean,
    enabled: Boolean = true,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .size(46.dp)
            .then(if (glass) Modifier.glass(LocalPlayerBackdrop.current, playerButtonGlass(23.dp)) else Modifier)
            .clip(CircleShape)
            .pressable(label = description, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StyledIcon(icon, description, Modifier.size(23.dp), tint = ink.copy(alpha = if (enabled) .62f else .3f))
    }
}

@Composable
private fun GlyphButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    description: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    tint: Color,
    content: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    Box(
        Modifier
            .size(size)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val halo = press.value.coerceIn(0f, 1f)
                    alpha = halo
                    scaleX = .72f + .28f * halo
                    scaleY = .72f + .28f * halo
                }
                .clip(CircleShape)
                .background(tint.copy(alpha = .14f)),
        )
        Box(
            Modifier.graphicsLayer {
                val scale = 1f - .16f * press.value
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center,
        ) {
            if (content != null) content()
            else if (icon != null) StyledIcon(icon, null, Modifier.size(iconSize), tint = tint)
        }
    }
}

@Composable
private fun UtilityRow(
    listening: Boolean,
    queueSize: Int,
    onQueue: () -> Unit,
    onListening: (Boolean) -> Unit,
    onOpenExternalApp: () -> Unit,
    transition: com.olbyine.music.data.TransitionMode,
    transitionBlocked: Boolean,
    onTransition: (com.olbyine.music.data.TransitionMode) -> Unit,
    menu: @Composable () -> Unit,
    showSource: Boolean = true,
) {
    val ink = MaterialTheme.colorScheme.onBackground.copy(alpha = .72f)
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        menu()
        if (!listening) TransitionButton(transition, transitionBlocked, onTransition, ink)
        if (showSource) SourceSwitch(listening, onListening)
        if (listening) {
            GhostIconButton(Icons.AutoMirrored.Rounded.OpenInNew, "打开播放器", onOpenExternalApp, size = 46.dp, iconSize = 22.dp, tint = ink)
        } else {
            GhostIconButton(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列，$queueSize 首", onQueue, size = 46.dp, iconSize = 24.dp, tint = ink, enabled = queueSize > 0)
        }
    }
}

@Composable
private fun TransitionButton(
    mode: com.olbyine.music.data.TransitionMode,
    blocked: Boolean,
    onSelect: (com.olbyine.music.data.TransitionMode) -> Unit,
    ink: Color,
) {
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val active = mode != com.olbyine.music.data.TransitionMode.OFF && !blocked
    Box {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.onBackground.copy(alpha = .14f) else Color.Transparent)
                .pressable(label = "歌曲过渡") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AllInclusive,
                "歌曲过渡：${mode.label}",
                Modifier.size(24.dp),
                tint = if (active) MaterialTheme.colorScheme.onBackground else ink,
            )
        }
        OlbyineDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            com.olbyine.music.data.TransitionMode.entries.forEach { option ->
                GlassMenuItem(
                    option.label,
                    {
                        open = false
                        haptics.tick(com.olbyine.music.data.HapticArea.MENUS)
                        onSelect(option)
                    },
                    icon = when (option) {
                        com.olbyine.music.data.TransitionMode.OFF -> Icons.Rounded.Block
                        com.olbyine.music.data.TransitionMode.CROSSFADE -> Icons.Rounded.SwapHoriz
                        com.olbyine.music.data.TransitionMode.AUTOMIX -> Icons.Rounded.AllInclusive
                    },
                    selected = option == mode,
                )
            }
            if (blocked) {
                Text(
                    "USB 独占驱动开启时不能过渡",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<Track>,
    playback: PlaybackState,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val currentIndex = playback.currentIndex
    GlassBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("播放队列", style = MaterialTheme.typography.headlineSmall)
                Text(
                    buildString {
                        append("${queue.size} 首 · ${formatTotalDuration(queue.sumOf { it.durationMs })}")
                        if (playback.shuffleEnabled) append(" · 随机")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            itemsIndexed(queue, key = { index, track -> "${track.id}-$index" }) { index, track ->
                val current = index == currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else Color.Transparent)
                        .clickable { onPlay(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                        if (current) {
                            EqualizerBars(playback.isPlaying, MaterialTheme.colorScheme.primary, Modifier.size(14.dp))
                        } else {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (index < currentIndex) .5f else 1f),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TrackArtwork(track, Modifier.size(44.dp), OlbyineTokens.Corner.thumb)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            track.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatDuration(track.durationMs),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
