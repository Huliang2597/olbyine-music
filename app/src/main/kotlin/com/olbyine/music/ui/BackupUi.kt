package com.olbyine.music.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olbyine.music.MainViewModel
import com.olbyine.music.data.WebDavRepository
import com.olbyine.music.data.WebDavServer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackupPage(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val state by viewModel.backupState.collectAsState()
    val webdav = remember { WebDavRepository(context) }
    var baseUrl by remember { mutableStateOf(webdav.servers.value.firstOrNull()?.baseUrl ?: "") }
    var username by remember { mutableStateOf(webdav.servers.value.firstOrNull()?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var restoreDialog by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.backupJson().toByteArray(Charsets.UTF_8)) }
        }.onSuccess {
            haptics.light(com.olbyine.music.data.HapticArea.MENUS)
            viewModel.showMessage("已导出备份文件")
        }.onFailure { viewModel.showMessage("导出失败：${it.message ?: "无法写入"}") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            viewModel.showMessage("读取备份文件失败")
            return@rememberLauncherForActivityResult
        }
        restoreDialog = text
    }

    state.message?.let { text ->
        Text(
            text,
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (text.contains("失败") || text.contains("不对")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    SettingsGroup("导出与恢复") {
        LinkRow(Icons.Rounded.FileDownload, "导出为 JSON 文件", "把配置、登录信息、下载记录等保存到手机任意位置") {
            exportLauncher.launch("olbyine-backup.json")
        }
        GroupDivider()
        LinkRow(Icons.Rounded.Restore, "从 JSON 文件恢复", "选择之前导出的备份文件，覆盖当前数据") {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }
    SettingsGroup("WebDAV") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            BackupField("服务器地址", baseUrl, "https://dav.jianguoyun.com/dav/") { baseUrl = it }
            Spacer(Modifier.height(10.dp))
            BackupField("用户名", username, "通常是邮箱") { username = it }
            Spacer(Modifier.height(10.dp))
            BackupField("密码 / 应用密码", password, "留空则用已保存的密码", secret = true) { password = it }
        }
        GroupDivider()
        LinkRow(
            Icons.Rounded.CloudUpload,
            if (state.busy) "正在处理…" else "备份到 WebDAV",
            "上传 ${com.olbyine.music.data.BackupRepository.FILE_NAME} 到上面的地址",
        ) {
            if (state.busy) return@LinkRow
            val url = baseUrl.trim()
            if (url.isBlank()) {
                viewModel.showMessage("先填 WebDAV 地址")
                return@LinkRow
            }
            val server = WebDavServer(
                id = "",
                name = "",
                baseUrl = url,
                username = username.trim(),
            )
            haptics.tick(com.olbyine.music.data.HapticArea.MENUS)
            viewModel.uploadBackup(server, password.trim())
        }
        GroupDivider()
        LinkRow(
            Icons.Rounded.Restore,
            "从 WebDAV 恢复",
            "下载 ${com.olbyine.music.data.BackupRepository.FILE_NAME} 并覆盖当前数据",
        ) {
            if (state.busy) return@LinkRow
            val url = baseUrl.trim()
            if (url.isBlank()) {
                viewModel.showMessage("先填 WebDAV 地址")
                return@LinkRow
            }
            val server = WebDavServer(id = "", name = "", baseUrl = url, username = username.trim())
            haptics.tick(com.olbyine.music.data.HapticArea.MENUS)
            viewModel.restoreBackupFromCloud(server, password.trim()) { viewModel.reloadSettings() }
        }
    }
    Text(
        "备份内容：软件设置、网易云 / SoundCloud / Spotify / Navidrome / WebDAV 的登录凭据与 Cookie、下载记录、播放来源等。" +
            "文件是明文 JSON，请放在自己信任的 WebDAV 目录里。",
        Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.lastBackupAt > 0L || state.lastRestoreAt > 0L) {
        val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
        Text(
            buildString {
                if (state.lastBackupAt > 0L) append("上次备份：${fmt.format(Date(state.lastBackupAt))}")
                if (state.lastRestoreAt > 0L) {
                    if (isNotEmpty()) append("　")
                    append("上次恢复：${fmt.format(Date(state.lastRestoreAt))}")
                }
            },
            Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    restoreDialog?.let { text ->
        val summary = remember(text) { viewModel.summaryOf(text) }
        OlbyineDialog(
            onDismissRequest = { restoreDialog = null },
            title = "恢复备份？",
            confirmButton = {
                GlassTextButton(onClick = {
                    val payload = text
                    restoreDialog = null
                    viewModel.restoreBackup(payload) { viewModel.reloadSettings() }
                }) { Text("覆盖恢复") }
            },
            dismissButton = { GlassTextButton(onClick = { restoreDialog = null }) { Text("取消") } },
        ) {
            Column {
                if (summary == null) {
                    Text("这个文件不是有效的 Olbyine 备份。", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("备份时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(summary.createdAt)))
                    Spacer(Modifier.height(6.dp))
                    Text("包含 ${summary.entries} 项，${summary.keys.size} 组数据。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        summary.keys.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "恢复会覆盖当前的设置和登录信息，建议先导出一次当前数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupField(
    label: String,
    value: String,
    placeholder: String,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
    )
}
