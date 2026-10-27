@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.olbyine.music.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.layout.onGloballyPositioned
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.OnlineLyricsUiState
import com.olbyine.music.data.LyricsCandidate
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.data.LyricsSearchSource
import com.olbyine.music.data.Track
import com.olbyine.music.playback.PlaybackState
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

@Composable
internal fun LyricsPage(
    track: Track,
    document: LyricsDocument,
    playback: PlaybackState,
    visible: Boolean,
    fontSize: Float,
    lineGap: Float = 1.2f,
    fontWeight: Int,
    fontPath: String?,
    inactiveOpacity: Float,
    offsetMs: Long,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
    onImportLyrics: () -> Unit,
    onSearchLyrics: (LyricsSearchSource) -> Unit,
    lyricsSourceVisible: Boolean,
    onlineSearching: Boolean,
    queue: List<Track>,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayMode: (PlayMode) -> Unit,
    glassButtons: Boolean,
    onFavorite: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    externalStatus: String? = null,
    split: Boolean = false,
    lineBlur: Boolean = false,
    showTranslation: Boolean = true,
    showRoman: Boolean = true,
    onShowTranslation: ((Boolean) -> Unit)? = null,
    onShowRoman: ((Boolean) -> Unit)? = null,
    subScale: Float = .5f,
    doubleTapSeek: Boolean = true,
    mvStyle: MvStyle? = null,
    onMvStyle: ((MvStyle?) -> Unit)? = null,
    alignPosition: Float = .5f,
    artworkColors: List<Int> = emptyList(),
) {
    var mvMenu by remember { mutableStateOf(false) }
    val listening = externalStatus != null
    var queueVisible by remember { mutableStateOf(false) }
    val openQueue = LocalQueuePanel.current
    val popupOpen = LocalGlassPopupHost.current?.current != null
    val current = document.trackId == null || document.trackId == track.id
    @Suppress("NAME_SHADOWING")
    val document = if (current) document else LyricsDocument.Empty
    val rawTtml = if (current) document.rawTtml else null
    var controlsVisible by remember(track.id) { mutableStateOf(true) }
    var pointerHeld by remember { mutableStateOf(false) }
    var interactionRevision by remember { mutableIntStateOf(0) }
    val accessibility = LocalAccessibilityManager.current
    val idleTimeout = accessibility?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = 4_000L,
        containsIcons = true,
        containsText = true,
        containsControls = true,
    ) ?: 4_000L
    fun revealControls() {
        controlsVisible = true
        interactionRevision++
    }
    val openModeMenu = rememberPlayModeMenu(playback.playMode()) { mode -> revealControls(); onPlayMode(mode) }
    LaunchedEffect(visible) { if (!visible) controlsVisible = true }
    LaunchedEffect(track.id, visible, controlsVisible, pointerHeld, interactionRevision, queueVisible, lyricsSourceVisible, idleTimeout, popupOpen) {
        if (visible && controlsVisible && !pointerHeld && !queueVisible && !lyricsSourceVisible && !popupOpen) {
            delay(idleTimeout)
            controlsVisible = false
        }
    }
    val hasLyrics = !(document.lines.isEmpty() && rawTtml.isNullOrBlank())
    val hasTranslation = remember(document, rawTtml) {
        document.lines.any { !it.translation.isNullOrBlank() } || rawTtml?.contains("x-translation") == true
    }
    val hasRoman = remember(document, rawTtml) {
        document.lines.any { !it.romanization.isNullOrBlank() } || rawTtml?.contains("x-roman") == true
    }
    val lyricKind = when {
        !hasLyrics -> null
        !rawTtml.isNullOrBlank() || document.lines.any { it.words.isNotEmpty() } -> "逐字歌词"
        else -> "逐行歌词"
    }
    val density = LocalDensity.current
    val statusTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value }
    val navBottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
    var controlsHeightDp by remember { mutableIntStateOf(210) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(track.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val nextHeld = event.changes.any { it.pressed }
                        if (pointerHeld != nextHeld) {
                            pointerHeld = nextHeld
                            interactionRevision++
                            if (nextHeld) controlsVisible = true
                        }
                    }
                }
            },
    ) {
        if (!hasLyrics && listening) {
            EmptyState(
                icon = Icons.Rounded.Lyrics,
                title = externalStatus ?: "暂无歌词",
                message = "${track.title} · ${track.artist}",
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, bottom = 200.dp)
                    .pointerInput(track.id) { detectTapGestures(onTap = { revealControls() }) },
            )
        } else if (!hasLyrics) {
            EmptyState(
                icon = Icons.Rounded.Lyrics,
                title = if (track.lyricsInstrumental) "纯音乐，请静心聆听" else "这首歌还没有歌词",
                message = if (track.lyricsInstrumental) "歌词源将这首歌标记为纯音乐" else "导入 LRC / TTML 文件，或从在线歌词库匹配",
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, bottom = 200.dp)
                    .pointerInput(track.id) { detectTapGestures(onTap = { revealControls() }) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("导入本地", onImportLyrics, icon = Icons.Rounded.UploadFile, style = PillStyle.TONAL)
                    PillButton(
                        if (onlineSearching) "搜索中…" else "在线匹配",
                        { onSearchLyrics(LyricsSearchSource.ALL) },
                        icon = Icons.Rounded.CloudDownload,
                        enabled = !onlineSearching,
                    )
                }
            }
        } else if (mvStyle != null && document.isSynchronized && document.lines.isNotEmpty()) {
            MvLyricsView(
                document = document,
                playback = playback,
                offsetMs = offsetMs,
                active = visible,
                showTranslation = showTranslation,
                style = mvStyle,
                modifier = Modifier.fillMaxSize(),
                lineGap = lineGap,
                onTap = { if (controlsVisible) controlsVisible = false else revealControls() },
            )
        } else {
            AmllLyricsView(
                document = document,
                rawTtml = rawTtml,
                playback = playback,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontPath = fontPath,
                inactiveOpacity = inactiveOpacity,
                lineGap = lineGap,
                offsetMs = offsetMs,
                onLineTap = { position ->
                    if (position != null) onSeek(position)
                    revealControls()
                },
                onTouching = { touching ->
                    if (pointerHeld != touching) {
                        pointerHeld = touching
                        interactionRevision++
                        if (touching) controlsVisible = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
                active = visible,
                fadeTopDp = statusTopDp + 64f,
                fadeBottomDp = if (controlsVisible && !split) navBottomDp + controlsHeightDp else navBottomDp + 12f,
                lineBlur = lineBlur,
                showTranslation = showTranslation,
                showRoman = showRoman,
                subScale = subScale,
                doubleTapSeek = doubleTapSeek,
                alignPosition = alignPosition,
            )
        }

        Box(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200, easing = OlbyineTokens.Motion.easeOut)),
                exit = fadeOut(tween(180, easing = OlbyineTokens.Motion.easeOut)),
            ) {
                LyricsTopBar(
                    track = track,
                    lyricKind = lyricKind,
                    onBack = onBack,
                    onFavorite = if (listening) null else onFavorite,
                    onImportLyrics = onImportLyrics,
                    onSearchLyrics = onSearchLyrics,
                    onlineSearching = onlineSearching,
                    queueAvailable = queue.isNotEmpty(),
                    onQueue = { revealControls(); openQueue?.invoke() ?: run { queueVisible = true } },
                    lyricTools = !listening,
                    subToggles = {
                        if (onShowTranslation != null && hasTranslation) {
                            LyricSubToggle("译", "翻译", showTranslation) { revealControls(); onShowTranslation(!showTranslation) }
                        }
                        if (onShowRoman != null && hasRoman) {
                            LyricSubToggle("音", "罗马音", showRoman) { revealControls(); onShowRoman(!showRoman) }
                        }
                        if (onMvStyle != null && document.isSynchronized && document.lines.isNotEmpty()) {
                            Box {
                                LyricSubToggle("MV", "MV 歌词", mvStyle != null) { revealControls(); mvMenu = true }
                                OlbyineDropdownMenu(expanded = mvMenu, onDismissRequest = { mvMenu = false }) {
                                    GlassMenuItem("普通歌词", { mvMenu = false; onMvStyle(null) }, selected = mvStyle == null)
                                    MvStyle.entries.forEach { option ->
                                        GlassMenuItem("MV 歌词 · ${option.label}", { mvMenu = false; onMvStyle(option) }, selected = mvStyle == option)
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible && !split,
                enter = fadeIn(tween(220, easing = OlbyineTokens.Motion.easeOut)) +
                    slideInVertically(tween(260, easing = OlbyineTokens.Motion.easeOut)) { it / 6 },
                exit = fadeOut(tween(180, easing = OlbyineTokens.Motion.easeOut)) +
                    slideOutVertically(tween(200, easing = OlbyineTokens.Motion.easeOut)) { it / 8 },
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .onSizeChanged { controlsHeightDp = with(density) { it.height.toDp().value.toInt() } }
                        .padding(start = 26.dp, end = 26.dp, top = 4.dp, bottom = 10.dp),
                ) {
                    SeekBar(playback, track.durationMs, onSeek = { revealControls(); onSeek(it) })
                    TransportControls(
                        playback = playback,
                        onToggle = { revealControls(); onTogglePlay() },
                        onPrevious = { revealControls(); onPrevious() },
                        onNext = { revealControls(); onNext() },
                        onModeButton = { button -> revealControls(); openModeMenu(button) },
                        glass = glassButtons,
                        modeEnabled = !listening,
                        playSize = 66.dp,
                    ) {
                        SideGlyph(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", { revealControls(); openQueue?.invoke() ?: run { queueVisible = true } }, glassButtons, enabled = queue.isNotEmpty())
                    }
                }
            }
        }
    }
    if (queueVisible) {
        QueueSheet(
            queue = queue,
            playback = playback,
            onDismiss = { queueVisible = false },
            onPlay = onPlayQueueIndex,
            artworkColors = artworkColors,
        )
    }
}

@Composable
private fun LyricsTopBar(
    track: Track?,
    lyricKind: String?,
    onBack: () -> Unit,
    onFavorite: (() -> Unit)?,
    onImportLyrics: () -> Unit,
    onSearchLyrics: (LyricsSearchSource) -> Unit,
    onlineSearching: Boolean,
    queueAvailable: Boolean,
    onQueue: () -> Unit,
    lyricTools: Boolean,
    subToggles: @Composable () -> Unit = {},
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "返回播放页", onBack, size = 46.dp, iconSize = 32.dp, tint = ink)
        if (track != null) {
            TrackArtwork(track, Modifier.size(42.dp), OlbyineTokens.Corner.thumb)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    Modifier.unlessReducedMotion { basicMarquee() },
                    style = MaterialTheme.typography.titleSmall,
                    color = ink,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(track.artist, lyricKind).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = .62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { subToggles() }
            if (onFavorite != null) FavoriteButton(track.favorite, onFavorite, tint = ink, size = 44.dp)
        } else {
            Spacer(Modifier.weight(1f))
        }
        PlayerMenu(
            enabled = track != null,
            hasLyrics = track?.hasLyrics == true,
            onSearchLyrics = onSearchLyrics,
            onImportLyrics = onImportLyrics,
            searching = onlineSearching,
            showLyricTools = lyricTools,
        ) { close ->
            GlassMenuItem("播放队列", { close(); onQueue() }, icon = Icons.AutoMirrored.Rounded.QueueMusic, enabled = queueAvailable)
        }
    }
}

@Composable
private fun LyricSubToggle(glyph: String, label: String, on: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    val fill by androidx.compose.animation.core.animateFloatAsState(
        if (on) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = .7f, stiffness = 520f), label = "sub-toggle",
    )
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .pressable(label = if (on) "隐藏$label" else "显示$label") {
                haptics.tick(com.olbyine.music.data.HapticArea.LYRICS)
                onClick()
            }
            .drawBehind {
                drawCircle(ink.copy(alpha = .28f * (1f - fill)), radius = size.minDimension / 2f - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
                if (fill > 0f) drawCircle(ink.copy(alpha = .92f), radius = size.minDimension / 2f * fill.coerceIn(0f, 1.05f))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            Modifier.graphicsLayer { val s = .92f + .08f * fill; scaleX = s; scaleY = s },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.ui.graphics.lerp(ink.copy(alpha = .7f), MaterialTheme.colorScheme.background, fill.coerceIn(0f, 1f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnlineLyricsSheet(
    state: OnlineLyricsUiState,
    trackTitle: String?,
    onSelect: (LyricsCandidate) -> Unit,
    onSourceChange: (LyricsSearchSource) -> Unit,
    onQueryChange: (String, String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val busy = state.searching || state.downloading
    GlassBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Text("搜索歌词", style = MaterialTheme.typography.headlineSmall)
            if (!trackTitle.isNullOrBlank()) {
                Text(
                    "为「$trackTitle」选择一份歌词",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OlbyineTextField(state.queryTitle, { onQueryChange(it, state.queryArtist) }, "歌曲名", Modifier.weight(1.25f))
                OlbyineTextField(state.queryArtist, { onQueryChange(state.queryTitle, it) }, "歌手（可空）", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        PillTabs(
            items = listOf(
                LyricsSearchSource.ALL to "全部来源",
                LyricsSearchSource.QQ_MUSIC to "QQ 音乐",
                LyricsSearchSource.NETEASE to "网易云",
                LyricsSearchSource.KUGOU to "酷狗",
                LyricsSearchSource.APPLE_MUSIC to "Apple Music",
                LyricsSearchSource.AMLL to "AMLL 逐字库",
                LyricsSearchSource.UNILYRIC to "UniLyric 多平台",
            ),
            selected = state.selectedSource,
            onSelect = onSourceChange,
            contentPadding = PaddingValues(horizontal = 22.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (state.selectedSource) {
                    LyricsSearchSource.AMLL -> "选择结果后下载 TTML 逐字歌词"
                    LyricsSearchSource.UNILYRIC -> "QQ 音乐、网易云、酷狗与 AMLL 歌词库"
                    LyricsSearchSource.APPLE_MUSIC -> "Apple Music 官方逐字歌词（需要订阅令牌）"
                    LyricsSearchSource.QQ_MUSIC -> "QQ 音乐 QRC 逐字歌词，含翻译与罗马音"
                    LyricsSearchSource.NETEASE -> "网易云 YRC 逐字歌词，含翻译"
                    LyricsSearchSource.KUGOU -> "酷狗 KRC 逐字歌词"
                    else -> "汇总多个在线歌词源，按匹配度排序"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            PillButton(
                "搜索",
                onRetry,
                icon = Icons.Rounded.Search,
                enabled = state.queryTitle.isNotBlank() && !state.downloading,
                height = 38.dp,
            )
        }
        Spacer(Modifier.height(10.dp))
        RevealIn(busy) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
                OlbyineProgressBar(
                    null,
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        state.downloading -> "正在下载所选歌词…"
                        state.selectedSource == LyricsSearchSource.AMLL -> "正在搜索 AMLL 歌词库…"
                        state.selectedSource == LyricsSearchSource.UNILYRIC -> "正在搜索 UniLyric 多平台歌词…"
                        state.selectedSource == LyricsSearchSource.APPLE_MUSIC -> "正在搜索 Apple Music…"
                        state.selectedSource == LyricsSearchSource.QQ_MUSIC -> "正在搜索 QQ 音乐…"
                        state.selectedSource == LyricsSearchSource.NETEASE -> "正在搜索网易云音乐…"
                        state.selectedSource == LyricsSearchSource.KUGOU -> "正在搜索酷狗音乐…"
                        else -> "正在搜索在线歌词…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.message?.let { message ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GhostIconButton(Icons.Rounded.Refresh, "重试", onRetry, size = 40.dp, iconSize = 20.dp, tint = MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.candidates, key = { index, candidate -> "$index:${candidate.id}" }) { _, candidate ->
                CandidateRow(candidate, enabled = !busy) { onSelect(candidate) }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: LyricsCandidate, enabled: Boolean, onClick: () -> Unit) {
    val confidence = (candidate.confidence * 100).roundToLong().coerceIn(0, 100)
    val strong = confidence >= 80
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(if (strong) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$confidence",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                candidate.title.ifBlank { "未知歌曲" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(candidate.artist, candidate.album).filter(String::isNotBlank).joinToString(" · ")
                    .ifBlank { "未提供元数据" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Badge(candidateSourceLabel(candidate.source))
                if (candidate.hasWordTiming) Badge("逐字", color = MaterialTheme.colorScheme.primary, filled = true)
                if (candidate.translationLyrics.isNotBlank()) Badge("翻译")
                if (candidate.databaseSupport > 1) Badge("${candidate.databaseSupport} 源一致")
            }
        }
    }
}

private fun candidateSourceLabel(source: String): String = when (source) {
    "lrclib" -> "LRCLIB"
    "netease" -> "网易云"
    "qqmusic" -> "QQ 音乐"
    "kugou" -> "酷狗"
    "amll-ttml" -> "AMLL"
    "applemusic" -> "Apple Music"
    "meting" -> "Meting"
    "wwoyun" -> "Wwoyun"
    "lrcapi" -> "LrcApi"
    else -> source
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
