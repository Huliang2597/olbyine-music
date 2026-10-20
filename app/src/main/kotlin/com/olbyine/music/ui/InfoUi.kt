package com.olbyine.music.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.MetadataMatchUiState
import com.olbyine.music.PlaylistImportUiState
import com.olbyine.music.PlaylistSource
import com.olbyine.music.data.SpotifyRepository
import com.olbyine.music.data.AlbumInfo
import com.olbyine.music.data.ArtistInfo
import com.olbyine.music.data.MetadataCandidate
import com.olbyine.music.data.MetadataSource
import com.olbyine.music.data.Track

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ArtistAlbumPage(
    track: Track,
    revision: Int,
    progress: () -> Float,
    loadArtist: suspend (String) -> ArtistInfo?,
    loadAlbum: suspend (String, String) -> AlbumInfo?,
    onSaveArtist: (name: String, bio: String, image: Uri?) -> Unit,
    onSaveAlbum: (title: String, artist: String, description: String, releaseDate: String, image: Uri?) -> Unit,
    onMatch: () -> Unit,
    onArtistHome: (() -> Unit)? = null,
    localComposerCount: Int = 0,
    localAlbumCount: Int = 0,
    onLocalComposer: (() -> Unit)? = null,
    onLocalAlbum: (() -> Unit)? = null,
    onAppear: () -> Unit = {},
    wiki: (@Composable () -> Unit)? = null,
) {
    LaunchedEffect(track.id) { onAppear() }
    val artistName = track.artist.split(" / ", "/", "、", ";", "&").first().trim().ifBlank { track.artist }
    val artist by produceState<ArtistInfo?>(null, artistName, revision) { value = loadArtist(artistName) }
    val album by produceState<AlbumInfo?>(null, track.album, track.artist, revision) { value = loadAlbum(track.album, track.artist) }
    var editArtist by rememberSaveable { mutableStateOf(false) }
    var editAlbum by rememberSaveable { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onBackground
    val scroll = rememberScrollState()

    androidx.compose.foundation.text.selection.SelectionContainer { Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 38.dp, height = 5.dp).clip(CircleShape).background(ink.copy(alpha = .32f)))
        }
        Spacer(Modifier.height(18.dp))
        Overline("歌手", color = ink.copy(alpha = .6f), modifier = Modifier.rise(progress, 0))
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .rise(progress, 1, scaleFrom = .82f)
                .size(168.dp)
                .shadow(28.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .08f))
                .clickable { onArtistHome?.invoke() ?: run { editArtist = true } },
        ) {
            PathImage(artist?.imagePath, Modifier.fillMaxSize(), maxEdge = 640, contentDescription = "$artistName 头像") {
                StyledIcon(Icons.Rounded.Person, null, Modifier.size(72.dp), tint = ink.copy(alpha = .5f))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            artistName,
            Modifier.rise(progress, 2),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        (track.composerCredit?.takeIf(String::isNotBlank) ?: track.composer)?.trim()?.takeIf(String::isNotEmpty)?.let { composer ->
            Text(
                "作曲：$composer",
                Modifier.padding(top = 6.dp).rise(progress, 2),
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = .62f),
                textAlign = TextAlign.Center,
            )
        }
        artist?.source?.let { source ->
            Text(
                "资料来源：${source.label}",
                Modifier.padding(top = 4.dp).rise(progress, 2),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = .5f),
            )
        }
        Spacer(Modifier.height(14.dp))
        InfoCard(Modifier.rise(progress, 3)) {
            ExpandableText(
                artist?.bio?.takeIf(String::isNotBlank) ?: "还没有歌手简介。点“匹配资料”从网易云 / QQ 音乐 / Apple Music 获取，或点“编辑”自己写。",
                collapsedLines = 6,
                muted = artist?.bio.isNullOrBlank(),
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onArtistHome != null) SmallChip("歌手主页", Icons.Rounded.Person, onClick = onArtistHome)
                if (onLocalComposer != null && localComposerCount > 0) {
                    SmallChip("作曲人的本地歌曲 · $localComposerCount", Icons.Rounded.EditNote, onClick = onLocalComposer)
                }
                SmallChip("编辑", Icons.Rounded.Edit) { editArtist = true }
                SmallChip("匹配资料", Icons.Rounded.Search, onClick = onMatch)
            }
        }

        Spacer(Modifier.height(30.dp))
        Overline("专辑", color = ink.copy(alpha = .6f), modifier = Modifier.rise(progress, 4))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().rise(progress, 5), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(112.dp)
                    .shadow(16.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { editAlbum = true },
            ) {
                val albumCover = album?.coverPath
                if (albumCover != null) {
                    PathImage(albumCover, Modifier.fillMaxSize(), maxEdge = 512, contentDescription = "${track.album} 封面")
                } else {
                    TrackArtwork(track, Modifier.fillMaxSize(), corner = 0.dp, decodeEdge = 512)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.album,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val facts = listOfNotNull(
                    album?.releaseDate?.takeIf(String::isNotBlank),
                    album?.genre?.takeIf(String::isNotBlank),
                    track.formatLabel(),
                )
                if (facts.isNotEmpty()) {
                    Text(
                        facts.joinToString(" · "),
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = ink.copy(alpha = .55f),
                        maxLines = 2,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        InfoCard(Modifier.rise(progress, 6)) {
            ExpandableText(
                album?.description?.takeIf(String::isNotBlank) ?: "还没有专辑介绍。",
                collapsedLines = 5,
                muted = album?.description.isNullOrBlank(),
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onLocalAlbum != null && localAlbumCount > 0) {
                    SmallChip("专辑的本地歌曲 · $localAlbumCount", Icons.Rounded.Album, onClick = onLocalAlbum)
                }
                SmallChip("编辑", Icons.Rounded.Edit) { editAlbum = true }
                SmallChip("匹配封面与信息", Icons.Rounded.Refresh, onClick = onMatch)
            }
        }
        if (wiki != null) {
            Spacer(Modifier.height(30.dp))
            Box(Modifier.rise(progress, 7)) { wiki() }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "向左滑动返回播放页",
            Modifier.rise(progress, 7),
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = .4f),
        )
        Spacer(Modifier.height(28.dp))
    } }

    if (editArtist) {
        EditInfoDialog(
            title = "编辑歌手资料",
            imageLabel = "更换头像",
            fields = listOf("简介" to (artist?.bio ?: "")),
            onDismiss = { editArtist = false },
            onSave = { values, image ->
                editArtist = false
                onSaveArtist(artistName, values[0], image)
            },
        )
    }
    if (editAlbum) {
        EditInfoDialog(
            title = "编辑专辑资料",
            imageLabel = "更换专辑图",
            fields = listOf("发行日期" to (album?.releaseDate ?: ""), "专辑介绍" to (album?.description ?: "")),
            onDismiss = { editAlbum = false },
            onSave = { values, image ->
                editAlbum = false
                onSaveAlbum(track.album, track.artist, values[1], values[0], image)
            },
        )
    }
}

internal fun Modifier.rise(progress: () -> Float, index: Int, scaleFrom: Float = 1f): Modifier = graphicsLayer {
    val start = index * .06f
    val local = ((progress() - start) / (1f - start).coerceAtLeast(.3f)).coerceIn(0f, 1f)
    val eased = 1f - (1f - local) * (1f - local) * (1f - local)
    alpha = eased
    translationY = (1f - eased) * 42.dp.toPx()
    if (scaleFrom != 1f) {
        val scale = scaleFrom + (1f - scaleFrom) * eased
        scaleX = scale
        scaleY = scale
    }
}

@Composable
internal fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = .08f))
            .border(0.7.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
internal fun ExpandableText(text: String, collapsedLines: Int, muted: Boolean) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflow by remember(text) { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onBackground
    Column(Modifier.animateContentSize(spring(dampingRatio = .86f, stiffness = 380f))) {
        Text(
            text,
            Modifier.clickable(enabled = overflow || expanded) { expanded = !expanded },
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = if (muted) .5f else .86f),
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
        )
        if (overflow || expanded) {
            Text(
                if (expanded) "收起" else "展开",
                Modifier.padding(top = 4.dp).clickable { expanded = !expanded },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun SmallChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = .1f))
            .pressable(label = text, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = ink.copy(alpha = .85f))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .85f))
    }
}

@Composable
private fun EditInfoDialog(
    title: String,
    imageLabel: String,
    fields: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (List<String>, Uri?) -> Unit,
) {
    val values = remember { androidx.compose.runtime.mutableStateListOf(*fields.map { it.second }.toTypedArray()) }
    var image by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) image = uri }
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = { GlassTextButton(onClick = { onSave(values.toList(), image) }) { Text("保存") } },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                if (image != null) "已选择图片" else imageLabel,
                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                icon = Icons.Rounded.Image,
                style = PillStyle.TONAL,
            )
            fields.forEachIndexed { index, (label, _) ->
                val multiline = index == fields.lastIndex
                GlassTextField(values[index], { values[index] = it }, label, minLines = if (multiline) 4 else 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetadataMatchSheet(
    state: MetadataMatchUiState,
    onSearch: (title: String, artist: String) -> Unit,
    onApply: (MetadataCandidate, cover: Boolean, text: Boolean, details: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(state.trackId) { mutableStateOf(state.queryTitle) }
    var artist by remember(state.trackId) { mutableStateOf(state.queryArtist) }
    var applyCover by rememberSaveable { mutableStateOf(true) }
    var applyText by rememberSaveable { mutableStateOf(false) }
    var applyDetails by rememberSaveable { mutableStateOf(true) }
    GlassBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("匹配封面与信息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "同时搜索网易云音乐、QQ 音乐和 Apple Music",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OlbyineTextField(title, { title = it }, "歌曲名", Modifier.weight(1f))
                OlbyineTextField(artist, { artist = it }, "歌手", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OptionCheck("封面", applyCover) { applyCover = it }
                OptionCheck("歌名/歌手/专辑", applyText) { applyText = it }
                OptionCheck("歌手与专辑资料", applyDetails) { applyDetails = it }
            }
            PillButton(
                if (state.searching) "搜索中…" else "重新搜索",
                { onSearch(title, artist) },
                icon = Icons.Rounded.Search,
                style = PillStyle.TONAL,
                enabled = !state.searching && !state.applying && title.isNotBlank(),
            )
            Spacer(Modifier.height(8.dp))
        }
        when {
            state.searching || state.applying -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                OlbyineSpinner()
            }
            state.candidates.isEmpty() -> Text(
                state.message ?: "没有结果",
                Modifier.fillMaxWidth().padding(32.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                itemsIndexed(state.candidates, key = { index, it -> "$index:${it.source}:${it.title}" }) { _, candidate ->
                    Row(
                        itemMotion()
                            .fillMaxWidth()
                            .clickable(enabled = applyCover || applyText || applyDetails) {
                                onApply(candidate, applyCover, applyText, applyDetails)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrlImage(
                            candidate.coverUrl,
                            Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            StyledIcon(Icons.Rounded.Album, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(candidate.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(candidate.artist, candidate.album).filter(String::isNotBlank).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            candidate.releaseDate?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Badge(candidate.source.shortLabel(), color = candidate.source.tint(), filled = true)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun OptionCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCheck(checked, onChange)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun MetadataSource.shortLabel() = when (this) {
    MetadataSource.NETEASE -> "网易云"
    MetadataSource.QQ -> "QQ"
    MetadataSource.APPLE -> "Apple"
    MetadataSource.LOCAL -> "本地"
}

private fun MetadataSource.tint() = when (this) {
    MetadataSource.NETEASE -> Color(0xFFE5484D)
    MetadataSource.QQ -> Color(0xFF31C27C)
    MetadataSource.APPLE -> Color(0xFFFA586A)
    MetadataSource.LOCAL -> Color(0xFF8E8E93)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistImportSheet(
    state: PlaylistImportUiState,
    onSource: (PlaylistSource) -> Unit,
    onFetch: (String) -> Unit,
    onSpotifyLogin: (clientId: String) -> Unit,
    onSpotifyLogout: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by rememberSaveable(state.source) { mutableStateOf("") }
    var clientId by rememberSaveable { mutableStateOf(state.spotifyClientId) }
    val spotify = state.source == PlaylistSource.SPOTIFY
    GlassBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("导入歌单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "用本地曲库里已有的歌曲重建一个同名歌单。只读取歌单信息，不下载、不串流音频。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            PillTabs(
                items = PlaylistSource.entries.map { it to it.label },
                selected = state.source,
                onSelect = onSource,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (spotify && !state.spotifyConnected) {
                Text(
                    "Spotify 需要你自己的开发者应用：在 developer.spotify.com 新建 App，Redirect URI 填 ${SpotifyRepository.REDIRECT_URI}，" +
                        "API 选 Web API，然后把 Client ID 粘贴到下面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OlbyineTextField(clientId, { clientId = it }, "Client ID")
                Spacer(Modifier.height(10.dp))
                PillButton("连接 Spotify", { onSpotifyLogin(clientId) }, icon = Icons.Rounded.Person, enabled = clientId.isNotBlank())
            } else {
                OlbyineTextField(input, { input = it }, if (spotify) "Spotify 歌单链接" else "网易云歌单链接或 ID")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (state.loading) "读取中…" else "读取歌单",
                        { onFetch(input) },
                        icon = Icons.Rounded.Search,
                        style = PillStyle.TONAL,
                        enabled = !state.loading && input.isNotBlank(),
                    )
                    if (spotify) PillButton("断开 Spotify", onSpotifyLogout, style = PillStyle.OUTLINED)
                }
            }
            state.message?.let {
                Text(it, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        val playlist = state.playlist
        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { OlbyineSpinner() }
            playlist == null && spotify && state.spotifyPlaylists.isNotEmpty() -> {
                Overline("我的歌单", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    itemsIndexed(state.spotifyPlaylists, key = { index, it -> "$index:${it.id}" }) { _, item ->
                        Row(
                            itemMotion().fillMaxWidth().clickable { onFetch(item.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlImage(item.imageUrl, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.owner} · ${item.trackCount} 首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            playlist != null -> {
                val found = playlist.songs.count { state.matches.containsKey(it.id) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    UrlImage(playlist.coverUrl, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${playlist.creator.ifBlank { "网易云音乐" }} · ${playlist.songs.size} 首 · 本地找到 $found 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    itemsIndexed(playlist.songs, key = { index, it -> "$index:${it.id}" }) { _, song ->
                        val matched = state.matches.containsKey(song.id)
                        Row(itemMotion().fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (matched) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    song.artists.joinToString(" / "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (matched) 1f else .5f),
                                    maxLines = 1,
                                )
                            }
                            Badge(if (matched) "本地" else "未找到", color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, filled = matched)
                        }
                    }
                }
                PillButton(
                    "创建歌单（$found 首）",
                    onConfirm,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    enabled = found > 0,
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}