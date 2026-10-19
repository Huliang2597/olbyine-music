@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.olbyine.music.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.media3.common.Player
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.olbyine.music.AppDestination
import com.olbyine.music.MainUiState
import com.olbyine.music.MainViewModel
import com.olbyine.music.data.LyricsDocument
import com.olbyine.music.data.Track
import com.olbyine.music.playback.PlaybackState
import com.olbyine.music.playback.asPlaybackState
import com.olbyine.music.playback.asTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OlbyineApp(state: MainUiState, lyrics: LyricsDocument, viewModel: MainViewModel) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val haptics = remember(context, hostView) { Haptics(context, hostView) }
    haptics.enabled = state.settings.hapticsEnabled
    haptics.areas = state.settings.hapticAreas
    val reduceMotion = rememberSystemReduceMotion()
    CompositionLocalProvider(
        LocalStyleSpec provides remember(state.settings.uiStyle) { styleSpecOf(state.settings.uiStyle) },
        LocalHaptics provides haptics,
        LocalGlassTuning provides state.settings.glassTuning,
        LocalReduceMotion provides reduceMotion,
        LocalPlayerFx provides state.settings.playerFx,
    ) {
        TrackGlassLight(state.settings.glassTuning.gyro && !reduceMotion && state.settings.uiStyle != com.olbyine.music.data.UiStyle.MATERIAL)
        HapticRipple { OlbyineAppContent(state, lyrics, viewModel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OlbyineAppContent(state: MainUiState, lyrics: LyricsDocument, viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dockSource = rememberBackdropSource()
    var lyricTargetId by remember { mutableStateOf<String?>(null) }
    var afterNotificationPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        afterNotificationPermission?.invoke()
        afterNotificationPermission = null
    }
    val runPlaybackAction: ((() -> Unit) -> Unit) = remember(context) {
        { action ->
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                afterNotificationPermission = action
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                action()
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        viewModel::importAudio,
    )
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
        viewModel::addScanFolder,
    )
    val lyricPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.importLyrics(uris, lyricTargetId)
        lyricTargetId = null
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCustomFont)
    }

    val openAudioPicker = { audioPicker.launch(AUDIO_MIME_TYPES) }
    val openLyricsPicker: (String?) -> Unit = { trackId ->
        lyricTargetId = trackId
        lyricPicker.launch(LYRIC_MIME_TYPES)
    }
    val playInOrder: (List<Track>) -> Unit = { tracks ->
        if (tracks.isNotEmpty()) runPlaybackAction {
            if (state.playback.shuffleEnabled) viewModel.toggleShuffle()
            viewModel.playTracks(tracks, 0)
        }
    }
    val shufflePlay: (List<Track>) -> Unit = { tracks ->
        if (tracks.isNotEmpty()) runPlaybackAction {
            if (!state.playback.shuffleEnabled) viewModel.toggleShuffle()
            viewModel.playTracks(tracks, tracks.indices.random())
        }
    }

    LaunchedEffect(state.message) {
        val value = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(value)
        viewModel.consumeMessage()
    }
    LaunchedEffect(state.playback.error) {
        val error = state.playback.error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = error,
            actionLabel = "重试",
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.retryPlayback()
    }

    val playerOpen = state.destination == AppDestination.NOW_PLAYING
    val sheet = rememberSpringValue(if (playerOpen) 1f else 0f)
    var infoVisible by rememberSaveable { mutableStateOf(false) }
    val pageTarget = when {
        state.lyricsVisible -> 1f
        infoVisible -> -1f
        else -> 0f
    }
    val pager = rememberSpringValue(pageTarget)
    val playerBackdrop = rememberBackdropSource()
    val metadataMatch by viewModel.metadataMatch.collectAsState()
    val metadataRevision by viewModel.metadataRevision.collectAsState()
    val externalLyrics by viewModel.externalLyrics.collectAsState()
    val playlistImport by viewModel.playlistImport.collectAsState()
    val basePopups = rememberGlassPopupHostState()
    val motionArtwork by viewModel.motionArtwork.collectAsState()
    val appleSettings by viewModel.appleMusicSettings.collectAsState()
    val usbDriver by viewModel.usbDriverState.collectAsState()
    val neteaseState by viewModel.neteaseState.collectAsState()
    val collectionResume by viewModel.collectionResume.collectAsState()
    val activeCollection by viewModel.activeCollection.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val albumRequest by viewModel.albumRequest.collectAsState()
    var playlistPickerFor by remember { mutableStateOf<Track?>(null) }
    val trackActions = remember(viewModel) {
        TrackActions(
            addToPlaylist = { playlistPickerFor = it },
            playNext = { viewModel.playNext(listOf(it)) },
            enqueue = { viewModel.enqueue(listOf(it)) },
        )
    }
    var queueOpen by remember { mutableStateOf(false) }
    var webDavOpen by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var scCommentsFor by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val openScComments: (Long, String) -> Unit = { id, title -> scCommentsFor = id to title }
    var streamingProvider by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("netease") }
    var scSearch by remember { mutableStateOf<String?>(null) }
    val touchOrigin = AppTouchOrigin
    val ncmStack = rememberPageStack<NcmRoute>()
    val appScope = androidx.compose.runtime.rememberCoroutineScope()
    val pushNcm: (NcmRoute) -> Unit = { route ->
        commentsFor = null
        if (sheet.value < 0.5f) ncmStack.pushFromTouch(route) else ncmStack.push(route)
        viewModel.collapseNowPlaying()
    }
    val openNeteaseProfile: (Long) -> Unit = { userId -> pushNcm(NcmRoute.Profile(userId)) }
    val searchSoundCloud: (String) -> Unit = { query ->
        streamingProvider = "soundcloud"
        scSearch = query
        viewModel.navigateTo(AppDestination.ONLINE)
    }
    val openComments: (Long, String) -> Unit = { id, title -> commentsFor = id to title }
    var shareFor by remember { mutableStateOf<Pair<Track, String?>?>(null) }
    var wikiHistoryOpen by remember { mutableStateOf<Long?>(null) }
    var folderSheet by remember { mutableStateOf(false) }
    val scanFolders by viewModel.scanFolders.collectAsState()
    var listenOpen by remember { mutableStateOf(false) }
    val listenTogether by viewModel.listenTogether.collectAsState()
    var scOpenRequest by remember { mutableStateOf<Any?>(null) }
    val openChat: (com.olbyine.music.data.NeteaseThread) -> Unit = { thread -> pushNcm(NcmRoute.Chat(thread)) }
    val openArtist: (Long?, String) -> Unit = { id, name -> pushNcm(NcmRoute.Artist(id, name)) }
    val openNeteaseLink: (com.olbyine.music.data.NeteaseTarget) -> Unit = { target ->
        when (target) {
            is com.olbyine.music.data.NeteaseTarget.Song -> viewModel.openNeteaseTarget(target)
            is com.olbyine.music.data.NeteaseTarget.Playlist ->
                pushNcm(NcmRoute.Collection(com.olbyine.music.data.NeteasePlaylistSummary(target.id, "歌单", null, 0, "", liked = false, own = false)))
            is com.olbyine.music.data.NeteaseTarget.Album -> pushNcm(
                NcmRoute.Collection(com.olbyine.music.data.NeteasePlaylistSummary(target.id, "专辑", null, 0, "", liked = false, own = false, kind = com.olbyine.music.data.NeteaseCollectionKind.ALBUM)),
            )
            is com.olbyine.music.data.NeteaseTarget.Artist -> openArtist(target.id, target.name)
            is com.olbyine.music.data.NeteaseTarget.User -> openNeteaseProfile(target.id)
            is com.olbyine.music.data.NeteaseTarget.Mv -> pushNcm(NcmRoute.Mv(target.id))
            is com.olbyine.music.data.NeteaseTarget.ListenTogether -> {
                viewModel.openNeteaseTarget(target)
                listenOpen = true
            }
            is com.olbyine.music.data.NeteaseTarget.Web -> {
                val host = runCatching { android.net.Uri.parse(target.url).host.orEmpty() }.getOrDefault("")
                if (host.endsWith("163.com") || host.endsWith("126.net")) pushNcm(NcmRoute.Web(target.url)) else openExternal(context, target.url)
            }
        }
    }
    val libraryNav = rememberLibraryNav(state.libraryFilter == com.olbyine.music.LibraryFilter.FAVORITES)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val appLayout = AppLayout.of(state.settings.layoutMode, configuration.screenWidthDp, configuration.screenHeightDp)
    val openLibrarySection: (LibrarySection) -> Unit = { item ->
        libraryNav.section = item
        libraryNav.albumKey = null
        viewModel.setLibraryFolderId(null)
        viewModel.setActivePlaylistId(null)
        viewModel.setLibraryFilter(if (item == LibrarySection.FAVORITES) com.olbyine.music.LibraryFilter.FAVORITES else com.olbyine.music.LibraryFilter.ALL)
        viewModel.navigateTo(AppDestination.LIBRARY)
    }
    var removeConfirmFor by remember { mutableStateOf<Track?>(null) }
    val haptics = rememberHaptics()
    val playerPopups = rememberGlassPopupHostState()

    val listening = externalLyrics.visible
    val external = externalLyrics.nowPlaying
    val externalArtwork = externalLyrics.artworkPath
    val externalTrack = remember(external?.key, externalArtwork, external?.durationMs, external?.album) {
        external?.asTrack()?.copy(coverPath = externalArtwork)
    }
    val playerState = if (listening) {
        state.copy(
            currentTrack = externalTrack,
            playback = external?.asPlaybackState() ?: PlaybackState(),
            queue = emptyList(),
        )
    } else {
        val current = state.currentTrack
        if (current != null && current.isOnline) {
            val liked = current.id.removePrefix("ncm:").toLongOrNull()?.let { it in neteaseState.liked } == true
            state.copy(currentTrack = current.copy(favorite = liked))
        } else state
    }
    val playerLyrics = if (listening) externalLyrics.document else lyrics
    val listeningInfo = if (listening) {
        ListeningInfo(external?.appName, externalLyrics.access, externalLyrics.searching, externalLyrics.message)
    } else {
        null
    }
    val togglePlay: () -> Unit = {
        haptics.click(com.olbyine.music.data.HapticArea.PLAYBACK)
        if (listening) viewModel.externalTogglePlay() else runPlaybackAction(viewModel::togglePlayPause)
    }
    val skipNext: () -> Unit = {
        haptics.click(com.olbyine.music.data.HapticArea.PLAYBACK)
        if (listening) viewModel.externalNext() else viewModel.skipNext()
    }
    val skipPrevious: () -> Unit = {
        haptics.click(com.olbyine.music.data.HapticArea.PLAYBACK)
        if (listening) viewModel.externalPrevious() else viewModel.skipPrevious()
    }
    val seekTo: (Long) -> Unit = { position -> if (listening) viewModel.externalSeek(position) else viewModel.seekTo(position) }
    var closingListening by remember { mutableStateOf(false) }
    val setListening: (Boolean) -> Unit = { on ->
        when {
            on -> viewModel.openExternalLyrics()
            state.currentTrack == null -> {
                closingListening = true
                viewModel.collapseNowPlaying()
            }
            else -> viewModel.closeExternalLyrics()
        }
    }
    val openListening: () -> Unit = {
        viewModel.openExternalLyrics()
        viewModel.navigateTo(AppDestination.NOW_PLAYING)
    }
    val openExternalApp: () -> Unit = {
        external?.packageName?.let { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
    val grantAccess: () -> Unit = {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshExternalAccess()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importCustomBackground)
    }
    val shared = remember { SharedArtworkState() }
    val coverTrack = playerState.currentTrack
    val playerCoverPath by androidx.compose.runtime.produceState(coverTrack?.coverPath, coverTrack?.id, coverTrack?.coverPath, coverTrack?.artworkUrl) {
        value = coverTrack?.coverPath ?: remoteArtworkPath(coverTrack?.artworkUrl)
    }
    val density = LocalDensity.current
    val flingPx = with(density) { 700.dp.toPx() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var miniBounds by remember { mutableStateOf<Rect?>(null) }
    var launchBounds by remember { mutableStateOf<Rect?>(null) }
    var dockHeightPx by remember { mutableIntStateOf(0) }
    var baseDestination by rememberSaveable {
        mutableStateOf(if (state.destination == AppDestination.NOW_PLAYING) AppDestination.LIBRARY else state.destination)
    }
    val miniHidden = baseDestination == AppDestination.SETTINGS || playerState.currentTrack == null
    val collapsedBounds: () -> Rect = {
        launchBounds ?: if (miniHidden && rootSize.height > 0) with(density) {
            val width = rootSize.width.toFloat()
            val top = rootSize.height.toFloat() + 12.dp.toPx()
            Rect(12.dp.toPx(), top, width - 12.dp.toPx(), top + 66.dp.toPx())
        } else miniBounds ?: with(density) {
            val width = rootSize.width.toFloat()
            val height = rootSize.height.toFloat().coerceAtLeast(1f)
            val bottom = height - dockHeightPx.coerceAtLeast(96.dp.roundToPx()) + 84.dp.toPx()
            Rect(12.dp.toPx(), bottom - 66.dp.toPx(), width - 12.dp.toPx(), bottom)
        }
    }
    LaunchedEffect(state.destination) {
        if (state.destination != AppDestination.NOW_PLAYING) baseDestination = state.destination
    }
    LaunchedEffect(playerOpen) {
        if (playerOpen && launchBounds == null && sheet.value < .01f && !sheet.dragging) {
            val pressed = AppTouchOrigin.bounds?.takeIf {
                android.os.SystemClock.uptimeMillis() - AppTouchOrigin.boundsAt < 3_000 && it.width > 4f && it.height > 4f
            }
            val mini = miniBounds
            if (pressed != null && (mini == null || !pressed.overlaps(mini)) && pressed.top < rootSize.height - dockHeightPx) {
                launchBounds = pressed
            }
        }
        val target = if (playerOpen) 1f else 0f
        if (!sheet.dragging && sheet.target != target) sheet.settle(target)
    }
    LaunchedEffect(playerState.currentTrack?.id) { if (playerState.currentTrack == null) infoVisible = false }
    LaunchedEffect(pageTarget) {
        val target = pageTarget
        if (pager.dragging || pager.target == target) return@LaunchedEffect
        if (target == 0f && sheet.target == 0f) {
            snapshotFlow { sheet.value <= 0.001f || sheet.target == 1f }.first { it }
            if (sheet.target == 1f) pager.settle(0f) else pager.snap(0f)
        } else {
            pager.settle(target)
        }
    }
    val sheetComposed by remember { derivedStateOf { sheet.value > 0.001f || sheet.target > 0f || sheet.dragging } }
    GlassMotion.Drive { sheet.value; pager.value }
    val sheetSettledOpen by remember { derivedStateOf { sheet.value > 0.98f && !sheet.dragging } }
    val lyricsShown by remember { derivedStateOf { pager.value > 0.01f || pager.dragging } }
    val playerPageShown by remember { derivedStateOf { sheet.value > 0.3f && kotlin.math.abs(pager.value) < 0.99f } }
    val infoShown by remember { derivedStateOf { pager.value < -0.01f || pager.dragging } }
    LaunchedEffect(sheetSettledOpen) { if (sheetSettledOpen) launchBounds = null }
    LaunchedEffect(launchBounds) {
        if (launchBounds != null) {
            delay(2_500)
            if (sheet.target < 1f) launchBounds = null
        }
    }
    var lyricsWarm by remember { mutableStateOf(false) }
    LaunchedEffect(sheetSettledOpen, playerState.currentTrack != null) {
        if (sheetSettledOpen && playerState.currentTrack != null && !lyricsWarm) {
            delay(450)
            lyricsWarm = true
        }
    }
    LaunchedEffect(sheetComposed) { if (!sheetComposed) lyricsWarm = false }
    LaunchedEffect(closingListening) {
        if (!closingListening) return@LaunchedEffect
        snapshotFlow { sheet.value <= 0.001f || sheet.target == 1f }.first { it }
        if (sheet.target != 1f) viewModel.closeExternalLyrics()
        closingListening = false
    }

    val view = LocalView.current
    val baseDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    LaunchedEffect(baseDark, view) {
        snapshotFlow { sheet.value > 0.5f }.collect { overPlayer ->
            setSystemBarsLight(view, light = !baseDark && !overPlayer)
        }
    }
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(activity) {
        snapshotFlow {
            when {
                sheet.value < 0.5f -> RefreshMode.SYSTEM
                pager.value > 0.5f || appLayout.splitPlayer -> RefreshMode.LYRICS
                else -> RefreshMode.PLAYER
            }
        }.collect { mode -> activity?.applyRefreshMode(mode) }
    }

    androidx.compose.runtime.key(playerOpen || state.lyricsVisible) {
        BackHandler(enabled = playerOpen || state.lyricsVisible) {
            when {
                state.lyricsVisible -> viewModel.showLyrics(false)
                infoVisible -> infoVisible = false
                else -> viewModel.collapseNowPlaying()
            }
        }
    }
    LaunchedEffect(appLayout.splitPlayer) {
        if (appLayout.splitPlayer && state.lyricsVisible) viewModel.showLyrics(false)
    }
    LaunchedEffect(playerOpen) {
        if (!playerOpen) {
            infoVisible = false
            queueOpen = false
        }
    }

    val miniDrag: (Float) -> Unit = { deltaY ->
        sheet.drag((sheet.value - deltaY / collapsedBounds().top.coerceAtLeast(1f)).coerceIn(0f, 1f))
    }
    val miniDragStop: (Float) -> Unit = { velocityY ->
        val velocity = -velocityY / collapsedBounds().top.coerceAtLeast(1f)
        if (sheet.value > 0.22f || velocityY < -flingPx) {
            sheet.settle(1f, velocity)
            viewModel.navigateTo(AppDestination.NOW_PLAYING)
        } else {
            sheet.settle(0f, velocity)
        }
    }
    val miniClock = rememberLyricClock(playerState.playback, state.settings.lyricOffsetMs, active = !sheetSettledOpen)
    val miniLyric = rememberMiniLyric(playerLyrics, playerState.currentTrack?.id, miniClock)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .trackTouches(touchOrigin)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (sheet.value >= 0.999f) 0f else 1f
                }
                .drawWithContent {
                    drawContent()
                    val p = sheet.value.coerceIn(0f, 1f)
                    if (p > 0f) drawRect(Color.Black.copy(alpha = 0.38f * p))
                },
        ) {
            CompositionLocalProvider(LocalTrackActions provides trackActions, LocalGlassPopupHost provides basePopups, LocalOpenWebDav provides { webDavOpen = true }, LocalAppLayout provides appLayout, LocalLyricFeed provides LyricFeed(playerLyrics, playerLyrics.trackId, miniClock, state.settings.lyricTranslation), LocalNeteaseComments provides openComments, LocalScComments provides openScComments, LocalSearchSoundCloud provides searchSoundCloud, LocalNeteaseProfile provides openNeteaseProfile, LocalNeteaseChat provides openChat, LocalNeteaseLinks provides openNeteaseLink, LocalOpenArtist provides openArtist, LocalPlayerLaunch provides { bounds -> if (sheet.value < 0.001f && !sheet.dragging) launchBounds = bounds }) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { CompositionLocalProvider(LocalOnGlass provides true) {
                    BottomDock(
                        state = playerState,
                        selected = baseDestination,
                        source = dockSource,
                        lyric = miniLyric,
                        lyricClock = miniClock,
                        modifier = Modifier.onSizeChanged { dockHeightPx = it.height },
                        onMiniBounds = { bounds -> if (sheet.value < 0.001f && !sheet.dragging) miniBounds = bounds },
                        onOpenPlayer = { viewModel.navigateTo(AppDestination.NOW_PLAYING) },
                        onToggle = togglePlay,
                        onNext = skipNext,
                        onDragStart = { sheet.drag(sheet.value) },
                        onDrag = miniDrag,
                        onDragStop = miniDragStop,
                        onDragCancel = { sheet.settle(if (playerOpen) 1f else 0f) },
                        onSelect = viewModel::navigateTo,
                        tablet = appLayout.tablet,
                        startInset = appLayout.sidebarWidth,
                    )
                } },
            ) { scaffoldPadding ->
                val customBackground = state.settings.customBackgroundPath
                val artworkBackdrop = playerCoverPath?.takeIf { state.settings.artworkBackdrop && customBackground == null }
                val pageBackdrop = customBackground != null || artworkBackdrop != null
                Box(Modifier.fillMaxSize().backdropSource(dockSource)) {
                when {
                    customBackground != null -> CustomBackground(
                        path = customBackground,
                        blur = state.settings.customBackgroundBlur,
                        dim = state.settings.customBackgroundDim,
                        scrim = MaterialTheme.colorScheme.background,
                    )
                    else -> {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        androidx.compose.animation.Crossfade(
                            targetState = artworkBackdrop,
                            animationSpec = tween(700),
                            label = "artwork-backdrop",
                        ) { cover ->
                            if (cover != null) {
                                CustomBackground(
                                    path = cover,
                                    blur = 1f,
                                    dim = if (baseDark) .58f else .52f,
                                    scrim = MaterialTheme.colorScheme.background,
                                )
                            }
                        }
                    }
                }
                val tabMaterial = LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL
                val tabReduce = LocalReduceMotion.current
                AnimatedContent(
                    targetState = baseDestination,
                    modifier = Modifier.fillMaxSize().padding(start = appLayout.sidebarWidth),
                    transitionSpec = {
                        val from = DockTabs.indexOfFirst { it.destination == initialState }
                        val to = DockTabs.indexOfFirst { it.destination == targetState }
                        tabSwitch(if (to >= from) 1 else -1, tabMaterial, tabReduce)
                    },
                    label = "base-page",
                ) { destination ->
                    CompositionLocalProvider(LocalPageBackdrop provides pageBackdrop) {
                    when (destination) {
                        AppDestination.SETTINGS -> SettingsScreen(
                            state = state,
                            viewModel = viewModel,
                            bottomContentPadding = scaffoldPadding.calculateBottomPadding(),
                            onImportFont = { fontPicker.launch(arrayOf("*/*")) },
                            onPickBackground = {
                                backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onExternalLyrics = openListening,
                            onNeteaseLogin = viewModel::openNeteaseLogin,
                        )
                        AppDestination.ONLINE -> CompositionLocalProvider(
                            LocalStreamingSwitch provides {
                                var providerMenu by remember { mutableStateOf(false) }
                                Box {
                                    PillButton(
                                        when (streamingProvider) { "soundcloud" -> "SoundCloud"; "navidrome" -> "Navidrome"; else -> "网易云" },
                                        { providerMenu = true },
                                        icon = Icons.Rounded.SwapHoriz,
                                        style = PillStyle.TONAL,
                                        height = 36.dp,
                                    )
                                    OlbyineDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                                        listOf("netease" to "网易云音乐", "soundcloud" to "SoundCloud", "navidrome" to "Navidrome").forEach { (key, label) ->
                                            GlassMenuItem(label, {
                                                providerMenu = false
                                                if (key != streamingProvider) {
                                                    haptics.tick(com.olbyine.music.data.HapticArea.MENUS)
                                                    streamingProvider = key
                                                }
                                            }, selected = key == streamingProvider)
                                        }
                                    }
                                }
                            },
                            LocalSearchSoundCloud provides searchSoundCloud,
                            LocalNcmPush provides pushNcm,
                            LocalCollectionResume provides { key -> collectionResume[key] },
                            LocalResumeCollection provides { key, songs -> runPlaybackAction { viewModel.resumeNeteaseCollection(key, songs) } },
                            LocalPlayNeteaseCollection provides { key, songs, index, shuffle, startMs ->
                                runPlaybackAction { viewModel.playNeteaseSongs(songs, index, shuffle, key, startMs) }
                            },
                            LocalActiveCollection provides activeCollection,
                        ) {
                            AnimatedContent(
                                targetState = streamingProvider,
                                transitionSpec = {
                                    val order = listOf("netease", "soundcloud", "navidrome")
                                    tabSwitch(if (order.indexOf(targetState) > order.indexOf(initialState)) 1 else -1, tabMaterial, tabReduce)
                                },
                                label = "streaming-provider",
                            ) { provider ->
                            if (provider == "navidrome") {
                                NavidromeScreen(
                                    client = viewModel.navidrome,
                                    bottomPadding = scaffoldPadding.calculateBottomPadding(),
                                    currentTrackId = state.currentTrack?.id,
                                    isPlaying = state.playback.isPlaying,
                                    onPlay = { songs, index, shuffle -> runPlaybackAction { viewModel.playNavidrome(songs, index, shuffle) } },
                                )
                            } else if (provider == "soundcloud") {
                                SoundCloudScreen(
                                    client = viewModel.soundCloud,
                                    bottomPadding = scaffoldPadding.calculateBottomPadding(),
                                    currentTrackId = state.currentTrack?.id,
                                    isPlaying = state.playback.isPlaying,
                                    searchRequest = scSearch,
                                    onSearchHandled = { scSearch = null },
                                    openUser = scOpenRequest as? com.olbyine.music.data.ScUser,
                                    onOpenUserHandled = { scOpenRequest = null },
                                    onPlay = { tracks, index, shuffle -> runPlaybackAction { viewModel.playSoundCloud(tracks, index, shuffle) } },
                                )
                            } else {
                                NeteaseScreen(
                            state = neteaseState,
                            onlinePlayback = state.settings.neteaseOnline,
                            bottomContentPadding = scaffoldPadding.calculateBottomPadding(),
                            currentTrackId = state.currentTrack?.id,
                            isPlaying = state.playback.isPlaying,
                            onLogin = viewModel::openNeteaseLogin,
                            onRefresh = viewModel::loadNeteasePlaylists,
                            onOpenPlaylist = viewModel::openNeteasePlaylist,
                            onClosePlaylist = viewModel::closeNeteasePlaylist,
                            onPlay = { songs, index, shuffle -> runPlaybackAction { viewModel.playNeteaseSongs(songs, index, shuffle) } },
                            onLike = viewModel::toggleNeteaseLike,
                            onSaveLocal = viewModel::saveNeteasePlaylistLocally,
                            onOnlinePlayback = viewModel::setNeteaseOnline,
                            onSignOut = viewModel::signOutNetease,
                            client = viewModel.neteaseClient,
                        )
                            }
                            }
                        }
                        else -> LibraryScreen(
                            state = state,
                            albums = albums,
                            importProgress = importProgress,
                            bottomContentPadding = scaffoldPadding.calculateBottomPadding(),
                            onDeleteTracks = viewModel::deleteTracks,
                            onAddTracksToPlaylist = viewModel::addTracksToPlaylist,
                            onSearch = viewModel::setSearch,
                            onFilter = viewModel::setLibraryFilter,
                            onPlayTracks = { tracks, index -> runPlaybackAction { viewModel.playTracks(tracks, index) } },
                            onPlayInOrder = playInOrder,
                            onShufflePlay = shufflePlay,
                            onFavorite = viewModel::toggleFavorite,
                            onDelete = viewModel::deleteTrack,
                            onEdit = viewModel::updateMetadata,
                            onImportAudio = openAudioPicker,
                            onImportFolder = { viewModel.refreshScanFolders(); folderSheet = true },
                            onImportLyrics = openLyricsPicker,
                            onSearchLyrics = viewModel::searchOnlineLyrics,
                            onViewMode = viewModel::setLibraryViewMode,
                            onSortMode = viewModel::setLibrarySortMode,
                            onFolder = viewModel::setLibraryFolderId,
                            onPlaylist = viewModel::setActivePlaylistId,
                            onCreatePlaylist = viewModel::createPlaylist,
                            onRenamePlaylist = viewModel::renamePlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onAddToPlaylist = viewModel::addToPlaylist,
                            onRemoveFromPlaylist = viewModel::removeFromPlaylist,
                            onReorderPlaylistTracks = viewModel::reorderPlaylistTracks,
                            onReorderPlaylists = viewModel::reorderPlaylists,
                            onMatchMetadata = { track -> viewModel.matchMetadata(track) },
                            onImportNeteasePlaylist = viewModel::openPlaylistImport,
                            onExternalLyrics = openListening,
                            albumRequest = albumRequest,
                            onAlbumRequestHandled = viewModel::consumeAlbumRequest,
                            nav = libraryNav,
                            onAlbumStyle = viewModel::setAlbumStyle,
                        )
                    }
                    }
                }
                }
                if (playerFx(com.olbyine.music.data.PlayerFx.GRADUAL_BLUR) && !appLayout.tablet && dockHeightPx > 0) {
                    val dockHeight = with(androidx.compose.ui.platform.LocalDensity.current) { dockHeightPx.toDp() }
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(dockHeight + 18.dp)
                                .gradualBlur(dockSource, BlurEdge.BOTTOM, strength = 10.dp, layers = 4, solid = dockHeight),
                        )
                    }
                }
                if (appLayout.tablet) {
                    TabletSidebar(
                        layout = appLayout,
                        destination = state.destination,
                        section = libraryNav.section,
                        playlists = state.playlists,
                        activePlaylistId = state.activePlaylistId,
                        favoriteCount = remember(state.tracks) { state.tracks.count { it.favorite } },
                        source = dockSource,
                        onLibrary = openLibrarySection,
                        onPlaylist = { id ->
                            openLibrarySection(LibrarySection.PLAYLISTS)
                            viewModel.setActivePlaylistId(id)
                        },
                        onDestination = viewModel::navigateTo,
                        onWebDav = { webDavOpen = true },
                        streamingProvider = streamingProvider,
                        onStreaming = { provider -> streamingProvider = provider; viewModel.navigateTo(AppDestination.ONLINE) },
                        modifier = Modifier.width(appLayout.sidebarWidth),
                    )
                }
            }
            }
            CompositionLocalProvider(LocalGlassPopupHost provides basePopups) {
                WebDavScreen(
                    visible = webDavOpen,
                    repository = viewModel.webDav,
                    importing = state.importing,
                    importProgress = importProgress,
                    backEnabled = !playerOpen,
                    onDismiss = { webDavOpen = false },
                    onImport = { server, url -> viewModel.importWebDav(server, url) },
                    onPlay = { server, entries, index -> runPlaybackAction { viewModel.playWebDav(server, entries, index) } },
                )
            }
            CompositionLocalProvider(
                LocalGlassPopupHost provides basePopups,
                LocalNeteaseLinks provides openNeteaseLink,
                LocalNeteaseProfile provides openNeteaseProfile,
                LocalNeteaseComments provides openComments,
                LocalNeteaseChat provides openChat,
                LocalOpenArtist provides openArtist,
                LocalCollectionResume provides { key -> collectionResume[key] },
                LocalResumeCollection provides { key, songs -> runPlaybackAction { viewModel.resumeNeteaseCollection(key, songs) } },
                LocalPlayNeteaseCollection provides { key, songs, index, shuffle, startMs ->
                    runPlaybackAction { viewModel.playNeteaseSongs(songs, index, shuffle, key, startMs) }
                },
                LocalActiveCollection provides activeCollection,
            ) {
                NeteaseOverlayHost(
                    stack = ncmStack,
                    client = viewModel.neteaseClient,
                    currentTrackId = state.currentTrack?.id,
                    backEnabled = !playerOpen,
                    onPush = pushNcm,
                    onPlay = { songs, index, shuffle -> runPlaybackAction { viewModel.playNeteaseSongs(songs, index, shuffle) } },
                    onPauseMusic = viewModel::pausePlayback,
                )
            }
            GlassPopupLayer(basePopups, dockSource, popupGlassStyle(state.settings.glassPopups))
        }

        if (sheetComposed) {
            val track = playerState.currentTrack
            val setPage: (Int) -> Unit = { requested ->
                val page = if (appLayout.splitPlayer && requested == 1) 0 else requested
                infoVisible = page == -1
                if (state.lyricsVisible != (page == 1)) viewModel.showLyrics(page == 1)
            }
            val onPlayMode: (PlayMode) -> Unit = { mode ->
                when (mode) {
                    PlayMode.SEQUENTIAL -> viewModel.setPlayMode(false, Player.REPEAT_MODE_OFF)
                    PlayMode.REPEAT_ALL -> viewModel.setPlayMode(false, Player.REPEAT_MODE_ALL)
                    PlayMode.REPEAT_ONE -> viewModel.setPlayMode(false, Player.REPEAT_MODE_ONE)
                    PlayMode.SHUFFLE -> viewModel.setPlayMode(true, Player.REPEAT_MODE_ALL)
                }
            }
            PlayerTheme { HapticRipple {
                val motionPresentation = motionArtwork?.let { motion ->
                    MotionPresentation(
                        cover = motion.square?.takeIf { state.settings.motionCover },
                        background = motion.tall?.takeIf { state.settings.motionBackground },
                        playing = playerState.playback.isPlaying && sheetSettledOpen,
                    )
                }
                CompositionLocalProvider(
                    LocalTrackActions provides trackActions,
                    LocalPlayerBackdrop provides playerBackdrop,
                    LocalGlassSource provides playerBackdrop,
                    LocalNeteaseComments provides openComments,
                    LocalScComments provides openScComments,
                    LocalNeteaseProfile provides openNeteaseProfile,
                    LocalNeteaseLinks provides openNeteaseLink,
                    LocalOpenArtist provides openArtist,
                    LocalGlassPopupHost provides playerPopups,
                    LocalMotionArtwork provides motionPresentation,
                    LocalQueuePanel provides { if (playerState.queue.isNotEmpty()) queueOpen = true },
                    LocalAppLayout provides appLayout,
                    LocalNeteaseQuality provides (state.settings.neteaseQuality to viewModel::switchNeteaseQuality),
                    LocalStreamLikeCount provides viewModel::streamLikeCount,
                    LocalWatchMv provides { target ->
                        appScope.launch {
                            val mv = viewModel.neteaseMvOf(target)
                            if (mv > 0) pushNcm(NcmRoute.Mv(mv)) else viewModel.showMessage("这首歌没有 MV")
                        }
                    },
                ) {
                val playerPopupStyle = popupGlassStyle(state.settings.glassPopups)
                PlayerSheetHost(
                    sheet = sheet,
                    pager = pager,
                    collapsedBounds = collapsedBounds,
                    lyricsAvailable = track != null && !appLayout.splitPlayer,
                    shared = shared,
                    onSheetReleased = { open, velocity ->
                        haptics.light(com.olbyine.music.data.HapticArea.GESTURES)
                        if (open) {
                            sheet.settle(1f, velocity)
                        } else {
                            sheet.settle(0f, velocity)
                            viewModel.collapseNowPlaying()
                        }
                    },
                    onPagerReleased = { page, velocity ->
                        if (page != kotlin.math.round(pager.target).toInt()) haptics.light(com.olbyine.music.data.HapticArea.GESTURES)
                        pager.settle(page.toFloat(), velocity)
                        setPage(page)
                    },
                    background = {
                        Box(Modifier.fillMaxSize().backdropSource(playerBackdrop)) {
                            val custom = state.settings.customBackgroundPath
                            if (custom != null && state.settings.customBackgroundOnPlayer) {
                                CustomBackground(
                                    path = custom,
                                    blur = state.settings.customBackgroundBlur,
                                    dim = state.settings.customBackgroundDim.coerceAtLeast(.3f),
                                    scrim = Color.Black,
                                )
                            } else if (track != null) {
                                FlowingBackground(
                                    coverPath = playerCoverPath,
                                    seed = track.id,
                                    fallbackColors = playerState.artworkColors,
                                    modifier = Modifier.fillMaxSize(),
                                    beatReactive = state.settings.beatPulse && !listening,
                                    style = state.settings.backgroundStyle,
                                )
                                val tall = motionPresentation?.background
                                if (tall != null) {
                                    MotionVideo(tall, playing = motionPresentation.playing, modifier = Modifier.fillMaxSize())
                                    Box(
                                        Modifier.fillMaxSize().background(
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                0f to Color.Black.copy(alpha = .10f),
                                                .45f to Color.Black.copy(alpha = .05f),
                                                .72f to Color.Black.copy(alpha = .42f),
                                                1f to Color.Black.copy(alpha = .78f),
                                            ),
                                        ),
                                    )
                                }
                            }
                            if (state.settings.playerFx and com.olbyine.music.data.PlayerFx.FILM_GRAIN != 0) {
                                FilmGrain(active = playerPageShown && !LocalReduceMotion.current, modifier = Modifier.fillMaxSize())
                            }
                        }
                    },
                    miniRow = {
                        if (track != null) {
                            MiniPlayerRow(
                                track = track,
                                playback = playerState.playback,
                                lyric = miniLyric.value,
                                clock = miniClock,
                                showArtwork = false,
                                interactive = false,
                            )
                        }
                    },
                    artwork = if (track != null) {
                        { TrackArtwork(track, Modifier.fillMaxSize(), corner = 0.dp, decodeEdge = LARGE_COVER_EDGE) }
                    } else null,
                    playerPage = {
                        PlayerPage(
                            state = playerState,
                            lyrics = playerLyrics,
                            listening = listeningInfo,
                            onTogglePlay = togglePlay,
                            onPrevious = skipPrevious,
                            onNext = skipNext,
                            onSeek = seekTo,
                            onLyrics = { setPage(1) },
                            onInfo = { setPage(-1) },
                            onFavorite = { state.currentTrack?.let(viewModel::toggleFavorite) },
                            onPlayMode = onPlayMode,
                            onSpeed = viewModel::setPlaybackSpeed,
                            onSearchLyrics = { source -> viewModel.searchOnlineLyrics(state.currentTrack, source) },
                            onImportLyrics = { openLyricsPicker(state.currentTrack?.id) },
                            onMatchMetadata = { viewModel.matchMetadata(state.currentTrack) },
                            onPlayQueueIndex = viewModel::playQueueIndex,
                            onOpenLibrary = { viewModel.navigateTo(AppDestination.LIBRARY) },
                            onListening = setListening,
                            onGrantAccess = grantAccess,
                            onOpenExternalApp = openExternalApp,
                            shared = shared,
                            pageActive = playerPageShown,
                            onTransition = viewModel::setTransitionMode,
                            onAddToPlaylist = { playlistPickerFor = state.currentTrack },
                            onGoToAlbum = {
                                state.currentTrack?.let { current ->
                                    if (current.id.startsWith("ncm:")) {
                                        appScope.launch {
                                            val albumId = viewModel.neteaseAlbumIdOf(current)
                                            if (albumId != null) openNeteaseLink(com.olbyine.music.data.NeteaseTarget.Album(albumId))
                                            else viewModel.showMessage("没有找到这首歌的专辑")
                                        }
                                    } else viewModel.openAlbumOf(current)
                                }
                            },
                            onShareLyric = {
                                val current = playerState.currentTrack
                                if (current != null) {
                                    val line = playerLyrics.lines.getOrNull(playerLyrics.lineIndexAt(playerState.playback.estimatedPosition() + state.settings.lyricOffsetMs))
                                        ?.text?.takeIf(String::isNotBlank)
                                    val text = buildString {
                                        if (line != null) append("「$line」\n")
                                        append("—— ${current.title} · ${current.artist}")
                                    }
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent.createChooser(
                                                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                                    .putExtra(android.content.Intent.EXTRA_TEXT, text),
                                                "分享歌词",
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                            onRemoveFromLibrary = { removeConfirmFor = state.currentTrack },
                            onShareSong = {
                                playerState.currentTrack?.let { current ->
                                    val line = playerLyrics.lines.getOrNull(playerLyrics.lineIndexAt(playerState.playback.estimatedPosition() + state.settings.lyricOffsetMs))
                                        ?.text?.takeIf(String::isNotBlank)
                                    shareFor = current to line
                                }
                            },
                            onListenTogether = if (playerState.currentTrack?.id?.startsWith("sc:") == true) null else ({
                                viewModel.refreshListenTogether()
                                listenOpen = true
                            }),
                            topExtra = listenTogether.room?.let { room ->
                                { ListenTogetherBar(room, viewModel.neteaseClient.account.value?.userId) { listenOpen = true } }
                            },
                        )
                    },
                    lyricsPage = if (track != null && (lyricsWarm || lyricsShown || state.lyricsVisible || appLayout.splitPlayer)) {
                        {
                            LyricsPage(
                                track = track,
                                document = playerLyrics,
                                playback = playerState.playback,
                                visible = lyricsShown || (appLayout.splitPlayer && sheetSettledOpen),
                                split = appLayout.splitPlayer,
                                lineBlur = state.settings.lyricBlur,
                                showTranslation = state.settings.lyricTranslation,
                                showRoman = state.settings.lyricRomanization,
                                onShowTranslation = viewModel::setLyricTranslation,
                                onShowRoman = viewModel::setLyricRomanization,
                                subScale = state.settings.lyricSubScale,
                                doubleTapSeek = state.settings.lyricDoubleTapSeek,
                            mvStyle = MvStyle.entries.firstOrNull { it.name == state.settings.lyricMvStyle },
                            onMvStyle = { viewModel.setLyricMvStyle(it?.name.orEmpty()) },
                                fontSize = state.settings.lyricFontSize,
                                fontWeight = state.settings.lyricFontWeight,
                                fontPath = state.settings.lyricFontPath,
                                inactiveOpacity = state.settings.lyricInactiveOpacity,
                                offsetMs = state.settings.lyricOffsetMs,
                                alignPosition = state.settings.lyricAlignPosition,
                                onBack = { setPage(0) },
                                onSeek = { position ->
                                    haptics.click(com.olbyine.music.data.HapticArea.LYRICS)
                                    seekTo(position)
                                },
                                onImportLyrics = { openLyricsPicker(track.id) },
                                onSearchLyrics = { source -> viewModel.searchOnlineLyrics(track, source) },
                                lyricsSourceVisible = state.onlineLyrics.visible,
                                onlineSearching = state.onlineLyrics.searching && state.onlineLyrics.trackId == track.id,
                                queue = playerState.queue,
                                onTogglePlay = togglePlay,
                                onPrevious = skipPrevious,
                                onNext = skipNext,
                                onPlayMode = onPlayMode,
                                glassButtons = state.settings.playerGlassButtons && state.settings.uiStyle != com.olbyine.music.data.UiStyle.MATERIAL,
                                onFavorite = { viewModel.toggleFavorite(track) },
                                onPlayQueueIndex = viewModel::playQueueIndex,
                                externalStatus = if (listening) {
                                    when {
                                        externalLyrics.searching -> "正在匹配歌词…"
                                        else -> externalLyrics.message ?: "暂无歌词"
                                    }
                                } else null,
                            )
                        }
                    } else null,
                    infoPage = if (track != null && (infoShown || infoVisible) && track.id.startsWith("sc:")) {
                        {
                            val scId = track.id.removePrefix("sc:").toLongOrNull() ?: 0L
                            ScTrackInfoPage(
                                client = viewModel.soundCloud,
                                trackId = scId,
                                progress = { (-pager.value).coerceIn(0f, 1f) },
                                onOpenUser = { user ->
                                    scOpenRequest = user
                                    streamingProvider = "soundcloud"
                                    ncmStack.clear()
                                    viewModel.collapseNowPlaying()
                                    viewModel.navigateTo(AppDestination.ONLINE)
                                },
                                onComments = { scCommentsFor = scId to track.title },
                            )
                        }
                    } else if (track != null && (infoShown || infoVisible)) {
                        {
                            ArtistAlbumPage(
                                track = track,
                                revision = metadataRevision,
                                progress = { (-pager.value).coerceIn(0f, 1f) },
                                loadArtist = viewModel::artistInfo,
                                loadAlbum = viewModel::albumInfo,
                                onSaveArtist = viewModel::saveArtistInfo,
                                onSaveAlbum = viewModel::saveAlbumInfo,
                                onMatch = { viewModel.matchMetadata(track) },
                                onArtistHome = {
                                    val name = track.artist.split(" / ", "/", "、", ";", "&").first().trim().ifBlank { track.artist }
                                    if (track.id.startsWith("ncm:")) {
                                        appScope.launch { openArtist(viewModel.neteaseArtistIdsOf(track).firstOrNull(), name) }
                                    } else openArtist(null, name)
                                },
                                onAppear = { viewModel.autoDetails(track) },
                                wiki = {
                                    SongWikiSection(
                                        client = viewModel.neteaseClient,
                                        track = track,
                                        resolveId = viewModel::neteaseSongIdOf,
                                        onContribute = { id ->
                                            if (viewModel.neteaseClient.signedIn) {
                                                pushNcm(NcmRoute.Web(viewModel.neteaseClient.wikiEditorUrl(id)))
                                            } else {
                                                viewModel.showMessage("参与百科共建需要先登录网易云音乐")
                                            }
                                        },
                                        onHistory = { id -> wikiHistoryOpen = id ?: 0L },
                                    )
                                },
                            )
                        }
                    } else if (track != null) {
                        {}
                    } else null,
                    gesturesEnabled = { playerPopups.shown == null && !queueOpen },
                    split = appLayout.splitPlayer,
                    overlay = { source ->
                        GlassPopupLayer(playerPopups, source, playerPopupStyle)
                        QueuePanel(
                            visible = queueOpen && playerOpen,
                            source = source,
                            queue = playerState.queue,
                            playback = playerState.playback,
                            onDismiss = { queueOpen = false },
                            onPlay = viewModel::playQueueIndex,
                            onRemove = viewModel::removeQueueIndex,
                            onShuffle = viewModel::toggleShuffle,
                            onRepeat = viewModel::cycleRepeatMode,
                            actions = QueueItemActions(
                                playNext = { viewModel.playNext(listOf(it)) },
                                addToPlaylist = { playlistPickerFor = it },
                                share = { shareFor = it to null },
                                comments = { target ->
                                    when {
                                        target.id.startsWith("ncm:") -> target.id.removePrefix("ncm:").toLongOrNull()?.let { openComments(it, target.title) }
                                        target.id.startsWith("sc:") -> target.id.removePrefix("sc:").toLongOrNull()?.let { openScComments(it, target.title) }
                                    }
                                },
                                artist = { target ->
                                    queueOpen = false
                                    if (target.id.startsWith("ncm:")) {
                                        appScope.launch {
                                            val song = target.id.removePrefix("ncm:").toLongOrNull()?.let { runCatching { viewModel.neteaseClient.song(it) }.getOrNull() }
                                            openArtist(song?.artistIds?.firstOrNull(), target.artist.split(" / ", "/", "、").first())
                                        }
                                    } else openArtist(null, target.artist.split(" / ", "/", "、").first())
                                },
                                album = { target ->
                                    queueOpen = false
                                    if (target.id.startsWith("ncm:")) {
                                        appScope.launch {
                                            val albumId = viewModel.neteaseAlbumIdOf(target)
                                            if (albumId != null) openNeteaseLink(com.olbyine.music.data.NeteaseTarget.Album(albumId))
                                            else viewModel.showMessage("没有找到这首歌的专辑")
                                        }
                                    } else {
                                        viewModel.collapseNowPlaying()
                                        viewModel.openAlbumOf(target)
                                    }
                                },
                                mv = { target ->
                                    queueOpen = false
                                    appScope.launch {
                                        val mv = viewModel.neteaseMvOf(target)
                                        if (mv > 0) pushNcm(NcmRoute.Mv(mv)) else viewModel.showMessage("这首歌没有 MV")
                                    }
                                },
                            ),
                        )
                    },
                )
                }
            } }
        }

        scCommentsFor?.let { (id, title) ->
            ScCommentsSheet(viewModel.soundCloud, id, title, onDismiss = { scCommentsFor = null }, positionMs = {
                if (playerState.currentTrack?.id == "sc:$id") playerState.playback.estimatedPosition() else 0L
            })
        }
        commentsFor?.let { (id, title) ->
            NeteaseCommentsSheet(viewModel.neteaseClient, id, title, onDismiss = { commentsFor = null })
        }
        shareFor?.let { (shareTrack, line) ->
            ShareSongSheet(
                track = shareTrack,
                lyricLine = line,
                client = viewModel.neteaseClient,
                resolveId = viewModel::neteaseSongIdOf,
                onMessage = viewModel::showMessage,
                onDismiss = { shareFor = null },
                soundCloud = viewModel.soundCloud,
            )
        }
        if (folderSheet) {
            FolderScanSheet(
                folders = scanFolders,
                importing = state.importing,
                loadSubfolders = viewModel::subfolders,
                onAddFolder = { folderPicker.launch(null) },
                onScan = { targets ->
                    folderSheet = false
                    viewModel.importFolders(targets)
                },
                onDismiss = { folderSheet = false },
            )
        }
        wikiHistoryOpen?.let { songId ->
            WikiHistorySheet(viewModel.neteaseClient, songId.takeIf { it > 0 }, onDismiss = { wikiHistoryOpen = null })
        }
        if (listenOpen) {
            ListenTogetherSheet(
                state = listenTogether,
                client = viewModel.neteaseClient,
                currentSongId = playerState.currentTrack?.id?.takeIf { it.startsWith("ncm:") }?.removePrefix("ncm:")?.toLongOrNull(),
                onCreate = viewModel::createListenTogether,
                onJoin = viewModel::joinListenTogether,
                onLeave = viewModel::leaveListenTogether,
                onLogin = { listenOpen = false; viewModel.openNeteaseLogin() },
                onMessage = viewModel::showMessage,
                onDismiss = { listenOpen = false },
            )
        }
        VolumeHud(usbDriver, Modifier.align(Alignment.TopCenter))

        val snackbarBottom by animateDpAsState(
            if (playerOpen) 20.dp else with(density) { dockHeightPx.toDp() } + 4.dp,
            tween(OlbyineTokens.Motion.routeMs, easing = OlbyineTokens.Motion.easeOut),
            label = "snackbar-offset",
        )
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).then(
                if (playerOpen) Modifier.navigationBarsPadding() else Modifier,
            ).offset { IntOffset(0, -snackbarBottom.roundToPx()) },
        ) { data ->
            if (LocalStyleSpec.current.style != com.olbyine.music.data.UiStyle.MATERIAL) StyledToast(data) else Snackbar(
                snackbarData = data,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
                dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
    val ncmPickId = playlistPickerFor?.id?.takeIf { it.startsWith("ncm:") }?.removePrefix("ncm:")?.toLongOrNull()
    val scPickId = playlistPickerFor?.id?.takeIf { it.startsWith("sc:") }?.removePrefix("sc:")?.toLongOrNull()
    val pickContext = LocalContext.current
    if (scPickId != null) {
        ScPlaylistPicker(
            client = viewModel.soundCloud,
            trackId = scPickId,
            onDone = { message ->
                playlistPickerFor = null
                android.widget.Toast.makeText(pickContext, message, android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { playlistPickerFor = null },
        )
    } else if (ncmPickId != null) {
        NeteasePlaylistPicker(
            client = viewModel.neteaseClient,
            onPick = { playlist -> playlistPickerFor = null; viewModel.addNeteaseSongToPlaylist(playlist, ncmPickId) },
            onDismiss = { playlistPickerFor = null },
        )
    } else playlistPickerFor?.let { target ->
        OlbyineDialog(
            onDismissRequest = { playlistPickerFor = null },
            title = "加入歌单",
            confirmButton = { GlassTextButton(onClick = { playlistPickerFor = null }) { Text("完成") } },
        ) {
            if (state.playlists.isEmpty()) {
                Text("还没有歌单，先在音乐库右上角的 + 里新建一个。")
            } else {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    state.playlists.forEach { playlist ->
                        val included = target.id in playlist.trackIds
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                if (included) viewModel.removeFromPlaylist(playlist.id, target.id) else viewModel.addToPlaylist(playlist.id, target.id)
                            }.padding(horizontal = 6.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(playlist.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(
                                if (included) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                                null,
                                tint = if (included) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    removeConfirmFor?.let { target ->
        OlbyineDialog(
            onDismissRequest = { removeConfirmFor = null },
            title = "移除这首歌？",
            confirmButton = {
                GlassTextButton(onClick = {
                    removeConfirmFor = null
                    viewModel.collapseNowPlaying()
                    viewModel.deleteTrack(target)
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { GlassTextButton(onClick = { removeConfirmFor = null }) { Text("取消") } },
        ) { Text("将从音乐库移除「${target.title}」，原音频文件不会被删除。") }
    }
    neteaseState.login?.let { login ->
        NeteaseLoginSheet(
            state = login,
            onRefreshQr = viewModel::refreshNeteaseQr,
            onCookie = viewModel::signInNeteaseWithCookie,
            onDismiss = viewModel::closeNeteaseLogin,
        )
    }
    if (playlistImport.visible) {
        PlaylistImportSheet(
            state = playlistImport,
            onSource = viewModel::selectPlaylistSource,
            onFetch = viewModel::fetchNeteasePlaylist,
            onSpotifyLogin = { id ->
                viewModel.spotifyLoginUrl(id)?.let { url ->
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url)) }
                }
            },
            onSpotifyLogout = viewModel::logoutSpotify,
            onConfirm = viewModel::confirmPlaylistImport,
            onDismiss = viewModel::dismissPlaylistImport,
        )
    }
    if (metadataMatch.visible) {
        MetadataMatchSheet(
            state = metadataMatch,
            onSearch = { title, artist ->
                viewModel.matchMetadata(state.tracks.firstOrNull { it.id == metadataMatch.trackId }, title, artist)
            },
            onApply = viewModel::applyMetadata,
            onDismiss = viewModel::dismissMetadataMatch,
        )
    }
    if (state.onlineLyrics.visible) {
        OnlineLyricsSheet(
            state = state.onlineLyrics,
            trackTitle = state.tracks.firstOrNull { it.id == state.onlineLyrics.trackId }?.title,
            onSelect = viewModel::applyOnlineLyrics,
            onSourceChange = viewModel::selectOnlineLyricsSource,
            onQueryChange = viewModel::updateOnlineLyricsQuery,
            onRetry = viewModel::retryOnlineLyrics,
            onDismiss = viewModel::dismissOnlineLyricsCandidates,
        )
    }
}

internal data class MiniLyric(val index: Int, val line: com.olbyine.music.data.LyricLine)

@Composable
private fun rememberMiniLyric(
    document: LyricsDocument,
    trackId: String?,
    clock: androidx.compose.runtime.State<Long>,
): androidx.compose.runtime.State<MiniLyric?> = remember(document, trackId) {
    derivedStateOf {
        if (trackId == null || document.trackId != trackId || !document.isSynchronized) return@derivedStateOf null
        val index = document.lineIndexAt(clock.value)
        val line = document.lines.getOrNull(index) ?: return@derivedStateOf null
        if (line.text.isBlank()) null else MiniLyric(index, line)
    }
}

@Composable
private fun dockGlass(liquid: Boolean, cornerRadius: Dp): GlassStyle {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    return if (liquid) {
        GlassStyle(
            cornerRadius = cornerRadius,
            blur = 5.dp,
            tint = if (dark) Color.Black.copy(alpha = .24f) else Color.White.copy(alpha = .34f),
            saturation = 1.5f,
            lens = true,
            refractionHeight = 24.dp,
            refractionAmount = 24.dp,
            dispersion = true,
            highlight = Color.White.copy(alpha = if (dark) .55f else .8f),
            shadow = 14.dp,
            fallback = colors.surfaceContainerHigh,
        )
    } else {
        GlassStyle(
            cornerRadius = cornerRadius,
            blur = 26.dp,
            tint = colors.surfaceContainerHigh.copy(alpha = if (dark) .70f else .66f),
            saturation = 1.7f,
            highlight = Color.White.copy(alpha = if (dark) .14f else .45f),
            shadow = 14.dp,
            fallback = colors.surfaceContainerHigh,
        )
    }
}

@Composable
private fun miniPlayerStyle(liquid: Boolean): GlassStyle {
    val spec = LocalStyleSpec.current
    val colors = MaterialTheme.colorScheme
    return when (spec.style) {
        com.olbyine.music.data.UiStyle.IOS -> dockGlass(liquid, MiniCorner)
        com.olbyine.music.data.UiStyle.MATERIAL -> GlassStyle(
            cornerRadius = 16.dp,
            blur = 0.dp,
            tint = colors.surfaceContainerHigh,
            saturation = 1f,
            shadow = 3.dp,
            fallback = colors.surfaceContainerHigh,
        )
        com.olbyine.music.data.UiStyle.HYPEROS -> dockGlass(false, 28.dp)
        com.olbyine.music.data.UiStyle.COLOROS -> dockGlass(false, 18.dp)
    }
}

@Composable
private fun BottomDock(
    state: MainUiState,
    selected: AppDestination,
    source: BackdropSource,
    lyric: androidx.compose.runtime.State<MiniLyric?>,
    lyricClock: androidx.compose.runtime.State<Long>,
    modifier: Modifier,
    onMiniBounds: (Rect) -> Unit,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStop: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onSelect: (AppDestination) -> Unit,
    tablet: Boolean = false,
    startInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val liquid = state.settings.liquidGlassEnabled
    val spec = LocalStyleSpec.current
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val bottomPadding = with(density) { (navBottom * 0.45f).toDp() } + 4.dp
    val showMini = state.currentTrack != null && (tablet || selected != AppDestination.SETTINGS)
    Column(
        modifier.fillMaxWidth().padding(start = startInset),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = showMini,
            enter = fadeIn(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)) +
                slideInVertically(appleSpring(response = .32f, damping = .9f)) { it / 2 } +
                scaleIn(appleSpring(response = .32f), initialScale = .9f, transformOrigin = TransformOrigin(.5f, 1f)),
            exit = fadeOut(tween(OlbyineTokens.Motion.quickMs, easing = OlbyineTokens.Motion.easeOut)) +
                slideOutVertically(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut)) { it / 2 } +
                scaleOut(tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut), targetScale = .9f, transformOrigin = TransformOrigin(.5f, 1f)),
        ) {
            val track = state.currentTrack ?: return@AnimatedVisibility
            MiniPlayer(
                track = track,
                playback = state.playback,
                lyric = lyric.value,
                lyricClock = lyricClock,
                source = source,
                style = miniPlayerStyle(liquid && spec.dock == DockKind.FLOATING_GLASS),
                modifier = Modifier
                    .then(if (tablet) Modifier.widthIn(max = 600.dp) else Modifier)
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                    .onGloballyPositioned { onMiniBounds(it.boundsInRoot()) },
                onOpen = onOpenPlayer,
                onToggle = onToggle,
                onNext = onNext,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragStop = onDragStop,
                onDragCancel = onDragCancel,
            )
        }
        if (tablet) {
            Spacer(Modifier.navigationBarsPadding().height(6.dp))
        } else when (spec.dock) {
            DockKind.FLOATING_GLASS -> Box(Modifier.padding(bottom = bottomPadding)) {
                LiquidTabBar(
                    tabs = DockTabs,
                    selected = selected,
                    source = source,
                    liquid = liquid,
                    hapticsEnabled = state.settings.hapticsEnabled,
                    onSelect = onSelect,
                )
            }
            DockKind.FLOATING_BAR -> FloatingTabBar(
                tabs = DockTabs,
                selected = selected,
                source = source,
                onSelect = onSelect,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = bottomPadding),
            )
            DockKind.FLAT_BAR -> FlatTabBar(DockTabs, selected, source, onSelect)
            DockKind.NAVIGATION_BAR -> MaterialNavigationBar(DockTabs, selected, onSelect)
        }
    }
}

@Composable
private fun FlatTabBar(tabs: List<DockTab>, selected: AppDestination, source: BackdropSource, onSelect: (AppDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val style = remember(colors) {
        GlassStyle(
            cornerRadius = 0.dp,
            blur = 24.dp,
            tint = colors.surfaceContainer.copy(alpha = .82f),
            saturation = 1.6f,
            fallback = colors.surfaceContainer,
        )
    }
    Box(Modifier.fillMaxWidth().glass(source, style).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val isSelected = tab.destination == selected
                val tint by animateColorAsState(if (isSelected) colors.primary else colors.onSurfaceVariant, label = "flat-tab")
                val tabPress = remember { MutableInteractionSource() }
                val pressed = rememberPressProgress(tabPress)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = tabPress,
                            indication = null,
                            role = Role.Tab,
                        ) {
                            haptics.tab(com.olbyine.music.data.HapticArea.GESTURES)
                            onSelect(tab.destination)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.graphicsLayer { val s = 1f - .1f * pressed.value; scaleX = s; scaleY = s }) {
                        IconSwap(if (isSelected) tab.selectedIcon else tab.icon, tab.label, 24.dp, tint)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = 11.sp, color = tint, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun FloatingTabBar(
    tabs: List<DockTab>,
    selected: AppDestination,
    source: BackdropSource,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Box(modifier.fillMaxWidth().height(64.dp).glass(source, dockGlass(false, 32.dp))) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val isSelected = tab.destination == selected
                val tint by animateColorAsState(if (isSelected) colors.primary else colors.onSurfaceVariant, label = "hyper-tab")
                val lift by animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = .92f, stiffness = 600f), label = "hyper-lift")
                val tabPress = remember { MutableInteractionSource() }
                val pressed = rememberPressProgress(tabPress)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = tabPress,
                            indication = null,
                            role = Role.Tab,
                        ) {
                            haptics.tab(com.olbyine.music.data.HapticArea.GESTURES)
                            onSelect(tab.destination)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.graphicsLayer { val s = 1f + .08f * lift - .08f * pressed.value; scaleX = s; scaleY = s }) {
                        IconSwap(if (isSelected) tab.selectedIcon else tab.icon, tab.label, 24.dp, tint)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = 10.5.sp, color = tint, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun MaterialNavigationBar(tabs: List<DockTab>, selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Box(Modifier.fillMaxWidth().background(colors.surfaceContainer).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val isSelected = tab.destination == selected
                val indicator by animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = 1f, stiffness = 500f), label = "m3-indicator")
                val tabPress = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = tabPress,
                            indication = null,
                            role = Role.Tab,
                        ) {
                            haptics.tab(com.olbyine.music.data.HapticArea.GESTURES)
                            onSelect(tab.destination)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(width = 64.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = .5f + .5f * indicator
                                    alpha = indicator
                                }
                                .clip(CircleShape)
                                .background(colors.secondaryContainer),
                        )
                        IconSwap(
                            if (isSelected) tab.selectedIcon else tab.icon,
                            tab.label,
                            24.dp,
                            if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

internal fun tabSwitch(direction: Int, material: Boolean, reduce: Boolean): androidx.compose.animation.ContentTransform {
    if (reduce) return fadeIn(tween(120)).togetherWith(fadeOut(tween(80)))
    if (material) {
        return (fadeIn(tween(210, delayMillis = 90, easing = OlbyineTokens.Motion.easeOut)) +
            scaleIn(tween(210, delayMillis = 90, easing = OlbyineTokens.Motion.easeOut), initialScale = .92f))
            .togetherWith(fadeOut(tween(90, easing = androidx.compose.animation.core.LinearEasing)))
    }
    val glide = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 1f, stiffness = 520f)
    return (fadeIn(tween(200, delayMillis = 30, easing = OlbyineTokens.Motion.easeOut)) +
        slideInHorizontally(glide) { width -> direction * width / 9 })
        .togetherWith(
            fadeOut(tween(120, easing = OlbyineTokens.Motion.easeOut)) +
                slideOutHorizontally(glide) { width -> -direction * width / 14 },
        )
}

internal val MiniCorner = 22.dp
internal val MiniHeight = 58.dp
internal val MiniArtSize = 42.dp
internal val MiniArtInset = 8.dp
private val TabWidth = 92.dp
private val TabBarHeight = 62.dp
private val TabBarPadding = 5.dp

private data class DockTab(
    val destination: AppDestination,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val DockTabs = listOf(
    DockTab(AppDestination.LIBRARY, "音乐库", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    DockTab(AppDestination.ONLINE, "流媒体", Icons.Outlined.Cloud, Icons.Rounded.Cloud),
    DockTab(AppDestination.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
private fun LiquidTabBar(
    tabs: List<DockTab>,
    selected: AppDestination,
    source: BackdropSource,
    liquid: Boolean,
    hapticsEnabled: Boolean,
    onSelect: (AppDestination) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val tabWidthPx = with(density) { TabWidth.toPx() }
    val paddingPx = with(density) { TabBarPadding.toPx() }
    val maxX = tabWidthPx * (tabs.size - 1)
    val selectedIndex = tabs.indexOfFirst { it.destination == selected }.coerceAtLeast(0)
    val indicatorX = remember { androidx.compose.animation.core.Animatable(selectedIndex * tabWidthPx) }
    val press = remember { androidx.compose.animation.core.Animatable(0f) }
    var velocity by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val currentTabs by androidx.compose.runtime.rememberUpdatedState(tabs)
    val currentSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)
    LaunchedEffect(selectedIndex, tabWidthPx) {
        if (!dragging) indicatorX.animateTo(selectedIndex * tabWidthPx, spring(dampingRatio = .72f, stiffness = 380f))
    }
    val accentSource = rememberBackdropSource()
    val barStyle = dockGlass(liquid, TabBarHeight / 2)
    val primary = MaterialTheme.colorScheme.primary
    val indicatorStyle = barStyle.copy(
        cornerRadius = (TabBarHeight - TabBarPadding * 2) / 2,
        lens = liquid,
        refractionHeight = 12.dp,
        refractionAmount = 16.dp,
        dispersion = liquid,
        highlight = if (liquid) Color.White.copy(alpha = .85f) else Color.Transparent,
        shadow = 0.dp,
    )
    val indicatorDynamics = remember { GlassDynamics(lensProgress = { press.value }) }

    Box(
        Modifier
            .width(TabWidth * tabs.size + TabBarPadding * 2)
            .height(TabBarHeight)
            .glass(source, barStyle)
            .pointerInput(tabs.size, tabWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = indicatorX.value
                    val fingerX = down.position.x - paddingPx - tabWidthPx / 2f
                    val onIndicator = kotlin.math.abs(fingerX - start) < tabWidthPx / 2f
                    val origin = if (onIndicator) start else fingerX.coerceIn(0f, maxX)
                    dragging = true
                    scope.launch { press.animateTo(1f, spring(dampingRatio = .85f, stiffness = 600f)) }
                    if (!onIndicator) scope.launch { indicatorX.animateTo(origin, spring(dampingRatio = .8f, stiffness = 600f)) }
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var moved = false
                    var lastIndex = kotlin.math.round(origin / tabWidthPx).toInt()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = change.position.x - down.position.x
                        if (!moved && kotlin.math.abs(dx) > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            val x = rubberbandClamp(origin + dx, 0f, maxX, dimension = tabWidthPx * .4f)
                            scope.launch { indicatorX.snapTo(x) }
                            velocity = tracker.calculateVelocity().x
                            val index = kotlin.math.round(x / tabWidthPx).toInt().coerceIn(0, currentTabs.lastIndex)
                            if (index != lastIndex) {
                                haptics.tick(com.olbyine.music.data.HapticArea.GESTURES)
                                lastIndex = index
                            }
                            change.consume()
                        }
                    }
                    val flingVelocity = tracker.calculateVelocity().x
                    val finalIndex = if (moved) {
                        kotlin.math.round((indicatorX.value + projectFling(flingVelocity, .99f)) / tabWidthPx).toInt()
                    } else {
                        ((down.position.x - paddingPx) / tabWidthPx).toInt()
                    }.coerceIn(0, currentTabs.lastIndex)
                    dragging = false
                    velocity = 0f
                    scope.launch {
                        indicatorX.animateTo(
                            finalIndex * tabWidthPx,
                            spring(dampingRatio = .86f, stiffness = 420f),
                            initialVelocity = if (moved) flingVelocity else 0f,
                        )
                    }
                    scope.launch { press.animateTo(0f, spring(dampingRatio = .82f, stiffness = 300f)) }
                    if (!moved || finalIndex != lastIndex) haptics.tab(com.olbyine.music.data.HapticArea.GESTURES)
                    currentSelect(currentTabs[finalIndex].destination)
                }
            }
            .padding(TabBarPadding),
    ) {
        TabRowContent(tabs, accent = true, modifier = Modifier.matchParentSize().hiddenBackdropSource(accentSource))
        TabRowContent(tabs, accent = false, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .width(TabWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = indicatorX.value
                    val p = press.value
                    val stretch = (kotlin.math.abs(velocity) / 7000f).coerceAtMost(.16f) * p
                    scaleX = 1f + .08f * p + stretch
                    scaleY = 1f + .08f * p - stretch * .5f
                }
                .glass(source, indicatorStyle, dynamics = indicatorDynamics, sharpSources = listOf(accentSource))
                .drawBehind { drawRect(primary.copy(alpha = .13f * (1f - press.value * .6f))) },
        )
    }
}

@Composable
private fun TabRowContent(tabs: List<DockTab>, accent: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { tab ->
            Column(
                Modifier.width(TabWidth).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StyledIcon(
                    if (accent) tab.selectedIcon else tab.icon,
                    contentDescription = if (accent) null else tab.label,
                    modifier = Modifier.size(24.dp),
                    tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    fontSize = 11.sp,
                    fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    playback: PlaybackState,
    lyric: MiniLyric?,
    lyricClock: androidx.compose.runtime.State<Long>,
    source: BackdropSource,
    style: GlassStyle,
    modifier: Modifier,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStop: (Float) -> Unit,
    onDragCancel: () -> Unit,
) {
    val clock = rememberPlaybackClock(playback, intervalMs = 250L)
    val duration = playback.durationMs.takeIf { it > 0L } ?: track.durationMs
    val pendingSeek = playback.pendingSeekMs
    val progressColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
    Box(
        modifier
            .fillMaxWidth()
            .height(MiniHeight)
            .glass(source, style)
            .pointerInput(onDragStart, onDrag, onDragStop, onDragCancel) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var previousY = down.position.y
                    var totalY = 0f
                    var dragStarted = false
                    var canceled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size > 1) {
                            canceled = true
                            if (dragStarted) onDragCancel()
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            if (dragStarted && pressed.isNotEmpty()) {
                                canceled = true
                                onDragCancel()
                            }
                            break
                        }
                        val deltaY = change.position.y - previousY
                        previousY = change.position.y
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!dragStarted) {
                            totalY += deltaY
                            if (totalY <= -viewConfiguration.touchSlop) {
                                dragStarted = true
                                onDragStart()
                                onDrag(totalY + viewConfiguration.touchSlop)
                                change.consume()
                            }
                        } else {
                            onDrag(deltaY)
                            change.consume()
                        }
                    }
                    if (dragStarted && !canceled) onDragStop(tracker.calculateVelocity().y)
                }
            }
            .clickable(onClickLabel = "打开播放器", onClick = onOpen),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { }
                .drawBehind {
                    val inset = 22.dp.toPx()
                    val lineHeight = 2.dp.toPx()
                    val top = size.height - 5.dp.toPx() - lineHeight
                    val width = size.width - inset * 2
                    val fraction = if (duration > 0L) ((pendingSeek ?: clock.value).toFloat() / duration).coerceIn(0f, 1f) else 0f
                    drawRoundRect(trackColor, androidx.compose.ui.geometry.Offset(inset, top), Size(width, lineHeight), CornerRadius(lineHeight))
                    drawRoundRect(progressColor, androidx.compose.ui.geometry.Offset(inset, top), Size(width * fraction, lineHeight), CornerRadius(lineHeight))
                },
        )
        MiniPlayerRow(
            track = track,
            playback = playback,
            lyric = lyric,
            clock = lyricClock,
            showArtwork = true,
            interactive = true,
            onToggle = onToggle,
            onNext = onNext,
        )
    }
}

@Composable
internal fun MiniPlayerRow(
    track: Track,
    playback: PlaybackState,
    lyric: MiniLyric?,
    clock: androidx.compose.runtime.State<Long>,
    showArtwork: Boolean,
    interactive: Boolean,
    onToggle: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxSize().padding(start = MiniArtInset, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val direction = rememberSkipDirection(playback.currentIndex, playback.mediaItemCount)
        val travel = skipDistance()
        val reduce = LocalReduceMotion.current
        if (showArtwork) {
            AnimatedContent(
                targetState = track,
                contentKey = { it.coverPath ?: it.artworkUrl ?: it.id },
                transitionSpec = { skipTransition(direction, travel / 2, reduce) },
                label = "mini-art",
            ) { shown -> TrackArtwork(shown, Modifier.size(MiniArtSize), corner = OlbyineTokens.Corner.thumb) }
        } else Spacer(Modifier.size(MiniArtSize))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = track,
                contentKey = { it.id },
                transitionSpec = { skipTransition(direction, travel, reduce) },
                label = "mini-title",
            ) { shown ->
                Text(
                    shown.title,
                    Modifier.fillMaxWidth().then(
                        if (interactive && !reduce) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_200, velocity = 28.dp)
                        else Modifier,
                    ),
                    maxLines = 1,
                    overflow = if (interactive && !reduce) TextOverflow.Clip else TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val lyricStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            AnimatedContent(
                targetState = lyric,
                contentKey = { it?.index ?: -1 },
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = .9f, stiffness = 420f)) { it } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically(spring(dampingRatio = .9f, stiffness = 420f)) { -it } + fadeOut(tween(140)))
                },
                label = "mini-subtitle",
            ) { shown ->
                if (shown != null) {
                    LyricSweepLine(
                        line = shown.line,
                        clock = clock,
                        started = true,
                        synced = true,
                        style = lyricStyle,
                        ink = MaterialTheme.colorScheme.onSurface,
                        centered = false,
                        unsungAlpha = .5f,
                    )
                } else {
                    Text(
                        track.artist,
                        Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .then(if (interactive) Modifier.pressable(label = "播放或暂停", onClick = onToggle) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (playback.isBuffering) {
                OlbyineSpinner(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
            } else {
                AnimatedContent(
                    targetState = playback.isPlaying,
                    transitionSpec = {
                        (fadeIn(tween(140)) + scaleIn(initialScale = .7f, animationSpec = tween(160, easing = OlbyineTokens.Motion.easeOut)))
                            .togetherWith(fadeOut(tween(90)))
                    },
                    label = "mini-play",
                ) { playing ->
                    StyledIcon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "暂停" else "播放",
                        Modifier.size(28.dp).then(morphBlur()),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (interactive) {
            GhostIconButton(Icons.Rounded.SkipNext, "下一首", onNext, size = 42.dp, iconSize = 24.dp)
        } else {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                StyledIcon(Icons.Rounded.SkipNext, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private enum class RefreshMode { SYSTEM, PLAYER, LYRICS }

private fun Activity.applyRefreshMode(mode: RefreshMode) {
    val window = window ?: return
    val rates = runCatching { window.decorView.display?.supportedModes?.map { it.refreshRate } }.getOrNull().orEmpty()
    val rate = when (mode) {
        RefreshMode.SYSTEM -> 0f
        RefreshMode.PLAYER, RefreshMode.LYRICS -> rates.maxOrNull() ?: 0f
    }
    window.attributes = window.attributes.apply { preferredRefreshRate = rate }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

internal val SPEEDS = listOf(.75f, 1f, 1.25f, 1.5f, 2f)
internal fun Float.formatSpeed(): String = if (this % 1f == 0f) "${toInt()}.0" else toString()
private val AUDIO_MIME_TYPES = arrayOf("audio/*", "audio/mp4", "audio/x-m4a", "video/mp4", "application/mp4", "application/ogg", "application/octet-stream")
private val LYRIC_MIME_TYPES = arrayOf("*/*")

@Composable
private fun VolumeHud(driver: com.olbyine.music.playback.usb.UsbDriverState, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(driver.volume) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        visible = true
        delay(1_300)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.statusBarsPadding().padding(top = 8.dp),
        enter = fadeIn(tween(160, easing = OlbyineTokens.Motion.easeOut)) +
            scaleIn(initialScale = .92f, animationSpec = appleSpring(response = .3f)),
        exit = fadeOut(tween(OlbyineTokens.Motion.quickMs, easing = OlbyineTokens.Motion.easeOut)) +
            scaleOut(targetScale = .96f, animationSpec = tween(OlbyineTokens.Motion.quickMs, easing = OlbyineTokens.Motion.easeOut)),
    ) {
        Row(
            Modifier
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .94f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (driver.volume == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(120.dp).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(driver.volume / 100f).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("${driver.volume}", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
        }
    }
}