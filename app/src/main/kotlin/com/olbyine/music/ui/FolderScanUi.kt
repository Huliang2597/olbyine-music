package com.olbyine.music.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.MusicLibraryRepository.ScanFolder
import com.olbyine.music.data.MusicLibraryRepository.ScanTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderScanSheet(
    folders: List<ScanFolder>,
    importing: Boolean,
    loadSubfolders: suspend (Uri, String?) -> List<Pair<String, String>>,
    onAddFolder: () -> Unit,
    onScan: (List<ScanTarget>) -> Unit,
    onDismiss: () -> Unit,
) {
    val wholes = remember { mutableStateMapOf<String, Boolean>() }
    val parts = remember { mutableStateMapOf<String, Map<String, String>>() }
    var expanded by remember { mutableStateOf<String?>(null) }
    var known by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(folders) {
        val keys = folders.map { it.uri.toString() }.toSet()
        known?.let { before -> (keys - before).forEach { wholes[it] = true } }
        known = keys
    }
    val targets = folders.flatMap { folder ->
        val key = folder.uri.toString()
        val chosen = parts[key].orEmpty()
        when {
            chosen.isNotEmpty() -> chosen.map { (id, name) -> ScanTarget(folder.uri, id, "${folder.name.substringAfterLast('/')}/$name") }
            wholes[key] == true -> listOf(ScanTarget(folder.uri, null, folder.name.substringAfterLast('/')))
            else -> emptyList()
        }
    }
    val colors = MaterialTheme.colorScheme
    GlassBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("扫描文件夹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "可以勾选多个文件夹一起扫描；点文件夹右侧的箭头只选其中几个子文件夹。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PillButton("添加文件夹", onAddFolder, icon = Icons.Rounded.CreateNewFolder, style = PillStyle.TONAL, height = 40.dp)
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            if (folders.isEmpty()) {
                item {
                    Text("还没有授权过的文件夹，点「添加文件夹」选择", Modifier.padding(20.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            itemsIndexed(folders, key = { index, it -> "$index:${it.uri}" }) { _, folder ->
                val key = folder.uri.toString()
                val chosen = parts[key].orEmpty()
                val open = expanded == key
                Column(itemMotion()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            if (chosen.isNotEmpty()) parts.remove(key) else wholes[key] = wholes[key] != true
                        }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tick(checked = wholes[key] == true || chosen.isNotEmpty(), partial = chosen.isNotEmpty())
                        Spacer(Modifier.width(12.dp))
                        StyledIcon(Icons.Rounded.Folder, null, Modifier.size(22.dp), tint = colors.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.name.substringAfterLast('/').ifBlank { folder.name }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (chosen.isNotEmpty()) "已选 ${chosen.size} 个子文件夹" else folder.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        GhostIconButton(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, "子文件夹", { expanded = if (open) null else key }, size = 38.dp, iconSize = 20.dp)
                    }
                    AnimatedVisibility(open) {
                        SubfolderList(folder, loadSubfolders, chosen) { id, name, checked ->
                            val next = parts[key].orEmpty().toMutableMap()
                            if (checked) next[id] = name else next.remove(id)
                            if (next.isEmpty()) parts.remove(key) else parts[key] = next
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            PillButton(
                if (importing) "正在导入…" else if (targets.isEmpty()) "请选择文件夹" else "扫描所选（${targets.size} 个）",
                { onScan(targets) },
                icon = Icons.Rounded.Search,
                enabled = targets.isNotEmpty() && !importing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

@Composable
private fun SubfolderList(
    folder: ScanFolder,
    load: suspend (Uri, String?) -> List<Pair<String, String>>,
    chosen: Map<String, String>,
    onToggle: (String, String, Boolean) -> Unit,
) {
    var children by remember(folder.uri) { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(folder.uri) { children = runCatching { load(folder.uri, null) }.getOrDefault(emptyList()) }
    val list = children
    Column(Modifier.fillMaxWidth().padding(start = 40.dp, end = 8.dp, bottom = 6.dp)) {
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.CenterStart) {
                OlbyineSpinner(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            list.isEmpty() -> Text("没有子文件夹", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> list.forEach { (id, name) ->
                val checked = id in chosen
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggle(id, name, !checked) }.padding(horizontal = 6.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tick(checked, partial = false, size = 20)
                    Spacer(Modifier.width(10.dp))
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Tick(checked: Boolean, partial: Boolean, size: Int = 24) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (checked) colors.primary else Color.Transparent)
            .border(1.5.dp, if (checked) colors.primary else colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            if (partial) Box(Modifier.size((size / 2.4f).dp, 2.dp).background(colors.onPrimary))
            else StyledIcon(Icons.Rounded.Check, null, Modifier.size((size * .66f).dp), tint = colors.onPrimary)
        }
    }
}
