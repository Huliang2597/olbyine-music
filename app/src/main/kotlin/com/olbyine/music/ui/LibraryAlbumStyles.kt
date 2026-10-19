package com.olbyine.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.Album
import com.olbyine.music.data.HapticArea
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@OptIn(ExperimentalSharedTransitionApi::class)
internal class SharedAlbumScope(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

internal val LocalSharedAlbum = compositionLocalOf<SharedAlbumScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedAlbumCover(key: String, shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)): Modifier {
    val scope = LocalSharedAlbum.current ?: return this
    return with(scope.transition) {
        this@sharedAlbumCover.sharedElement(
            rememberSharedContentState("album-cover:$key"),
            scope.visibility,
            boundsTransform = { _, _ -> spring(dampingRatio = 1f, stiffness = 380f) },
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

@Composable
internal fun CoverFlow(
    albums: List<Album>,
    currentTrackId: String?,
    index: Int,
    onIndex: (Int) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlay: (Album) -> Unit,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
) {
    if (albums.isEmpty()) {
        EmptyState(
            Icons.Rounded.Album,
            "还没有专辑",
            modifier = Modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding),
            message = "导入歌曲后会按专辑自动归类",
        )
        return
    }
    val last = albums.lastIndex
    val position = remember { androidx.compose.runtime.mutableFloatStateOf(index.coerceIn(0, last).toFloat()) }
    val settleJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val currentOnIndex by rememberUpdatedState(onIndex)
    LaunchedEffect(last) { if (position.floatValue > last) position.floatValue = last.toFloat() }
    val centered by remember(last) { derivedStateOf { position.floatValue.roundToInt().coerceIn(0, last) } }
    LaunchedEffect(last) {
        snapshotFlow { centered }.drop(1).collect {
            haptics.tick(HapticArea.SCRUB)
            currentOnIndex(it)
        }
    }
    val animateTo: (Float, Float) -> Unit = { target, velocity ->
        settleJob[0]?.cancel()
        settleJob[0] = scope.launch {
            androidx.compose.animation.core.animate(
                position.floatValue, target, velocity,
                spring(dampingRatio = 1f, stiffness = 260f),
            ) { value, _ -> position.floatValue = value }
        }
    }
    val settle: (Int) -> Unit = { target -> animateTo(target.coerceIn(0, last).toFloat(), 0f) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(top = topContentPadding, bottom = bottomContentPadding)) {
        val landscape = maxWidth > maxHeight
        val cover = if (landscape) minOf(maxHeight * .56f, maxWidth * .34f) else minOf(maxWidth * .62f, maxHeight * .40f)
        val coverPx = with(density) { cover.toPx() }
        val dragged = remember { floatArrayOf(0f) }
        val stage: @Composable () -> Unit = {
            DeckStage(
                albums = albums,
                position = { position.floatValue },
                centered = centered,
                cover = cover,
                coverPx = coverPx,
                onDrag = { dx ->
                    dragged[0] -= dx / (coverPx * .9f)
                    position.floatValue = rubberbandClamp(dragged[0], 0f, last.toFloat(), dimension = 1f)
                },
                onDragStart = {
                    settleJob[0]?.cancel()
                    dragged[0] = position.floatValue.coerceIn(0f, last.toFloat())
                },
                onRelease = { velocity ->
                    val perCover = velocity / (coverPx * .9f)
                    val target = (position.floatValue - perCover * .3f).roundToInt().coerceIn(0, last)
                    val towards = target - position.floatValue
                    val v = (-perCover).let { if (it * towards <= 0f) 0f else it }
                    animateTo(target.toFloat(), v)
                },
                onTap = { i ->
                    if (i == centered) {
                        haptics.click(HapticArea.MENUS)
                        onAlbum(albums[i])
                    } else settle(i)
                },
            )
        }
        val info: @Composable () -> Unit = {
            DeckInfo(albums[centered], currentTrackId, onPlay = { onPlay(albums[centered]) }, onOpen = { onAlbum(albums[centered]) })
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DeckPane(cover) { stage() } }
                Column(Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    info()
                    if (last > 0) {
                        Spacer(Modifier.height(18.dp))
                        CoverFlowScrubber(position, last, onStart = { settleJob[0]?.cancel() }, onSettle = settle)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(.4f))
                DeckPane(cover) {
                    stage()
                    Spacer(Modifier.height(14.dp))
                    info()
                }
                Spacer(Modifier.weight(1f))
                if (last > 0) CoverFlowScrubber(position, last, onStart = { settleJob[0]?.cancel() }, onSettle = settle)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private const val DECK_STEP = .085f
private const val DECK_DEPTH = 4

@Composable
private fun DeckPane(cover: Dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val paneTop = cover * .62f
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.width(cover * 1.42f), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = paneTop)
                .clip(shape)
                .background(
                    when {
                        LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL -> colors.surfaceContainer
                        dark -> Color.White.copy(alpha = .07f)
                        else -> Color.White.copy(alpha = .34f)
                    },
                )
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .10f else .30f), Color.Transparent)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .28f else .8f), Color.White.copy(alpha = .03f))), shape),
        )
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun DeckStage(
    albums: List<Album>,
    position: () -> Float,
    centered: Int,
    cover: Dp,
    coverPx: Float,
    onDrag: (Float) -> Unit,
    onDragStart: () -> Unit,
    onRelease: (Float) -> Unit,
    onTap: (Int) -> Unit,
) {
    val last = albums.lastIndex
    Box(
        Modifier
            .width(cover * 1.42f)
            .height(cover * (1f + DECK_STEP * (DECK_DEPTH - 1)) + 8.dp)
            .pointerInput(last, coverPx) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        onDragStart()
                    },
                    onDragEnd = { onRelease(tracker.calculateVelocity().x) },
                    onDragCancel = { onRelease(0f) },
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    onDrag(dx)
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val first = (centered - 2).coerceAtLeast(0)
        val lastShown = (centered + DECK_DEPTH + 1).coerceAtMost(last)
        for (i in first..lastShown) {
            val album = albums[i]
            key(album.key) {
                DeckCard(album, i, position, cover, coverPx, onClick = { onTap(i) })
            }
        }
    }
}

@Composable
private fun DeckCard(
    album: Album,
    index: Int,
    position: () -> Float,
    cover: Dp,
    coverPx: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val maxBlurPx = with(density) { 20.dp.toPx() }
    val shadowPx = with(density) { 18.dp.toPx() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(cover)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val d = index - position()
                    placeable.place(0, 0, zIndex = if (d < 0f) 10f else -d)
                }
            }
            .graphicsLayer {
                val d = index - position()
                this.shape = shape
                clip = false
                if (d < 0f) {
                    val out = (-d).coerceIn(0f, 1f)
                    translationX = -out * coverPx * 1.15f
                    translationY = out * coverPx * .04f
                    rotationZ = -out * 9f
                    alpha = (1f - out) * (1f - out * .25f)
                    shadowElevation = shadowPx * (1f - out)
                    renderEffect = null
                } else {
                    val depth = d.coerceAtMost(DECK_DEPTH.toFloat())
                    translationY = depth * coverPx * DECK_STEP
                    val scale = 1f - depth * .075f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(.5f, 1f)
                    alpha = (1f - (d - (DECK_DEPTH - 1)).coerceIn(0f, 1f)) * (1f - depth * .08f)
                    shadowElevation = shadowPx * (1f - depth / DECK_DEPTH).coerceIn(.2f, 1f)
                    val blur = maxBlurPx * ((depth - .35f) / 1.65f).coerceIn(0f, 1f)
                    renderEffect = if (blur >= 1f && android.os.Build.VERSION.SDK_INT >= 31) {
                        val stepped = (kotlin.math.round(blur / 2f) * 2f).coerceAtLeast(2f)
                        androidx.compose.ui.graphics.BlurEffect(stepped, stepped, androidx.compose.ui.graphics.TileMode.Decal)
                    } else null
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        TrackArtwork(album.cover, Modifier.fillMaxSize().sharedAlbumCover(album.key), 14.dp, decodeEdge = 512)
    }
}
@Composable
private fun DeckInfo(album: Album, currentTrackId: String?, onPlay: () -> Unit, onOpen: () -> Unit) {
    AnimatedContent(
        targetState = album,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        contentKey = { it.key },
        label = "deck-info",
    ) { shown ->
        val playing = currentTrackId != null && shown.tracks.any { it.id == currentTrackId }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                shown.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                listOfNotNull(shown.artist, shown.year.takeIf { it > 0 }?.toString(), "${shown.tracks.size} 首")
                    .joinToString(" · ") + if (playing) " · 正在播放" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("播放", onPlay, icon = Icons.Rounded.PlayArrow)
        PillButton("打开专辑", onOpen, icon = Icons.AutoMirrored.Rounded.QueueMusic, style = PillStyle.TONAL)
    }
}
@Composable
private fun CoverFlowScrubber(
    position: androidx.compose.runtime.MutableFloatState,
    last: Int,
    onStart: () -> Unit,
    onSettle: (Int) -> Unit,
) {
    OlbyineSlider(
        value = position.floatValue.coerceIn(0f, last.toFloat()),
        onValueChange = { value -> onStart(); position.floatValue = value },
        onValueChangeFinished = { onSettle(position.floatValue.roundToInt()) },
        valueRange = 0f..last.toFloat(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
        quiet = true,
    )
}
