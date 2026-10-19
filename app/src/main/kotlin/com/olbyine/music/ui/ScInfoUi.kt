package com.olbyine.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.ScTrackInfo
import com.olbyine.music.data.ScUser
import com.olbyine.music.data.SoundCloudClient

private val ScAccent = Color(0xFFFF5500)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScTrackInfoPage(
    client: SoundCloudClient,
    trackId: Long,
    progress: () -> Float,
    onOpenUser: (ScUser) -> Unit,
    onComments: () -> Unit,
) {
    val loaded by produceState<Result<Pair<ScTrackInfo, ScUser>>?>(null, trackId) { value = runCatching { client.trackInfo(trackId) } }
    val ink = MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    androidx.compose.foundation.text.selection.SelectionContainer { Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 38.dp, height = 5.dp).clip(CircleShape).background(ink.copy(alpha = .32f)))
        }
        Spacer(Modifier.height(18.dp))
        val result = loaded
        val data = result?.getOrNull()
        if (result == null) {
            Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { OlbyineSpinner(color = ink.copy(alpha = .7f)) }
            return@Column
        }
        if (data == null) {
            Text(result.exceptionOrNull()?.message ?: "加载失败", Modifier.padding(top = 120.dp), color = ink.copy(alpha = .6f), textAlign = TextAlign.Center)
            return@Column
        }
        val (info, user) = data
        Overline("创作者", color = ink.copy(alpha = .6f), modifier = Modifier.rise(progress, 0))
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.rise(progress, 1, scaleFrom = .82f).size(156.dp)
                .shadow(28.dp, CircleShape, ambientColor = ScAccent, spotColor = ScAccent)
                .clip(CircleShape).background(Color.White.copy(alpha = .08f))
                .clickable { onOpenUser(user) },
        ) {
            UrlImage(user.avatarUrl, Modifier.fillMaxSize()) {
                StyledIcon(Icons.Rounded.Person, null, Modifier.padding(40.dp), tint = ink.copy(alpha = .5f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(user.username, Modifier.rise(progress, 2), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink, textAlign = TextAlign.Center)
        Text(
            listOfNotNull(
                "${playCountLabel(user.followers.toLong())} 粉丝",
                "${user.trackCount} 首曲目",
                user.city.takeIf(String::isNotBlank),
            ).joinToString(" · "),
            Modifier.padding(top = 4.dp).rise(progress, 2),
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = .6f),
        )
        Spacer(Modifier.height(14.dp))
        InfoCard(Modifier.rise(progress, 3)) {
            ExpandableText(user.description.ifBlank { "这位创作者还没有写简介。" }, collapsedLines = 6, muted = user.description.isBlank())
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("查看主页", Icons.Rounded.Person) { onOpenUser(user) }
            }
        }

        Spacer(Modifier.height(30.dp))
        Overline("曲目简介", color = ink.copy(alpha = .6f), modifier = Modifier.rise(progress, 4))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().rise(progress, 5), verticalAlignment = Alignment.CenterVertically) {
            UrlImage(info.track.artworkUrl ?: user.avatarUrl, Modifier.size(104.dp).shadow(16.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(info.track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "▶ " + playCountLabel(info.track.playCount),
                        "♥ " + playCountLabel(info.track.likeCount),
                        "⟲ " + playCountLabel(info.reposts),
                        "💬 " + playCountLabel(info.track.commentCount),
                    ).joinToString("  "),
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = .6f),
                )
                val date = (info.releaseDate.ifBlank { info.createdAt }).take(10).replace('/', '-')
                if (date.isNotBlank() || info.label.isNotBlank()) {
                    Text(
                        listOfNotNull(date.takeIf(String::isNotBlank), info.label.takeIf(String::isNotBlank)).joinToString(" · "),
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = ink.copy(alpha = .55f),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        InfoCard(Modifier.rise(progress, 6)) {
            ExpandableText(info.description.ifBlank { "创作者没有为这首曲目写介绍。" }, collapsedLines = 8, muted = info.description.isBlank())
            if (info.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    info.tags.take(16).forEach { tag ->
                        Text("#$tag", Modifier.clip(CircleShape).background(ink.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .85f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("评论", Icons.Rounded.ChatBubbleOutline, onClick = onComments)
                if (info.permalink.isNotBlank()) SmallChip("在 SoundCloud 打开", Icons.AutoMirrored.Rounded.OpenInNew) { openExternal(context, info.permalink) }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("向左滑动返回播放页", Modifier.rise(progress, 7), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = .4f))
        Spacer(Modifier.height(28.dp))
    } }
}
