package com.olbyine.music.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.OlbyineApplication
import com.olbyine.music.data.LxScript
import com.olbyine.music.data.LxScriptStore
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun LxCustomSourceSection(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as OlbyineApplication
    val store = remember { app.lxScripts }
    var scripts by remember { mutableStateOf(store.all()) }
    var pendingDelete by remember { mutableStateOf<LxScript?>(null) }
    var showUrlImport by remember { mutableStateOf(false) }
    var initializing by remember { mutableStateOf<Set<String>>(emptySet()) }
    val haptics = rememberHaptics()

    fun refresh() {
        scripts = store.all()
    }

    fun initialize(script: LxScript) {
        if (script.id in initializing) return
        initializing = initializing + script.id
        scope.launch {
            val result = runCatching { app.lxRunner.prepare(script) }
            initializing = initializing - script.id
            result.fold(
                onSuccess = { sources ->
                    store.update(script.copy(sources = sources, initialized = true, lastError = "", enabled = true))
                    refresh()
                    haptics.confirm(com.olbyine.music.data.HapticArea.MENUS)
                    onMessage("「${script.name}」初始化成功：" + sources.joinToString("、") { it.name })
                },
                onFailure = { error ->
                    val message = error.message ?: "初始化失败"
                    store.update(script.copy(initialized = false, lastError = message, enabled = false))
                    refresh()
                    onMessage("「${script.name}」初始化失败：$message")
                },
            )
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val code = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (code.isNullOrBlank()) {
            onMessage("读取脚本失败")
            return@rememberLauncherForActivityResult
        }
        if (store.all().size >= 20) {
            onMessage("最多只能导入 20 个自定义源")
            return@rememberLauncherForActivityResult
        }
        val name = LxScriptStore.parseHeader(code, "name").ifBlank {
            uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "未命名源"
        }
        val script = LxScript(
            id = UUID.randomUUID().toString(),
            name = name,
            description = LxScriptStore.parseHeader(code, "description"),
            version = LxScriptStore.parseHeader(code, "version"),
            author = LxScriptStore.parseHeader(code, "author"),
            code = code,
            enabled = false,
            initialized = false,
        )
        store.add(script)
        refresh()
        haptics.light(com.olbyine.music.data.HapticArea.MENUS)
        onMessage("已导入「${script.name}」，请点右侧按钮初始化")
    }

    LinkRow(
        Icons.Rounded.Add,
        "从文件导入自定义源（.js）",
        "洛雪音乐格式的脚本；导入后需要手动初始化才能使用",
    ) { importer.launch(arrayOf("text/plain", "application/javascript", "*/*")) }
    GroupDivider()
    LinkRow(
        Icons.Rounded.Link,
        "从链接导入自定义源",
        "填 http(s) 地址，自动下载脚本；上限 9 MB",
    ) { showUrlImport = true }

    if (scripts.isEmpty()) {
        GroupDivider()
        Text(
            "还没有自定义源。脚本开头需要包含 @name / @description / @version / @author 注释，" +
                "并通过 lx.send(lx.EVENT_NAMES.inited, { status: true, sources: {…} }) 声明支持的源。",
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    scripts.forEach { script ->
        GroupDivider()
        val busy = script.id in initializing
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledIcon(Icons.Rounded.Code, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        script.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            script.description.takeIf { it.isNotBlank() },
                            script.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                            script.author.takeIf { it.isNotBlank() }?.let { "by $it" },
                        ).joinToString(" · ").ifBlank { "没有描述" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                GhostIconButton(
                    if (busy) Icons.Rounded.PlayArrow else Icons.Rounded.PlayArrow,
                    if (script.initialized) "重新初始化" else "初始化",
                    {
                        initialize(script)
                    },
                    size = 38.dp,
                    iconSize = 20.dp,
                    enabled = !busy,
                    tint = if (script.initialized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                OlbyineSwitch(
                    checked = script.enabled,
                    onCheckedChange = { checked ->
                        if (checked && !script.initialized) {
                            initialize(script)
                        } else {
                            store.update(script.copy(enabled = checked))
                            refresh()
                            onMessage(if (checked) "已启用「${script.name}」" else "已停用「${script.name}」")
                        }
                    },
                )
                Spacer(Modifier.width(4.dp))
                GhostIconButton(
                    Icons.Rounded.DeleteOutline,
                    "删除",
                    { pendingDelete = script },
                    size = 38.dp,
                    iconSize = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (busy) "初始化中…" else script.statusText,
                Modifier.padding(start = 34.dp, top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    busy -> MaterialTheme.colorScheme.onSurfaceVariant
                    script.lastError.isNotBlank() -> MaterialTheme.colorScheme.error
                    script.initialized -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }

    if (showUrlImport) {
        LxImportDialog(
            title = "从链接导入自定义源",
            hint = "粘贴脚本的 http(s) 地址，应用会下载并解析 @name 等信息。",
            onDismiss = { showUrlImport = false },
            onText = { text, name ->
                showUrlImport = false
                if (store.all().size >= 20) {
                    onMessage("最多只能导入 20 个自定义源")
                } else {
                    val script = LxScript(
                        id = UUID.randomUUID().toString(),
                        name = LxScriptStore.parseHeader(text, "name").ifBlank { name },
                        description = LxScriptStore.parseHeader(text, "description"),
                        version = LxScriptStore.parseHeader(text, "version"),
                        author = LxScriptStore.parseHeader(text, "author"),
                        code = text,
                        enabled = false,
                        initialized = false,
                    )
                    store.add(script)
                    refresh()
                    onMessage("已导入「${script.name}」，请点右侧按钮初始化")
                }
            },
        )
    }

    pendingDelete?.let { target ->
        OlbyineDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除「${target.name}」？",
            confirmButton = {
                GlassTextButton(onClick = {
                    pendingDelete = null
                    store.remove(target.id)
                    refresh()
                    onMessage("已删除「${target.name}」")
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { GlassTextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        ) {
            Text("只会移除脚本，不影响已下载的歌曲。")
        }
    }
}
