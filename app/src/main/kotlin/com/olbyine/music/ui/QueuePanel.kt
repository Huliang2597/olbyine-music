@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.olbyine.music.ui

import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.Track
import com.olbyine.music.data.UiStyle
import com.olbyine.music.playback.PlaybackState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val LocalQueuePanel = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
private fun queueGlass(): GlassStyle {
    val spec = LocalStyleSpec.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    return when (spec.style) {
        UiStyle.IOS -> GlassStyle(
            cornerRadius = 34.dp,
            blur = 30.dp,
            tint = if (dark) Color.Black.copy(alpha = .30f) else Color.White.copy(alpha = .40f),
            saturation = 1.9f,
            lens = true,
            refractionHeight = 22.dp,
            refractionAmount = 30.dp,
            dispersion = true,
            highlight = Color.White.copy(alpha = if (dark) .62f else .9f),
            highlightWidth = 1.dp,
            shadow = 24.dp,
            fallback = colors.surfaceContainerHigh,
        )
        UiStyle.HYPEROS -> GlassStyle(
            cornerRadius = 32.dp,
            blur = 40.dp,
            tint = if (dark) Color(0xFF1C1C1E).copy(alpha = .55f) else Color.White.copy(alpha = .55f),
            saturation = 1.6f,
            highlight = Color.White.copy(alpha = if (dark) .16f else .6f),
            highlightWidth = 1.dp,
            shadow = 20.dp,
            fallback = colors.surfaceContainerHigh,
        )
        UiStyle.COLOROS -> GlassStyle(
            cornerRadius = 26.dp,
            blur = 32.dp,
            tint = colors.surfaceContainer.copy(alpha = .72f),
            saturation = 1.5f,
            shadow = 16.dp,
            fallback = colors.surfaceContainer,
        )
        UiStyle.MATERIAL -> GlassStyle(
            cornerRadius = 28.dp,
            blur = 0.dp,
            tint = colors.surfaceContainerLow,
            saturation = 1f,
            shadow = 8.dp,
            fallback = colors.surfaceContainerLow,
        )
    }
}

@Composable
internal fun QueuePanel(
    visible: Boolean,
    source: BackdropSource,
    queue: List<Track>,
    playback: PlaybackState,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    actions: QueueItemActions? = null,
) {
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var dragOffset by remember { mutableStateOf(0f) }
    val handoff = remember { floatArrayOf(0f, 0f) }
    val settleJob = remember { arrayOfNulls<Job>(1) }
    LaunchedEffect(visible) {
        settleJob[0]?.cancel()
        var velocity = 0f
        val travel = handoff[0]
        if (dragOffset != 0f && travel > 0f) {
            progress.snapTo((progress.value - dragOffset / travel).coerceIn(0f, 1f))
            velocity = -handoff[1] / travel
        }
        dragOffset = 0f
        handoff[1] = 0f
        progress.animateTo(
            if (visible) 1f else 0f,
            spring(dampingRatio = if (visible) .82f else 1f, stiffness = if (visible) 380f else 600f),
            initialVelocity = velocity,
        )
    }
    GlassMotion.Drive { progress.value; dragOffset }
    if (!visible && progress.value <= 0.001f) return
    BackHandler(enabled = visible, onBack = onDismiss)
    val style = queueGlass()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelHeight = maxHeight * .74f
        val panelPx = with(density) { panelHeight.toPx() }
        handoff[0] = panelPx + 40f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (progress.value * (1f - dragOffset.coerceAtLeast(0f) / panelPx.coerceAtLeast(1f))).coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = .30f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight)
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                .graphicsLayer {
                    translationY = (1f - progress.value) * (panelPx + 40f) + dragOffset
                }
                .glass(source, style)
                .navigationBarsPadding(),
        ) { androidx.compose.runtime.CompositionLocalProvider(LocalOnGlass provides true, androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(panelPx) {
                            val tracker = VelocityTracker()
                            var dragged = 0f
                            val springBack: (Float) -> Unit = { velocity ->
                                settleJob[0] = scope.launch {
                                    Animatable(dragOffset).animateTo(0f, spring(dampingRatio = .8f, stiffness = 500f), initialVelocity = velocity) { dragOffset = value }
                                }
                            }
                            detectVerticalDragGestures(
                                onDragStart = {
                                    settleJob[0]?.cancel()
                                    tracker.resetTracking()
                                    dragged = dragOffset
                                },
                                onDragEnd = {
                                    val velocity = tracker.calculateVelocity().y
                                    if (dragOffset + projectFling(velocity) > panelPx * .22f) {
                                        handoff[1] = velocity.coerceAtLeast(0f)
                                        onDismiss()
                                    } else springBack(velocity)
                                },
                                onDragCancel = { springBack(0f) },
                            ) { change, amount ->
                                change.consume()
                                dragged += amount
                                tracker.addPosition(change.uptimeMillis, Offset(0f, dragged))
                                dragOffset = if (dragged >= 0f) dragged else rubberband(dragged, panelPx * .12f)
                            }
                        },
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 38.dp, height = 5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .28f)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("播放队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${queue.size} 首 · ${formatTotalDuration(queue.sumOf { it.durationMs })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ModeChip(Icons.Rounded.Shuffle, "随机", playback.shuffleEnabled) { haptics.tick(HapticArea.PLAYBACK); onShuffle() }
                        Spacer(Modifier.width(8.dp))
                        ModeChip(
                            if (playback.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            when (playback.repeatMode) { Player.REPEAT_MODE_ONE -> "单曲"; Player.REPEAT_MODE_ALL -> "循环"; else -> "顺序" },
                            playback.repeatMode != Player.REPEAT_MODE_OFF,
                        ) { haptics.tick(HapticArea.PLAYBACK); onRepeat() }
                    }
                }
                val current = playback.currentIndex
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
                val keys = remember(queue) {
                    val seen = HashMap<String, Int>()
                    queue.map { track -> val n = seen.merge(track.id, 1, Int::plus) ?: 1; "${track.id}#$n" }
                }
                val shredderOn = playerFx(com.olbyine.music.data.PlayerFx.SHREDDER)
                val shredder = rememberShredderState()
                val navPx = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)
                val navDp = with(density) { navPx.toDp() }
                val shredFall = 22.dp
                val zone = if (shredderOn) (shredFall + 4.dp + 10.dp - navDp).coerceAtLeast(10.dp) else 0.dp
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(bottom = zone),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(queue, key = { index, _ -> keys.getOrElse(index) { "row-$index" } }) { index, track ->
                            val key = keys.getOrElse(index) { "row-$index" }
                            val openMenu: (() -> Unit)? = if (actions != null) ({ haptics.heavy(HapticArea.MENUS); menuIndex = keys.indexOf(key).takeIf { it >= 0 } }) else null
                            SwipeToRemove(
                                enabled = index != current,
                                onRemove = {
                                    val at = keys.indexOf(key)
                                    if (at >= 0) { haptics.tick(HapticArea.MENUS); onRemove(at) }
                                },
                                modifier = itemMotion(),
                            ) {
                                QueueRow(
                                    track = track,
                                    index = index,
                                    current = index == current,
                                    played = index < current,
                                    playing = playback.isPlaying,
                                    onClick = { haptics.click(HapticArea.PLAYBACK); onPlay(index) },
                                    onRemove = { haptics.tick(HapticArea.MENUS); onRemove(index) },
                                    onLongPress = if (shredderOn && index != current) null else openMenu,
                                    modifier = if (shredderOn) Modifier.shreddable(shredder, key, enabled = index != current, onLongPress = openMenu) {
                                        val at = keys.indexOf(key)
                                        if (at >= 0) onRemove(at)
                                    } else Modifier,
                                )
                            }
                        }
                    }
                    if (shredderOn) ShredderLayer(
                        shredder,
                        Modifier.matchParentSize().layout { measurable, constraints ->
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = constraints.minHeight + navPx, maxHeight = constraints.maxHeight + navPx),
                            )
                            layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
                        },
                        fall = shredFall,
                    )
                }
            }
        } }
    }
    val menuTrack = menuIndex?.let { queue.getOrNull(it) }
    if (menuTrack != null && actions != null) {
        val at = menuIndex ?: 0
        QueueActionSheet(menuTrack, at == playback.currentIndex, actions, onRemove = { onRemove(at) }, onDismiss = { menuIndex = null })
    }
}

internal class QueueItemActions(
    val playNext: (Track) -> Unit,
    val addToPlaylist: (Track) -> Unit,
    val share: (Track) -> Unit,
    val comments: ((Track) -> Unit)?,
    val artist: ((Track) -> Unit)?,
    val album: ((Track) -> Unit)?,
    val mv: ((Track) -> Unit)?,
)

@Composable
private fun QueueActionSheet(track: Track, current: Boolean, actions: QueueItemActions, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val ncm = track.id.startsWith("ncm:")
    val sc = track.id.startsWith("sc:")
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(track, Modifier.size(48.dp), 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(6.dp))
            val run: (() -> Unit) -> Unit = { action -> onDismiss(); action() }
            if (!current) GlassMenuItem("下一首播放", { run { actions.playNext(track) } }, icon = Icons.AutoMirrored.Rounded.PlaylistPlay)
            GlassMenuItem("收藏到歌单", { run { actions.addToPlaylist(track) } }, icon = Icons.AutoMirrored.Rounded.PlaylistAdd)
            if ((ncm || sc) && actions.comments != null) GlassMenuItem("评论", { run { actions.comments.invoke(track) } }, icon = Icons.Rounded.ChatBubbleOutline)
            GlassMenuItem("分享", { run { actions.share(track) } }, icon = Icons.Rounded.Share)
            actions.artist?.let { artist -> GlassMenuItem("歌手：${track.artist}", { run { artist(track) } }, icon = Icons.Rounded.Person) }
            if (!sc) actions.album?.let { album -> GlassMenuItem("专辑：${track.album}", { run { album(track) } }, icon = Icons.Rounded.Album) }
            if (ncm) actions.mv?.let { mv -> GlassMenuItem("查看 MV", { run { mv(track) } }, icon = Icons.Rounded.OndemandVideo) }
            if (!current) GlassMenuItem("从播放列表删除", { run(onRemove) }, icon = Icons.Rounded.DeleteOutline, destructive = true)
        }
    }
}

@Composable
private fun SwipeToRemove(enabled: Boolean, onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    var width by remember { mutableStateOf(1f) }
    val threshold = width * .38f
    var armed by remember { mutableStateOf(false) }
    val danger = MaterialTheme.colorScheme.error
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x
                            val target = offset.value + projectFling(velocity)
                            scope.launch {
                                if (-target > threshold) {
                                    offset.animateTo(-width, tween(160, easing = OlbyineTokens.Motion.easeOut), initialVelocity = velocity)
                                    onRemove()
                                    offset.snapTo(0f)
                                } else offset.animateTo(0f, appleSpring(response = .3f), initialVelocity = velocity)
                            }
                            armed = false
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, appleSpring(response = .3f)) }; armed = false },
                    ) { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val next = (offset.value + amount).coerceAtMost(0f)
                        scope.launch { offset.snapTo(if (next > 0f) rubberband(next, width * .1f) else next) }
                        val nowArmed = -next > threshold
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptics.tick(HapticArea.GESTURES)
                        }
                    }
                },
            ),
    ) {
        val reveal = (-offset.value / threshold.coerceAtLeast(1f)).coerceIn(0f, 1.6f)
        if (offset.value < -0.5f) {
            Box(
                Modifier.matchParentSize().padding(vertical = 2.dp).clip(RoundedCornerShape(20.dp))
                    .background(danger.copy(alpha = (.35f + .65f * reveal.coerceAtMost(1f)))),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(Modifier.padding(end = 22.dp).graphicsLayer { alpha = reveal.coerceAtMost(1f); val s = .8f + .2f * reveal.coerceAtMost(1f); scaleX = s; scaleY = s }, verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onError)
                    Spacer(Modifier.width(6.dp))
                    Text("删除", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onError)
                }
            }
        }
        Box(Modifier.graphicsLayer { translationX = offset.value }) { content() }
    }
}

@Composable
private fun ModeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else ink.copy(alpha = .08f))
            .pressable(label = label, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyledIcon(icon, null, Modifier.size(17.dp), tint = if (active) MaterialTheme.colorScheme.primary else ink.copy(alpha = .75f))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.primary else ink.copy(alpha = .75f))
    }
}

@Composable
private fun QueueRow(
    track: Track,
    index: Int,
    current: Boolean,
    played: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (current) ink.copy(alpha = .10f) else Color.Transparent)
            .combinedClickable(onLongClick = onLongPress, onClick = onClick)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
            .graphicsLayer { alpha = if (played) .5f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (current) 54.dp else 46.dp)) {
            TrackArtwork(track, Modifier.fillMaxSize(), 12.dp)
            if (current) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .38f)), contentAlignment = Alignment.Center) {
                    EqualizerBars(playing, Color.White, Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (current) {
                Text("正在播放", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                color = ink,
            )
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!current) {
            GhostIconButton(Icons.Rounded.Close, "从队列移除", onRemove, size = 40.dp, iconSize = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.width(14.dp))
        }
    }
}
