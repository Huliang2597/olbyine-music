package com.olbyine.music.ui

import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as PlatformIcon
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.olbyine.music.BuildConfig
import com.olbyine.music.MainActivity
import com.olbyine.music.MainUiState
import com.olbyine.music.MainViewModel
import com.olbyine.music.OutputMode
import com.olbyine.music.R
import com.olbyine.music.data.AppleMusicSettings
import com.olbyine.music.data.EqBands
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.ThemeMode
import com.olbyine.music.data.TransitionMode
import com.olbyine.music.data.UiStyle
import com.olbyine.music.data.UsbVolumeMode
import com.olbyine.music.outputMode
import com.olbyine.music.playback.LyricsWidgetProvider
import com.olbyine.music.playback.UsbBitPerfectStatus
import com.olbyine.music.playback.usb.UsbDriverState
import com.olbyine.music.playback.usb.UsbDriverStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private enum class SettingsPage(val title: String, val subtitle: String, val icon: ImageVector) {
    APPEARANCE("外观与风格", "界面风格、主题、玻璃材质、背景与应用图标", Icons.Rounded.Palette),
    PLAYER("播放页", "动态封面、律动背景、玻璃按钮", Icons.Rounded.PlayCircle),
    LYRICS("歌词", "字号字重、时间偏移、高亮位置、自动匹配", Icons.Rounded.Lyrics),
    LYRIC_OUTPUT("歌词外显", "通知栏、状态栏、桌面小组件与车载歌词", Icons.Rounded.Notifications),
    TRANSITION("歌曲过渡", "像 Apple Music 一样自动衔接下一首", Icons.Rounded.SwapHoriz),
    AUDIO("音频输出", "输出模式、USB 独占驱动与位完美", Icons.Rounded.Usb),
    EQUALIZER("均衡器", "10 段均衡、预设、声道平衡", Icons.Rounded.Equalizer),
    LIBRARY("曲库与导入", "过滤视频和短音频、去重、自动补全信息", Icons.Rounded.LibraryMusic),
    ONLINE("在线服务", "网易云账号、Apple Music、WebDAV、监听其他播放器", Icons.Rounded.Cloud),
    HAPTICS("触感反馈", "选择哪些操作带震动", Icons.Rounded.Vibration),
    ABOUT("关于", "版本与开源许可", Icons.Rounded.Info),
}

private val SettingsSections = listOf(
    "界面" to listOf(SettingsPage.APPEARANCE, SettingsPage.PLAYER, SettingsPage.HAPTICS),
    "歌词" to listOf(SettingsPage.LYRICS, SettingsPage.LYRIC_OUTPUT),
    "声音" to listOf(SettingsPage.AUDIO, SettingsPage.EQUALIZER, SettingsPage.TRANSITION),
    "音乐" to listOf(SettingsPage.LIBRARY, SettingsPage.ONLINE),
    "" to listOf(SettingsPage.ABOUT),
)

@Composable
internal fun SettingsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    bottomContentPadding: Dp,
    onImportFont: () -> Unit,
    onPickBackground: () -> Unit,
    onExternalLyrics: () -> Unit,
    onNeteaseLogin: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val homeScroll = rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) }
    val haptics = rememberHaptics()
    androidx.compose.runtime.key(page) {
        BackHandler(enabled = page != null && state.destination == com.olbyine.music.AppDestination.SETTINGS) { page = null }
    }
    val material = LocalStyleSpec.current.style == UiStyle.MATERIAL
    val axis = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.roundToPx() }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            if (material) {
                (slideInHorizontally(tween(300, easing = OlbyineTokens.Motion.easeInOut)) { if (forward) axis else -axis } + fadeIn(tween(210, delayMillis = 90)))
                    .togetherWith(slideOutHorizontally(tween(300, easing = OlbyineTokens.Motion.easeInOut)) { if (forward) -axis else axis } + fadeOut(tween(90)))
            } else {
                (slideInHorizontally(tween(340, easing = OlbyineTokens.Motion.drawer)) { if (forward) it else -it / 4 } + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(tween(340, easing = OlbyineTokens.Motion.drawer)) { if (forward) -it / 4 else it } + fadeOut(tween(220)))
            }
        },
        label = "settings-page",
    ) { shown ->
        if (shown == null) {
            SettingsHome(state, bottomContentPadding, homeScroll) {
                haptics.light(HapticArea.MENUS)
                page = it
            }
        } else {
            SettingsSubPage(shown.title, bottomContentPadding, onBack = { page = null }) {
                when (shown) {
                    SettingsPage.APPEARANCE -> AppearancePage(state, viewModel, onPickBackground)
                    SettingsPage.PLAYER -> PlayerSettingsPage(state, viewModel)
                    SettingsPage.LYRICS -> LyricsSettingsPage(state, viewModel, onImportFont)
                    SettingsPage.LYRIC_OUTPUT -> LyricOutputPage(state, viewModel)
                    SettingsPage.TRANSITION -> TransitionPage(state, viewModel)
                    SettingsPage.AUDIO -> AudioPage(state, viewModel)
                    SettingsPage.EQUALIZER -> EqualizerPage(state, viewModel)
                    SettingsPage.LIBRARY -> LibrarySettingsPage(state, viewModel)
                    SettingsPage.ONLINE -> OnlineSettingsPage(state, viewModel, onExternalLyrics, onNeteaseLogin)
                    SettingsPage.HAPTICS -> HapticsPage(state, viewModel)
                    SettingsPage.ABOUT -> AboutPage(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(state: MainUiState, bottomContentPadding: Dp, scroll: androidx.compose.foundation.ScrollState, onOpen: (SettingsPage) -> Unit) {
    val source = rememberBackdropSource()
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    Box(Modifier.fillMaxSize().then(groupedPage())) {
        Column(
            Modifier.fillMaxSize()
                .backdropSource(source)
                .verticalScroll(scroll)
                .padding(bottom = bottomContentPadding + 28.dp),
        ) {
            ScreenHeader("设置", "Olbyine-X ${BuildConfig.VERSION_NAME}")
            StyleCard(state) { onOpen(SettingsPage.APPEARANCE) }
            val compact = LocalStyleSpec.current.style == UiStyle.IOS
            SettingsSections.forEach { (title, pages) ->
                SettingsGroup(title) {
                    pages.forEachIndexed { index, page ->
                        if (index > 0) GroupDivider()
                        NavRow(page.icon, page.title, page.subtitle, badge = badgeFor(page, state), compact = compact) { onOpen(page) }
                    }
                }
            }
        }
        CompactTitleBar("设置", source, scroll, statusBarPx)
    }
}

private fun badgeFor(page: SettingsPage, state: MainUiState): String? {
    val settings = state.settings
    return when (page) {
        SettingsPage.EQUALIZER -> if (settings.eqEnabled) EqBands.label(settings.eqPreset) else "关"
        SettingsPage.TRANSITION -> settings.transitionMode.label
        SettingsPage.AUDIO -> settings.outputMode().label
        SettingsPage.APPEARANCE -> settings.uiStyle.label
        else -> null
    }
}

@Composable
private fun SettingsSubPage(title: String, bottomContentPadding: Dp, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val source = rememberBackdropSource()
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    Box(Modifier.fillMaxSize().then(groupedPage())) {
        Column(
            Modifier.fillMaxSize()
                .backdropSource(source)
                .verticalScroll(scroll)
                .padding(bottom = bottomContentPadding + 28.dp),
        ) {
            Spacer(Modifier.statusBarsPadding().height(56.dp))
            Text(
                title,
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            content()
        }
        CompactTitleBar(title, source, scroll, statusBarPx, onBack = onBack)
    }
}

@Composable
private fun CompactTitleBar(
    title: String,
    source: BackdropSource,
    scroll: androidx.compose.foundation.ScrollState,
    statusBarPx: Int,
    onBack: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    val barAlpha by androidx.compose.runtime.rememberUpdatedState(rememberBarAlpha(28.dp) { scroll.value.toFloat() })
    val dynamics = remember(scroll) { GlassDynamics(materialAlpha = { barAlpha() }) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { statusBarPx.toDp() } + 52.dp + BarEdge)
            .glass(source, frostedBarStyle(), dynamics = dynamics),
    ) {
        if (onBack != null) {
            GhostIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                "返回",
                onBack,
                Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp + BarEdge),
            )
        }
        Text(
            title,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp + BarEdge)
                .graphicsLayer { alpha = ((scroll.value - threshold * .6f) / (threshold * .6f)).coerceIn(0f, 1f) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun AppearancePage(state: MainUiState, viewModel: MainViewModel, onPickBackground: () -> Unit) {
    val settings = state.settings
    SettingsGroup("界面风格") { StylePicker(settings.uiStyle, viewModel::setUiStyle) }
    SettingsGroup("布局") {
        ChoiceRow(
            Icons.Rounded.TabletAndroid, "布局模式", "平板：iPad 式侧边栏导航，横屏时播放页左边控制、右边歌词；手机竖屏也可开启（侧边栏收为图标栏）。自动：窗口够宽（平板、手机横屏）时用平板布局",
            listOf("auto" to "自动", "phone" to "手机", "tablet" to "平板"), settings.layoutMode, viewModel::setLayoutMode,
        )
        GroupDivider()
        ChoiceRow(
            Icons.Rounded.ViewCarousel, "专辑样式", "网格；Cover Flow：封面叠成一摞放在毛玻璃上，滑动翻页；球形：专辑铺在一个可以拖着转的球面上，点正对你的封面打开。单曲列表的「磁贴墙」视图在音乐库右上角的分类与排序菜单里",
            listOf("grid" to "网格", "coverflow" to "Cover Flow", "sphere" to "球形"), settings.albumStyle, viewModel::setAlbumStyle,
        )
    }
    SettingsGroup("主题") {
        ThemePicker(settings.themeMode, viewModel::setThemeMode)
        androidx.compose.animation.AnimatedVisibility(
            visible = settings.uiStyle == UiStyle.MATERIAL,
            enter = fadeIn(tween(OlbyineTokens.Motion.standardMs)) + expandVertically(tween(240, easing = OlbyineTokens.Motion.easeOut)),
            exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)) + shrinkVertically(tween(200, easing = OlbyineTokens.Motion.easeOut)),
        ) {
            Column {
                GroupDivider()
                ColorSourcePicker(settings.dynamicAccentEnabled, viewModel::setDynamicAccentEnabled)
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = settings.uiStyle != UiStyle.MATERIAL,
            enter = fadeIn(tween(OlbyineTokens.Motion.standardMs)) + expandVertically(tween(240, easing = OlbyineTokens.Motion.easeOut)),
            exit = fadeOut(tween(OlbyineTokens.Motion.quickMs)) + shrinkVertically(tween(200, easing = OlbyineTokens.Motion.easeOut)),
        ) {
            Column {
                GroupDivider()
                Text(
                    "${settings.uiStyle.label} 风格使用该系统自己的配色；莫奈取色只在「原生 Android」风格下生效",
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    SettingsGroup("材质") {
        SettingSwitch(Icons.Rounded.BlurOn, "液态玻璃", "底栏与迷你播放器使用透镜折射、色散和高光；关闭时为毛玻璃",
            settings.liquidGlassEnabled, viewModel::setLiquidGlassEnabled)
        GroupDivider()
        SettingSwitch(Icons.Rounded.Layers, "液态玻璃小弹窗", "菜单与小弹窗使用液态玻璃；关闭时为毛玻璃",
            settings.glassPopups, viewModel::setGlassPopups)
        GroupDivider()
        SettingSwitch(Icons.Rounded.Wallpaper, "封面毛玻璃背景", "音乐库、网易云和设置的背景使用正在播放歌曲封面的高斯模糊，卡片半透明",
            settings.artworkBackdrop, viewModel::setArtworkBackdrop)
    }
    SettingsGroup("玻璃参数") {
        GlassTuningEditor(settings.glassTuning) { t -> viewModel.setGlassTuning(t.highlight, t.blur, t.opacity, t.refraction, t.gyro) }
    }
    SettingsGroup("自定义背景") {
        BackgroundRow(settings.customBackgroundPath, onPickBackground, viewModel::clearCustomBackground)
        if (settings.customBackgroundPath != null) {
            SliderRow("模糊", { "${(it * 100).roundToInt()}%" }, settings.customBackgroundBlur, 0f..1f, onValue = viewModel::setCustomBackgroundBlur)
            SliderRow("压暗", { "${(it * 100).roundToInt()}%" }, settings.customBackgroundDim, 0f..0.8f, onValue = viewModel::setCustomBackgroundDim)
            Spacer(Modifier.height(6.dp))
            GroupDivider()
            SettingSwitch(Icons.Rounded.Wallpaper, "用于播放页", "以这张图代替播放页的流动封面背景",
                settings.customBackgroundOnPlayer, viewModel::setCustomBackgroundOnPlayer)
        }
    }
    SettingsGroup("应用图标") { AppIconPicker() }
}

@Composable
private fun PlayerSettingsPage(state: MainUiState, viewModel: MainViewModel) {
    val settings = state.settings
    SettingsGroup("封面与背景") {
        ChoiceRow(
            Icons.Rounded.Wallpaper, "背景样式", "Apple Music 流光：iOS 版的多层旋转封面 + 大半径模糊（参考 Lyricify-Backgrounds）；AMLL 流体：网页版 AMLL 的流动网格",
            listOf("ios" to "Apple Music 流光", "amll" to "AMLL 流体"), settings.backgroundStyle, viewModel::setBackgroundStyle,
        )
        GroupDivider()
        SettingSwitch(Icons.Rounded.Animation, "动态封面", "有 Apple Music 动态封面的专辑，封面会动起来（需要联网，首次加载后缓存）",
            settings.motionCover, viewModel::setMotionCover)
        GroupDivider()
        SettingSwitch(Icons.Rounded.Wallpaper, "动态背景", "用 Apple Music 的竖版动态封面铺满播放页背景（更耗电）",
            settings.motionBackground, viewModel::setMotionBackground)
        GroupDivider()
        SettingSwitch(Icons.Rounded.GraphicEq, "背景随节奏律动", "流动背景跟着低音轻微起伏、加速，像 Apple Music 一样",
            settings.beatPulse, viewModel::setBeatPulse)
    }
    SettingsGroup("控件") {
        SettingSwitch(Icons.AutoMirrored.Rounded.ShortText, "播放页歌词预览", "封面下方显示逐字滚动的当前歌词，点按进入全屏歌词",
            settings.showLyricPreview, viewModel::setLyricPreviewEnabled)
        GroupDivider()
        SettingSwitch(Icons.Rounded.RadioButtonChecked, "玻璃按钮", "播放、切歌与模式按钮下方加一层液态玻璃",
            settings.playerGlassButtons, viewModel::setPlayerGlassButtons)
        GroupDivider()
        SettingSwitch(Icons.Rounded.Headphones, "显示本地/监听切换", "播放页底部的切换；播放网易云 / SoundCloud 歌曲时会自动隐藏",
            settings.showSourceSwitch, viewModel::setShowSourceSwitch)
    }
    SettingsGroup("特效（来自 React Bits）") {
        com.olbyine.music.data.PlayerFx.labels.forEachIndexed { index, (bit, title, description) ->
            if (index > 0) GroupDivider()
            val icon = when (bit) {
                com.olbyine.music.data.PlayerFx.TITLE_REVEAL -> Icons.Rounded.BlurOn
                com.olbyine.music.data.PlayerFx.HEART_BURST -> Icons.Rounded.AutoAwesome
                com.olbyine.music.data.PlayerFx.COVER_TILT -> Icons.Rounded.Image
                com.olbyine.music.data.PlayerFx.ELASTIC_SEEK -> Icons.Rounded.Tune
                com.olbyine.music.data.PlayerFx.ROLLING_TIME -> Icons.Rounded.Timer
                com.olbyine.music.data.PlayerFx.BADGE_SHINE -> Icons.Rounded.HighQuality
                else -> Icons.Rounded.Wallpaper
            }
            SettingSwitch(icon, title, description, settings.playerFx and bit != 0) { viewModel.setPlayerFx(bit, it) }
        }
    }
}

@Composable
private fun LyricsSettingsPage(state: MainUiState, viewModel: MainViewModel, onImportFont: () -> Unit) {
    val settings = state.settings
    SettingsGroup("显示") {
        SliderRow("歌词字号", { "${it.roundToInt()} sp" }, settings.lyricFontSize, 20f..48f, steps = 27, onValue = viewModel::setLyricFontSize)
        SliderRow("歌词字重", { it.roundToInt().toString() }, settings.lyricFontWeight.toFloat(), 100f..900f, steps = 7) {
            viewModel.setLyricFontWeight(it.roundToInt())
        }
        SliderRow("未播放行透明度", { "${(it * 100).roundToInt()}%" }, settings.lyricInactiveOpacity, 0.16f..0.72f, onValue = viewModel::setLyricOpacity)
        OffsetRow(settings.lyricOffsetMs, viewModel::setLyricOffset)
        AlignRow(settings.lyricAlignPosition, viewModel::setLyricAlignPosition)
        GroupDivider()
        SettingSwitch(Icons.Rounded.BlurOn, "歌词景深模糊", "非当前行逐渐模糊，像 Apple Music 一样（更耗电，逐字歌词可能更卡）",
            settings.lyricBlur, viewModel::setLyricBlur)
        GroupDivider()
        SettingSwitch(Icons.Rounded.Translate, "显示翻译", "歌词带有翻译时，在每行下方显示",
            settings.lyricTranslation, viewModel::setLyricTranslation)
        GroupDivider()
        SettingSwitch(Icons.Rounded.Abc, "显示罗马音", "歌词带有罗马音（读音）时，在每行下方显示",
            settings.lyricRomanization, viewModel::setLyricRomanization)
        SliderRow("翻译与罗马音字号", { "${(it * 100).roundToInt()}%" }, settings.lyricSubScale, .3f..1f, steps = 13,
            onValue = viewModel::setLyricSubScale)
        Spacer(Modifier.height(10.dp))
        GroupDivider()
        SettingSwitch(Icons.Rounded.TouchApp, "双击歌词跳转", "开启后需连续点按同一行两次才会跳到该行，避免滑动时误触；关闭则单击即跳转",
            settings.lyricDoubleTapSeek, viewModel::setLyricDoubleTapSeek)
        GroupDivider()
        FontRow(settings.lyricFontPath != null, onImportFont, viewModel::clearCustomFont)
    }
    SettingsGroup("在线歌词") {
        SettingSwitch(Icons.Rounded.CloudSync, "自动匹配在线歌词", "导入歌曲后在后台自动查找逐字歌词（QQ 音乐、网易云、酷狗、Apple Music、AMLL 等同时搜索）",
            settings.automaticLyricsEnabled, viewModel::setAutomaticLyricsEnabled)
    }
}

@Composable
private fun LyricOutputPage(state: MainUiState, viewModel: MainViewModel) {
    val settings = state.settings
    val context = LocalContext.current
    SettingsGroup("通知与状态栏") {
        SettingSwitch(Icons.Rounded.Notifications, "通知栏歌词", "媒体通知和锁屏卡片的副标题显示当前歌词",
            settings.notificationLyrics, viewModel::setNotificationLyrics)
        GroupDivider()
        SettingSwitch(Icons.Rounded.Lyrics, "状态栏歌词",
            "Android 16 以「实况通知」胶囊显示当前歌词（ColorOS 流体云 / 超级岛等会接管显示）；同时兼容魅族 Flyme 状态栏歌词与 StatusBarLyric、Lyricon 等模块",
            settings.statusBarLyricsEnabled, viewModel::setStatusBarLyricsEnabled)
        if (settings.statusBarLyricsEnabled && Build.VERSION.SDK_INT >= 36) {
            LinkRow(Icons.Rounded.Tune, "允许实况通知", "如果状态栏没有出现歌词胶囊，请在这里打开 Olbyine 的「实况通知」权限") {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        GroupDivider()
        SettingSwitch(Icons.Rounded.DirectionsCar, "车载 / 蓝牙歌词", "把当前歌词作为歌曲标题发送给车机和蓝牙设备（这些设备通常只显示标题）",
            settings.bluetoothLyrics, viewModel::setBluetoothLyrics)
    }
    SettingsGroup("桌面歌词") {
        SettingSwitch(Icons.Rounded.PictureInPicture, "桌面歌词", "在其他应用上方显示悬浮歌词：可拖动、双指缩放、拖角改宽度，点按显示播放控制；回到 Olbyine 时自动隐藏",
            settings.floatingLyrics) { on ->
            viewModel.setFloatingLyrics(on)
            if (on && !com.olbyine.music.playback.FloatingLyrics.canDraw(context)) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        if (settings.floatingLyrics) {
            GroupDivider()
            RadioRow("逐字歌词", "只显示当前一句，随演唱逐字点亮；下方是翻译或下一句", settings.floatingLyricsMode != "scroll") {
                viewModel.setFloatingLyricsMode("karaoke")
            }
            RadioRow("滚动歌词", "上一句、当前句和下一句，换行时平滑滚动", settings.floatingLyricsMode == "scroll") {
                viewModel.setFloatingLyricsMode("scroll")
            }
            GroupDivider()
            FloatingColorRow(settings.floatingLyricsColor, viewModel::setFloatingLyricsColor)
            GroupDivider()
            SettingSwitch(Icons.Rounded.Lock, "锁定桌面歌词", "锁定后不响应触摸，点击会直接穿透到下面的应用；在这里关闭即可解锁",
                settings.floatingLyricsLocked, viewModel::setFloatingLyricsLocked)
        }
    }
    var widgetHelp by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    SettingsGroup("桌面小组件") {
        LinkRow(Icons.Rounded.Widgets, "添加歌词小组件", "封面、逐字高亮的当前歌词、下一句和播放控制；长按桌面空白处也能在小组件列表里找到「Olbyine 歌词」") {
            val manager = AppWidgetManager.getInstance(context)
            val requested = manager.isRequestPinAppWidgetSupported && runCatching {
                manager.requestPinAppWidget(
                    ComponentName(context, LyricsWidgetProvider::class.java),
                    android.os.Bundle().apply {
                        putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, android.widget.RemoteViews(context.packageName, com.olbyine.music.R.layout.widget_lyrics))
                    },
                    com.olbyine.music.playback.WidgetActionReceiver.pinnedCallback(context),
                )
            }.getOrDefault(false)
            widgetHelp = if (requested) 1 else 2
        }
    }
    if (widgetHelp == 1) {
        LaunchedEffect(Unit) {
            var left = false
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) left = true
            }
            lifecycle.addObserver(observer)
            try {
                kotlinx.coroutines.delay(1800)
            } finally {
                lifecycle.removeObserver(observer)
            }
            widgetHelp = if (left || com.olbyine.music.playback.WidgetActionReceiver.pinnedRecently()) 0 else 2
        }
    }
    if (widgetHelp == 2) {
        OlbyineDialog(
            onDismissRequest = { widgetHelp = 0 },
            title = "手动添加歌词小组件",
            confirmButton = {
                GlassTextButton(onClick = {
                    widgetHelp = 0
                    openAppPermissions(context)
                }) { Text("打开权限设置") }
            },
            dismissButton = { GlassTextButton(onClick = { widgetHelp = 0 }) { Text("知道了") } },
        ) {
            Text(
                "系统没有弹出添加窗口。\n\n" +
                    "· ColorOS / 澎湃 OS：在「应用权限」里允许 Olbyine 的「桌面快捷方式」，再点一次「添加歌词小组件」。\n" +
                    "· 也可以回到桌面，长按空白处（或双指捏合）→ 小组件 / 卡片，搜索「Olbyine」，把「Olbyine 歌词」拖到桌面。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun openAppPermissions(context: android.content.Context) {
    val pkg = context.packageName
    val candidates = listOf(
        Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", pkg),
        Intent("android.settings.APPLICATION_DETAILS_SETTINGS").setData(android.net.Uri.fromParts("package", pkg, null))
            .setPackage("com.oplus.securitypermission"),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.fromParts("package", pkg, null)),
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

@Composable
private fun TransitionPage(state: MainUiState, viewModel: MainViewModel) {
    val settings = state.settings
    SettingsGroup("过渡方式") {
        TransitionMode.entries.forEachIndexed { index, mode ->
            if (index > 0) GroupDivider()
            RadioRow(mode.label, mode.description, selected = settings.transitionMode == mode) { viewModel.setTransitionMode(mode) }
        }
    }
    AnimatedVisibility(settings.transitionMode == TransitionMode.CROSSFADE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup("淡入淡出") {
            SliderRow("过渡时长", { "${it.roundToInt()} 秒" }, settings.crossfadeSeconds.toFloat(), 1f..12f, steps = 10) {
                viewModel.setCrossfadeSeconds(it.roundToInt())
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    Note(
        "过渡时界面会立即切到下一首（和 Apple Music 一样），上一首的结尾在后台淡出。" +
            "开启 USB 独占驱动时 DAC 只能接收一路音频，过渡会自动停用；单曲循环时也不会过渡。也可以在播放页底栏随时切换。",
    )
}

@Composable
private fun AudioPage(state: MainUiState, viewModel: MainViewModel) {
    val settings = state.settings
    val usbDriver by viewModel.usbDriverState.collectAsState()
    val mode = settings.outputMode()
    SettingsGroup("输出模式") {
        OutputMode.entries.forEachIndexed { index, option ->
            if (index > 0) GroupDivider()
            RadioRow(option.label, option.description, selected = mode == option) { viewModel.setOutputMode(option) }
        }
    }
    AnimatedVisibility(mode == OutputMode.USB_DRIVER, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            SettingsGroup("USB 独占驱动") {
                UsbDriverRow(usbDriver, viewModel::retryUsbDriver)
                if (usbDriver.status == UsbDriverStatus.READY || usbDriver.status == UsbDriverStatus.STREAMING) {
                    if (settings.usbVolumeMode != UsbVolumeMode.FIXED) {
                        SliderRow(
                            if (usbDriver.hardwareVolume) "DAC 音量（硬件）" else "DAC 音量（软件）",
                            { "${it.roundToInt()}%" },
                            usbDriver.volume.toFloat(),
                            0f..settings.usbMaxVolume.toFloat(),
                            onValue = { viewModel.setUsbVolume(it.roundToInt()) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            SettingsGroup("高级（参考 Poweramp）") {
                ChoiceRow(
                    Icons.Rounded.HighQuality, "输出位深", "DAC 支持的位深中优先使用；自动 = 能用的最高位深",
                    listOf(0 to "自动", 16 to "16 位", 24 to "24 位", 32 to "32 位"), settings.usbBitDepth, viewModel::setUsbBitDepth,
                )
                GroupDivider()
                SettingSwitch(Icons.Rounded.GraphicEq, "降位深抖动", "需要把 24/32 位音源降到更低位深时加入 TPDF 抖动，避免细节失真",
                    settings.usbDither, viewModel::setUsbDither)
                GroupDivider()
                ChoiceRow(
                    Icons.Rounded.Speaker, "音量控制", "固定满音量时由耳放 / DAC 自身旋钮控制音量，数据完全位完美",
                    UsbVolumeMode.entries.map { it to it.label }, settings.usbVolumeMode, viewModel::setUsbVolumeMode,
                )
                SliderRow("音量上限", { "${it.roundToInt()}%" }, settings.usbMaxVolume.toFloat(), 10f..100f, steps = 17) {
                    viewModel.setUsbMaxVolume(it.roundToInt())
                }
                SliderRow("缓冲长度", { "${it.roundToInt()} ms" }, settings.usbBufferMs.toFloat(), 80f..1000f, steps = 22) {
                    viewModel.setUsbBufferMs(it.roundToInt())
                }
                Note("缓冲越短，拖动和暂停越跟手；偶尔爆音时调长。改动在下一首歌开始时生效。", inset = true)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
    SettingsGroup("系统 USB 输出") {
        SettingSwitch(Icons.Rounded.Usb, "USB 位完美（系统）", "Android 14+ 的系统级 USB 独占：插入 DAC 时按歌曲原始采样率与位深输出（不使用 Olbyine 驱动时）",
            settings.usbBitPerfectEnabled, viewModel::setUsbBitPerfectEnabled)
        GroupDivider()
        UsbStatusRow(state)
    }
    if (settings.eqEnabled || settings.balance != 0f || settings.monoAudio) {
        Note("均衡器、声道平衡或单声道开启时会改动音频数据，此时输出不再是位完美。")
    }
}

@Composable
private fun EqualizerPage(state: MainUiState, viewModel: MainViewModel) {
    val settings = state.settings
    SettingsGroup("") {
        SettingSwitch(Icons.Rounded.Equalizer, "均衡器", "对所有输出生效（包括 USB 独占驱动）；关闭时完全不处理音频",
            settings.eqEnabled, viewModel::setEqEnabled)
    }
    SettingsGroup("预设") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqBands.presets.forEach { (name, _) ->
                SourceChip(EqBands.label(name), selected = settings.eqPreset == name) { viewModel.setEqPreset(name) }
            }
        }
    }
    SettingsGroup("频段") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp).graphicsLayer { alpha = if (settings.eqEnabled) 1f else .45f }) {
            EqResponse(settings.eqBands, settings.eqPreamp, Modifier.fillMaxWidth().height(86.dp))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                settings.eqBands.forEachIndexed { index, gain ->
                    EqBandSlider(EqBands.labels[index], gain, enabled = settings.eqEnabled) { viewModel.setEqBand(index, it) }
                }
            }
        }
        SliderRow("前级增益", { "${if (it > 0) "+" else ""}${"%.1f".format(it)} dB" }, settings.eqPreamp, -12f..6f, steps = 35, onValue = viewModel::setEqPreamp)
        Note("提升频段时会自动降低整体电平防止削波。", inset = true)
        Spacer(Modifier.height(8.dp))
    }
    SettingsGroup("声道") {
        SliderRow(
            "左右平衡",
            { value -> when { value < -.01f -> "左 ${(-value * 100).roundToInt()}%"; value > .01f -> "右 ${(value * 100).roundToInt()}%"; else -> "居中" } },
            settings.balance,
            -1f..1f,
            steps = 39,
            onValue = viewModel::setBalance,
        )
        Spacer(Modifier.height(4.dp))
        GroupDivider()
        SettingSwitch(Icons.Rounded.Headphones, "单声道", "左右声道混合为一路，适合单耳佩戴",
            settings.monoAudio, viewModel::setMonoAudio)
    }
}

@Composable
private fun LibrarySettingsPage(state: MainUiState, viewModel: MainViewModel) {
    val settings = state.settings
    SettingsGroup("导入") {
        ChoiceRow(
            Icons.Rounded.Timer, "跳过短音频", "导入时不收录比这更短的音频（铃声、提示音、语音）。视频文件始终会被跳过",
            listOf(0 to "不跳过", 15 to "15 秒", 30 to "30 秒", 60 to "60 秒", 90 to "90 秒"),
            settings.importMinDurationSec, viewModel::setImportMinDuration,
        )
        GroupDivider()
        SettingSwitch(Icons.Rounded.ContentCopy, "跳过重复歌曲", "同一个文件，或歌名、歌手和时长都相同的歌曲只导入一次",
            settings.importSkipDuplicates, viewModel::setImportSkipDuplicates)
        GroupDivider()
        SettingSwitch(Icons.Rounded.AutoFixHigh, "自动补全封面与信息", "导入后为缺少封面、歌手或专辑信息的歌曲在线补全，并获取歌手照片与简介（优先网易云，其次 Apple Music）",
            settings.autoCompleteMetadata, viewModel::setAutoCompleteMetadata)
    }
    SettingsGroup("维护") {
        LinkRow(Icons.Rounded.Refresh, "重新扫描曲目信息", "重新读取曲目号、碟号、年份、专辑艺人与音频格式，修正专辑内的歌曲顺序", viewModel::rescanLibraryTags)
        GroupDivider()
        LinkRow(Icons.Rounded.Image, "补全全部封面与信息", "为整个曲库中缺少封面或信息的歌曲在线补全", viewModel::completeLibraryMetadata)
    }
}

@Composable
private fun OnlineSettingsPage(state: MainUiState, viewModel: MainViewModel, onExternalLyrics: () -> Unit, onNeteaseLogin: () -> Unit) {
    val settings = state.settings
    val netease by viewModel.neteaseState.collectAsState()
    val apple by viewModel.appleMusicSettings.collectAsState()
    var tokenDialog by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    SettingsGroup("网易云音乐") {
        val account = netease.account
        if (account == null) {
            LinkRow(Icons.Rounded.Cloud, "登录网易云音乐", "扫码、手机号或粘贴凭据登录，收听你的歌单、收藏和我喜欢的音乐", onNeteaseLogin)
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                UrlImage(account.avatarUrl, Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.nickname, style = MaterialTheme.typography.titleSmall)
                    Text(if (account.vipType > 0) "黑胶 VIP" else "普通账号", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PillButton("退出", viewModel::signOutNetease, style = PillStyle.OUTLINED, height = 34.dp)
            }
            GroupDivider()
            SettingSwitch(Icons.Rounded.Cloud, "在线播放", "曲库里没有的歌从网易云在线播放；关闭时只播放本地已有的歌曲",
                settings.neteaseOnline, viewModel::setNeteaseOnline)
            GroupDivider()
            ChoiceRow(
                Icons.Rounded.HighQuality, "在线音质", "高于账号权益的音质会自动降级",
                NeteaseClient.QUALITIES, settings.neteaseQuality, viewModel::setNeteaseQuality,
            )
        }
    }
    SettingsGroup("Apple Music") {
        StorefrontRow(apple.storefront, viewModel::setAppleStorefront)
        GroupDivider()
        LinkRow(
            Icons.Rounded.Key,
            if (apple.hasUserToken) "订阅令牌：已填写" + (apple.accountStorefront?.let { " · 账号地区 ${it.uppercase()}" } ?: "") else "订阅令牌（用于逐字歌词）",
            if (apple.hasUserToken) "歌词会按你的账号地区从 Apple Music 获取" else "封面、艺人、专辑资料和动态封面无需令牌；歌词需要你的订阅令牌",
        ) { tokenDialog = true }
        if (apple.hasUserToken) {
            GroupDivider()
            LinkRow(Icons.Rounded.Refresh, "检查令牌", verifying ?: "向 Apple 验证令牌并读取账号地区") {
                verifying = "正在验证…"
                scope.launch {
                    verifying = viewModel.verifyAppleToken()
                }
            }
        }
    }
    LocalOpenWebDav.current?.let { openWebDav ->
        val servers by viewModel.webDav.servers.collectAsState()
        SettingsGroup("WebDAV") {
            LinkRow(
                Icons.Rounded.Dns,
                if (servers.isEmpty()) "WebDAV 网盘与 NAS" else "WebDAV · ${servers.size} 台服务器",
                if (servers.isEmpty()) "连接 Alist、群晖、Nextcloud、坚果云等，在线播放或导入音乐库"
                else servers.joinToString("、") { it.name },
                openWebDav,
            )
        }
    }
    SettingsGroup("其他播放器") {
        LinkRow(Icons.Rounded.Headphones, "监听模式", "网易云音乐、Spotify、QQ 音乐等播放时，在 Olbyine 里同步显示封面、进度与逐字歌词", onExternalLyrics)
    }
    if (tokenDialog) {
        AppleTokenDialog(
            hasToken = apple.hasUserToken,
            onDismiss = { tokenDialog = false },
            onSave = { value -> tokenDialog = false; viewModel.setAppleUserToken(value) },
        )
    }
}

@Composable
private fun HapticsPage(state: MainUiState, viewModel: MainViewModel) {
    val settings = state.settings
    SettingsGroup("") {
        SettingSwitch(Icons.Rounded.Vibration, "触感反馈", "使用手机马达的清脆短振（遵循系统的触摸振动强度）",
            settings.hapticsEnabled, viewModel::setHapticsEnabled)
    }
    AnimatedVisibility(settings.hapticsEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        SettingsGroup("在这些地方振动") {
            HapticArea.labels.forEachIndexed { index, (area, label) ->
                if (index > 0) GroupDivider()
                CheckRow(label, checked = settings.hapticAreas and area != 0) { viewModel.setHapticArea(area, it) }
            }
        }
    }
}

private class Credit(val name: String, val repo: String, val author: String, val license: String, val usage: String, val avatar: Int)

private val Credits = listOf(
    Credit("Apple Music-like Lyrics", "Steve-xmh/applemusic-like-lyrics", "Steve-xmh", "AGPL-3.0", "歌词引擎（内置修改版）与流动背景算法", R.drawable.avatar_steve_xmh),
    Credit("AMLL DroidMate", "Zeehan2005/AMLL-DroidMate", "Zeehan2005", "AGPL-3.0", "歌词引擎的 Android 接入与歌词搜索", R.drawable.avatar_zeehan2005),
    Credit("AndroidLiquidGlass", "Kyant0/AndroidLiquidGlass", "Kyant0", "Apache-2.0", "液态玻璃透镜、折射与高光着色器", R.drawable.avatar_kyant0),
    Credit("Lyricify Lyrics Helper", "WXRIW/Lyricify-Lyrics-Helper", "WXRIW", "Apache-2.0", "歌词匹配算法与 Apple Music 歌词流程", R.drawable.avatar_wxriw),
    Credit("Lyricify Backgrounds", "WXRIW/Lyricify-Backgrounds", "WXRIW", "Apache-2.0", "Apple Music 风格的动态背景", R.drawable.avatar_wxriw),
    Credit("UniLyric", "apoint123/Unilyric", "apoint123", "MIT", "多平台歌词源（QQ 音乐、网易云、酷狗、AMLL 歌词库）", R.drawable.avatar_apoint123),
    Credit("LDDC", "chenmozhijin/LDDC", "chenmozhijin", "GPL-3.0", "QQ 音乐 QRC 逐字歌词解密", R.drawable.avatar_chenmozhijin),
    Credit("NeteaseCloudMusicApi", "Binaryify/NeteaseCloudMusicApi", "Binaryify", "MIT", "网易云音乐接口参考", R.drawable.avatar_binaryify),
    Credit("NeteaseCloudMusicApi Enhanced", "NeteaseCloudMusicApiEnhanced/api-enhanced", "NeteaseCloudMusicApiEnhanced", "MIT", "网易云音乐接口参考（听歌打卡、MV、播客等）", R.drawable.avatar_neteasecloudmusicapienhanced),
    Credit("soundcloud.py", "7x11x13/soundcloud.py", "7x11x13", "MIT", "SoundCloud v2 接口参考", R.drawable.avatar_7x11x13),
    Credit("Navidrome", "navidrome/navidrome", "navidrome", "GPL-3.0", "Subsonic / OpenSubsonic 接口", R.drawable.avatar_navidrome),
    Credit("Folia", "chthollyphile/folia-major", "chthollyphile", "AGPL-3.0", "MV 歌词动画的设计参考", R.drawable.avatar_chthollyphile),
    Credit("React Bits", "DavidHDev/react-bits", "DavidHDev", "MIT + Commons Clause", "交互特效（移植为 Compose）", R.drawable.avatar_davidhdev),
    Credit("shadcn/ui", "shadcn-ui/ui", "shadcn", "MIT", "界面设计参考", R.drawable.avatar_shadcn),
    Credit("Framework7 Icons", "framework7io/framework7-icons", "framework7io", "MIT", "iOS 风格图标", R.drawable.avatar_framework7io),
    Credit("Lucide", "lucide-icons/lucide", "lucide-icons", "ISC", "ColorOS 风格图标", R.drawable.avatar_lucide_icons),
    Credit("MingCute Icons", "mingcute-design/mingcute-icons", "mingcute-design", "Apache-2.0", "澎湃 OS 风格图标", R.drawable.avatar_mingcute_design),
    Credit("Gramophone", "FoedusProgramme/Gramophone", "nift4 / FoedusProgramme", "GPL-3.0", "ALAC 渲染器", R.drawable.avatar_foedusprogramme),
    Credit("Java Apple Lossless Decoder", "soiaf/Java-Apple-Lossless-decoder", "Peter McQuillan (soiaf)", "BSD", "ALAC 解码", R.drawable.avatar_soiaf),
    Credit("AndroidX Media3", "androidx/media", "Google", "Apache-2.0", "播放内核（ExoPlayer、媒体会话）", R.drawable.avatar_androidx),
    Credit("Jetpack Compose", "androidx/androidx", "Google", "Apache-2.0", "界面框架", R.drawable.avatar_androidx),
    Credit("OkHttp", "square/okhttp", "Square", "Apache-2.0", "网络请求", R.drawable.avatar_square),
    Credit("ZXing", "zxing/zxing", "ZXing", "Apache-2.0", "二维码生成", R.drawable.avatar_zxing),
)

@Composable
private fun AboutPage(state: MainUiState, viewModel: MainViewModel) {
    var licensesVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val update by viewModel.appUpdate.collectAsState()
    val checking by viewModel.updateChecking.collectAsState()
    val haptics = rememberHaptics()
    SettingsGroup("") {
        AboutRow()
        GroupDivider()
        LinkRow(Icons.Rounded.Gavel, "开源许可", "本应用内置 AMLL 修改版，整体以 AGPL-3.0 提供") { licensesVisible = true }
    }
    SettingsGroup("更新") {
        SettingSwitch(
            Icons.Rounded.Refresh,
            "自动检测更新",
            "每次启动时检查 GitHub Releases 是否有新版本",
            state.settings.autoUpdateCheck,
        ) { viewModel.setAutoUpdateCheck(it) }
        GroupDivider()
        LinkRow(
            Icons.Rounded.CloudSync,
            "检查更新",
            when {
                checking -> "正在检查…"
                update != null -> "发现新版本 ${update?.version}"
                else -> "当前版本 ${BuildConfig.VERSION_NAME}"
            },
        ) {
            haptics.light(HapticArea.MENUS)
            viewModel.checkForUpdates(manual = true)
        }
    }
    SettingsGroup("开源项目与致谢") {
        Credits.forEachIndexed { index, credit ->
            if (index > 0) GroupDivider()
            CreditRow(credit) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/${credit.repo}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
    Text(
        "感谢以上项目的作者与贡献者。图标、接口与算法均按各自的开源许可使用；音乐、歌词与封面的权利属于其权利人。",
        Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (licensesVisible) {
        val license = remember(context) {
            runCatching { context.assets.open("licenses/AGPL-3.0.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        OlbyineDialog(
            onDismissRequest = { licensesVisible = false },
            title = "开源许可",
            confirmButton = { GlassTextButton(onClick = { licensesVisible = false }) { Text("关闭") } },
        ) {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(
                    Credits.joinToString("\n") { "${it.name} — ${it.author} · ${it.license}\nhttps://github.com/${it.repo}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text(license, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun CreditRow(credit: Credit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            androidx.compose.ui.res.painterResource(credit.avatar),
            credit.author,
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(credit.name, style = MaterialTheme.typography.titleSmall)
            Text(credit.usage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${credit.author} · ${credit.license} · github.com/${credit.repo}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        StyledIcon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun glassColorsOf(page: SettingsPage): List<Color> = when (page) {
    SettingsPage.APPEARANCE -> GlassIconColors.purple
    SettingsPage.PLAYER -> GlassIconColors.pink
    SettingsPage.LYRICS -> GlassIconColors.indigo
    SettingsPage.LYRIC_OUTPUT -> GlassIconColors.orange
    SettingsPage.TRANSITION -> GlassIconColors.teal
    SettingsPage.AUDIO -> GlassIconColors.blue
    SettingsPage.EQUALIZER -> GlassIconColors.green
    SettingsPage.LIBRARY -> GlassIconColors.orange
    SettingsPage.ONLINE -> GlassIconColors.blue
    SettingsPage.HAPTICS -> GlassIconColors.red
    SettingsPage.ABOUT -> GlassIconColors.graphite
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    description: String,
    badge: String? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 11.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(if (compact) 14.dp else 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleSmall)
            if (!compact) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (badge != null) {
            Text(badge, style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        StyledIcon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(if (compact) 13.dp else 14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
    }
}

@Composable
private fun StyleCard(state: MainUiState, onClick: () -> Unit) {
    val spec = LocalStyleSpec.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spec.cardMargin).padding(top = 10.dp)
            .clip(RoundedCornerShape(spec.cardCorner)).background(cardColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(if (spec.style == UiStyle.MATERIAL) 27.dp else 14.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        if (spec.style == UiStyle.IOS) listOf(if (dark) Color(0xFF3A3A3C) else Color(0xFF1C1C1E), if (dark) Color(0xFF1C1C1E) else Color(0xFF48484A))
                        else listOf(colors.primary, colors.primary.copy(alpha = .7f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) { StyledIcon(Icons.Rounded.MusicNote, null, Modifier.size(28.dp), tint = if (spec.style == UiStyle.IOS) Color.White else colors.onPrimary) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Olbyine-X", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${state.settings.uiStyle.label} 风格 · " + when (state.settings.themeMode) {
                    com.olbyine.music.data.ThemeMode.DARK -> "深色"
                    com.olbyine.music.data.ThemeMode.LIGHT -> "浅色"
                    else -> "跟随系统"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        StyledIcon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(13.dp), tint = colors.onSurfaceVariant.copy(alpha = .6f))
    }
}

@Composable
private fun groupedPage(): Modifier {
    val grouped = StylePalettes.groupedBackground(LocalStyleSpec.current.style, MaterialTheme.colorScheme.background.luminance() < .5f)
    return if (grouped != null && !LocalPageBackdrop.current) Modifier.background(grouped) else Modifier
}

@Composable
private fun RadioRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.RadioButton) {
                if (!selected) haptics.tick(HapticArea.SWITCHES)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "radio")
        Box(Modifier.size(22.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                selected,
                enter = fadeIn(tween(OlbyineTokens.Motion.quickMs)) + androidx.compose.animation.scaleIn(appleSpring(response = .24f), initialScale = .5f),
                exit = fadeOut(tween(100)) + androidx.compose.animation.scaleOut(tween(100, easing = OlbyineTokens.Motion.easeOut), targetScale = .5f),
            ) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        OlbyineSwitch(checked, onChange)
    }
}

@Composable
private fun <T> ChoiceRow(icon: ImageVector, title: String, description: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.padding(start = 50.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                SourceChip(label, selected = value == selected) {
                    if (value != selected) haptics.tick(HapticArea.SWITCHES)
                    onSelect(value)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String, inset: Boolean = false) {
    Text(
        text,
        Modifier.padding(start = if (inset) 66.dp else LocalStyleSpec.current.cardMargin + 18.dp, end = 22.dp, top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EqBandSlider(label: String, gain: Float, enabled: Boolean, onGain: (Float) -> Unit) {
    val haptics = rememberHaptics()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: gain
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                shown == 0f -> "0"
                shown % 1f == 0f -> "%+.0f".format(shown)
                else -> "%+.1f".format(shown)
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = if (shown != 0f) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .width(26.dp)
                .height(150.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onDoubleTap = { haptics.click(HapticArea.SWITCHES); onGain(0f) })
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun gainAt(y: Float) = (((1f - y / size.height) * 24f - 12f) * 2f).roundToInt() / 2f
                    detectVerticalDragGestures(
                        onDragStart = { dragging = gainAt(it.y).coerceIn(-12f, 12f) },
                        onDragEnd = { dragging?.let(onGain); dragging = null },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        val next = gainAt(change.position.y).coerceIn(-12f, 12f)
                        if (next != dragging) {
                            if (next == 0f || next.rem(3f) == 0f) haptics.tick(HapticArea.SWITCHES)
                            dragging = next
                        }
                        change.consume()
                    }
                },
        ) {
            val x = size.width / 2
            val width = 5.dp.toPx()
            drawLine(trackColor, Offset(x, 0f), Offset(x, size.height), width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val zero = size.height / 2
            val y = size.height * (1f - (shown + 12f) / 24f)
            drawLine(accent, Offset(x, zero), Offset(x, y), width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.White, 9.dp.toPx(), Offset(x, y))
            drawCircle(accent, 9.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EqResponse(bands: List<Float>, preamp: Float, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
    Canvas(modifier) {
        val sampleRate = 48_000.0
        val points = 96
        val path = Path()
        val fill = Path()
        for (i in 0..points) {
            val frequency = 20.0 * 10.0.pow(i / points.toDouble() * log10(20_000.0 / 20.0))
            var db = preamp.toDouble()
            bands.forEachIndexed { index, gain ->
                if (gain != 0f) db += peakingResponseDb(sampleRate, EqBands.frequencies[index].toDouble(), 1.41, gain.toDouble(), frequency)
            }
            val x = size.width * i / points
            val y = (size.height / 2 - db / 15.0 * (size.height / 2)).toFloat().coerceIn(0f, size.height)
            if (i == 0) { path.moveTo(x, y); fill.moveTo(x, size.height / 2); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height / 2)
        fill.close()
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = .28f), accent.copy(alpha = .02f))))
        drawPath(path, accent, style = Stroke(2.dp.toPx()))
    }
}

private fun peakingResponseDb(sampleRate: Double, center: Double, q: Double, gainDb: Double, frequency: Double): Double {
    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2 * PI * center / sampleRate
    val alpha = sin(w0) / (2 * q)
    val b0 = 1 + alpha * a
    val b1 = -2 * cos(w0)
    val b2 = 1 - alpha * a
    val a0 = 1 + alpha / a
    val a1 = -2 * cos(w0)
    val a2 = 1 - alpha / a
    val w = 2 * PI * frequency / sampleRate
    fun magnitude(c0: Double, c1: Double, c2: Double): Double {
        val re = c0 + c1 * cos(w) + c2 * cos(2 * w)
        val im = -(c1 * sin(w) + c2 * sin(2 * w))
        return sqrt(re * re + im * im)
    }
    return 20 * log10(magnitude(b0, b1, b2) / magnitude(a0, a1, a2))
}

private data class IconOption(val alias: String, val label: String, val icon: Int)

private val IconOptions = listOf(
    IconOption(".IconAurora", "极光", R.mipmap.ic_launcher_aurora),
    IconOption(".IconMidnight", "午夜", R.mipmap.ic_launcher_midnight),
    IconOption(".IconPorcelain", "白瓷", R.mipmap.ic_launcher_porcelain),
    IconOption(".IconCoral", "珊瑚", R.mipmap.ic_launcher_coral),
    IconOption(".IconOcean", "深海", R.mipmap.ic_launcher_ocean),
    IconOption(".IconClassic", "经典", R.drawable.ic_launcher_native),
)

@Composable
private fun AppIconPicker() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(currentIcon(context)) }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            pinMessage = withContext(Dispatchers.IO) { pinCustomShortcut(context, uri) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconOptions.forEach { option ->
                val selected = option.alias == current
                val bitmap = remember(option.icon) {
                    runCatching {
                        androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, option.icon, context.theme)
                            ?.toBitmap(144, 144)
                    }.getOrNull()
                }
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                        if (!selected) {
                            selectIcon(context, option.alias)
                            current = option.alias
                        }
                    }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                            .border(if (selected) 2.5.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                            .padding(if (selected) 4.dp else 0.dp),
                    ) {
                        bitmap?.let { Image(it.asImageBitmap(), option.label, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(option.label, style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "切换后桌面图标会在几秒内更新；部分桌面会把图标移回应用列表，需要重新拖到桌面。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        PillButton("用自己的图片做桌面图标", {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, icon = Icons.Rounded.Apps, style = PillStyle.TONAL, height = 38.dp)
        pinMessage?.let {
            Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun currentIcon(context: android.content.Context): String {
    val pm = context.packageManager
    return IconOptions.firstOrNull { option ->
        val state = pm.getComponentEnabledSetting(ComponentName(context.packageName, context.packageName + option.alias))
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && option.alias == ".IconAurora")
    }?.alias ?: ".IconAurora"
}

private fun selectIcon(context: android.content.Context, alias: String) {
    val pm = context.packageManager
    val pkg = context.packageName
    pm.setComponentEnabledSetting(ComponentName(pkg, pkg + alias), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    IconOptions.filter { it.alias != alias }.forEach {
        pm.setComponentEnabledSetting(ComponentName(pkg, pkg + it.alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}

private fun pinCustomShortcut(context: android.content.Context, uri: android.net.Uri): String {
    val shortcuts = context.getSystemService(ShortcutManager::class.java) ?: return "这个桌面不支持添加快捷方式"
    if (!shortcuts.isRequestPinShortcutSupported) return "这个桌面不支持添加快捷方式"
    return runCatching {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            val scale = (864f / longest).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val side = minOf(decoded.width, decoded.height)
        val square = android.graphics.Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val icon = android.graphics.Bitmap.createScaledBitmap(square, 432, 432, true)
        val info = ShortcutInfo.Builder(context, "olbyine-custom-${System.currentTimeMillis()}")
            .setShortLabel("Olbyine-X")
            .setIcon(PlatformIcon.createWithAdaptiveBitmap(icon))
            .setIntent(Intent(Intent.ACTION_MAIN, null, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            .build()
        shortcuts.requestPinShortcut(info, null)
        "已请求添加到桌面，请在弹出的确认框里点「添加」"
    }.getOrElse { "图片无法使用：${it.message ?: "读取失败"}" }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val spec = LocalStyleSpec.current
    Column(Modifier.fillMaxWidth().padding(top = if (title.isBlank()) 14.dp else 20.dp)) {
        if (title.isNotBlank()) Overline(title, Modifier.padding(start = spec.cardMargin + 18.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spec.cardMargin)
                .clip(RoundedCornerShape(spec.cardCorner))
                .background(cardColor()),
            content = content,
        )
    }
}

@Composable
internal fun cardColor(): Color {
    val spec = LocalStyleSpec.current
    val colors = MaterialTheme.colorScheme
    return when (spec.style) {
        UiStyle.MATERIAL -> colors.surfaceContainer
        UiStyle.IOS -> if (LocalPageBackdrop.current) colors.surfaceContainerLow.copy(alpha = .72f) else colors.surfaceContainerLow
        UiStyle.COLOROS -> colors.surfaceContainerLow.copy(alpha = .80f)
        UiStyle.HYPEROS -> colors.surfaceContainer.copy(alpha = .76f)
    }
}

@Composable
private fun GroupDivider() {
    if (!LocalStyleSpec.current.dividers) return
    HorizontalDivider(
        Modifier.padding(start = 66.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private val SystemTileColors = listOf(
    Color(0xFF0A84FF), Color(0xFF30D158), Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFFBF5AF2),
    Color(0xFFFF375F), Color(0xFF5E5CE6), Color(0xFF32ADE6), Color(0xFF8E8E93), Color(0xFF00C7BE),
)

private fun tileColorOf(icon: ImageVector): Color = SystemTileColors[(icon.name.hashCode() and 0x7fffffff) % SystemTileColors.size]

@Composable
private fun IconTile(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    val hue = tileColorOf(icon)
    when (LocalStyleSpec.current.iconTile) {
        IconTileKind.FILLED -> Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(7.dp))
                .background(if (colors.background.luminance() < .5f) Color(0xFF636366) else Color(0xFF8E8E93)),
            contentAlignment = Alignment.Center,
        ) { StyledIcon(icon, null, Modifier.size(18.dp), tint = Color.White) }
        IconTileKind.TINTED -> Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.primary.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(19.dp), tint = colors.primary) }
        IconTileKind.CIRCLE -> Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(hue, lerpColor(hue, Color.Black, .12f)))),
            contentAlignment = Alignment.Center,
        ) { StyledIcon(icon, null, Modifier.size(19.dp), tint = Color.White) }
        IconTileKind.PLAIN -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            StyledIcon(
                icon,
                null,
                Modifier.size(24.dp),
                tint = if (LocalStyleSpec.current.style == UiStyle.MATERIAL) colors.onSurfaceVariant else hue,
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

@Composable
private fun SettingSwitch(icon: ImageVector, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        OlbyineSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StyledIcon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValue: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(start = 66.dp, end = 18.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            ValuePill(valueText(sliderValue))
        }
        OlbyineSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValue(sliderValue) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OffsetRow(offsetMs: Long, onOffset: (Long) -> Unit) {
    var sliderValue by remember(offsetMs) { mutableFloatStateOf(offsetMs.toFloat()) }
    val snapped = (sliderValue / 50f).roundToLong() * 50L
    Column(Modifier.fillMaxWidth().padding(start = 66.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("歌词时间偏移", style = MaterialTheme.typography.titleSmall)
                Text("正值让歌词更早出现", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (snapped != 0L) {
                GlassTextButton(onClick = { sliderValue = 0f; onOffset(0L) }) { Text("重置") }
            }
            ValuePill((if (snapped > 0) "+" else "") + "$snapped ms")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.Rounded.Remove, "提前 50 毫秒", {
                val next = (snapped - 50L).coerceAtLeast(-5_000L)
                sliderValue = next.toFloat(); onOffset(next)
            }, size = 32.dp, iconSize = 16.dp)
            OlbyineSlider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onOffset((sliderValue / 50f).roundToLong() * 50L) },
                valueRange = -5_000f..5_000f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            OlbyineIconButton(Icons.Rounded.Add, "推后 50 毫秒", {
                val next = (snapped + 50L).coerceAtMost(5_000L)
                sliderValue = next.toFloat(); onOffset(next)
            }, size = 32.dp, iconSize = 16.dp)
        }
    }
}

@Composable
private fun AlignRow(position: Float, onPosition: (Float) -> Unit) {
    var sliderValue by remember(position) { mutableFloatStateOf(position) }
    val snapped = (sliderValue * 100f).roundToInt() / 100f
    val delta = ((.5f - snapped) * 100f).roundToInt()
    Column(Modifier.fillMaxWidth().padding(start = 66.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("歌词高亮位置", style = MaterialTheme.typography.titleSmall)
                Text(
                    "把高亮的歌词行整体上下移动：数值越大越靠上，越靠近封面；越小越靠下",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ValuePill(
                when {
                    delta == 0 -> "居中"
                    delta > 0 -> "偏上 $delta%"
                    else -> "偏下 ${-delta}%"
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OlbyineIconButton(Icons.Rounded.Remove, "下移 5%", {
                val next = (snapped - .05f).coerceAtLeast(.15f)
                sliderValue = next; onPosition(next)
            }, size = 32.dp, iconSize = 16.dp)
            OlbyineSlider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onPosition((sliderValue * 100f).roundToInt() / 100f) },
                valueRange = .15f..0.85f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            OlbyineIconButton(Icons.Rounded.Add, "上移 5%", {
                val next = (snapped + .05f).coerceAtMost(.85f)
                sliderValue = next; onPosition(next)
            }, size = 32.dp, iconSize = 16.dp)
        }
    }
}

@Composable
private fun FontRow(hasCustomFont: Boolean, onImport: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.FontDownload)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("自定义歌词字体", style = MaterialTheme.typography.titleSmall)
            Text(
                if (hasCustomFont) "已使用自定义字体；可变字体可用字重滑块调整" else "支持 TTF、OTF、TTC、WOFF/WOFF2 与可变字体",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (hasCustomFont) "更换字体" else "导入字体", onImport, style = PillStyle.TONAL, height = 36.dp)
                if (hasCustomFont) PillButton("恢复默认", onClear, style = PillStyle.OUTLINED, height = 36.dp)
            }
        }
    }
}

@Composable
private fun UsbStatusRow(state: MainUiState) {
    val usb = state.usbBitPerfectState
    val (label, tone) = when (usb.status) {
        UsbBitPerfectStatus.OFF -> "已关闭" to StatusTone.IDLE
        UsbBitPerfectStatus.NO_DEVICE -> "等待 USB 音频设备" to StatusTone.IDLE
        UsbBitPerfectStatus.UNSUPPORTED_ANDROID -> "系统不支持无混音输出" to StatusTone.WARN
        UsbBitPerfectStatus.UNSUPPORTED_DEVICE -> "当前 USB 设备不支持" to StatusTone.WARN
        UsbBitPerfectStatus.WAITING_FOR_AUDIO -> "等待兼容音频格式" to StatusTone.IDLE
        UsbBitPerfectStatus.ACTIVE -> "USB bit-perfect 独占输出中" to StatusTone.ACTIVE
        UsbBitPerfectStatus.NATIVE_RATE -> "USB 原始采样率输出中" to StatusTone.ACTIVE
        UsbBitPerfectStatus.OUTPUT_MISMATCH -> "歌曲格式与 USB 输出不匹配" to StatusTone.WARN
        UsbBitPerfectStatus.FAILED -> "USB 输出未能启用" to StatusTone.ERROR
    }
    val dotColor by animateColorAsState(
        when (tone) {
            StatusTone.ACTIVE -> MaterialTheme.colorScheme.primary
            StatusTone.WARN -> Color(0xFFE0A33A)
            StatusTone.ERROR -> MaterialTheme.colorScheme.error
            StatusTone.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)
        },
        label = "usb-dot",
    )
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall,
                color = if (tone == StatusTone.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            usb.deviceName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            usb.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class StatusTone { IDLE, ACTIVE, WARN, ERROR }

@Composable
private fun BackgroundRow(path: String?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 54.dp, height = 84.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onPick),
        ) {
            PathImage(path, Modifier.fillMaxSize(), maxEdge = 256, contentDescription = "当前背景") {
                StyledIcon(Icons.Rounded.Image, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("自定义背景", style = MaterialTheme.typography.titleSmall)
            Text(
                if (path != null) "显示在音乐库与设置下方，可调模糊与压暗" else "选一张图片作为音乐库与设置的背景",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (path != null) "更换图片" else "选择图片", onPick, style = PillStyle.TONAL, height = 36.dp)
                if (path != null) PillButton("移除", onClear, style = PillStyle.OUTLINED, height = 36.dp)
            }
        }
    }
}

@Composable
private fun ColorSourcePicker(fromCover: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Rounded.Palette)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("莫奈取色", style = MaterialTheme.typography.titleSmall)
            Text(
                if (fromCover) "界面、弹窗与菜单随正在播放的专辑封面变色" else "跟随系统壁纸取色（Android 12 莫奈）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceChip("专辑封面", selected = fromCover) { onChange(true) }
                SourceChip("系统壁纸", selected = !fromCover) { onChange(false) }
            }
        }
    }
}

@Composable
private fun FloatingColorRow(selected: Int, onSelect: (Int) -> Unit) {
    val palettes = com.olbyine.music.playback.FloatingLyrics.PALETTES
    val names = com.olbyine.music.playback.FloatingLyrics.PALETTE_NAMES
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("高亮颜色", style = MaterialTheme.typography.titleSmall)
        Text(names[selected.coerceIn(0, names.lastIndex)], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            palettes.forEachIndexed { index, pair ->
                val ring by animateColorAsState(
                    if (index == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    tween(160), label = "float-color",
                )
                Box(
                    Modifier.size(36.dp).border(2.dp, ring, CircleShape).padding(4.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(pair[0]), Color(pair[1]))))
                        .border(1.dp, Color.Black.copy(alpha = .12f), CircleShape)
                        .clickable(role = Role.RadioButton) { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "chip-bg",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chip-fg",
    )
    Box(
        Modifier.height(34.dp).clip(CircleShape).background(background)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun AboutRow() {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF5B47FF), Color(0xFFB04BEB), Color(0xFFFF5E8E)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_olbyine_glyph_light), null, Modifier.size(52.dp), tint = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Olbyine-X", style = MaterialTheme.typography.titleLarge)
            Text(
                "版本 ${BuildConfig.VERSION_NAME} 正式版",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StyledIcon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .7f))
    }
}

@Composable
private fun ValuePill(text: String) {
    Box(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val borderWidth by animateDpAsState(if (isSelected) 2.dp else 1.dp, label = "theme-border")
            val borderColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "theme-border-color",
            )
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.RadioButton) { onSelect(mode) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(76.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(borderWidth, borderColor, RoundedCornerShape(14.dp)),
                ) {
                    when (mode) {
                        ThemeMode.LIGHT -> ThemePreview(light = true, Modifier.fillMaxSize())
                        ThemeMode.DARK -> ThemePreview(light = false, Modifier.fillMaxSize())
                        ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                            ThemePreview(light = true, Modifier.weight(1f).fillMaxHeight())
                            ThemePreview(light = false, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (mode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ThemePreview(light: Boolean, modifier: Modifier) {
    val background = if (light) Color(0xFFF7F6F8) else Color(0xFF0B0B0E)
    val ink = if (light) Color(0xFF151419) else Color(0xFFF4F2F6)
    Column(modifier.background(background).padding(10.dp)) {
        Box(Modifier.fillMaxWidth(.7f).height(7.dp).clip(CircleShape).background(ink.copy(alpha = .85f)))
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth(.45f).height(5.dp).clip(CircleShape).background(ink.copy(alpha = .35f)))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(5.dp))
            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(ink.copy(alpha = .25f)))
        }
    }
}

@Composable
private fun StylePicker(selected: UiStyle, onSelect: (UiStyle) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UiStyle.entries.forEach { style ->
            val isSelected = style == selected
            val border by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "style-border",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.RadioButton) { onSelect(style) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StylePreview(
                    style,
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(if (isSelected) 2.dp else 1.dp, border, RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    style.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StylePreview(style: UiStyle, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val paper = if (dark) Color(0xFF1B1B20) else Color(0xFFF2F2F6)
    val ink = if (dark) Color.White else Color.Black
    androidx.compose.foundation.Canvas(modifier.background(paper)) {
        val w = size.width
        val h = size.height
        val unit = w / 10f
        drawRoundRect(ink.copy(alpha = .07f), androidx.compose.ui.geometry.Offset(unit * .8f, unit * 1.2f),
            androidx.compose.ui.geometry.Size(w - unit * 1.6f, unit * 2.6f),
            androidx.compose.ui.geometry.CornerRadius(
                when (style) { UiStyle.IOS -> unit * .9f; UiStyle.HYPEROS -> unit * 1.1f; UiStyle.COLOROS -> unit * .7f; UiStyle.MATERIAL -> unit * .55f },
            ))
        drawRoundRect(ink.copy(alpha = .22f), androidx.compose.ui.geometry.Offset(unit * 1.5f, unit * 2.2f),
            androidx.compose.ui.geometry.Size(unit * 3.2f, unit * .55f), androidx.compose.ui.geometry.CornerRadius(unit * .3f))
        val trackW = unit * 2.4f
        val trackH = unit * 1.3f
        val trackX = w - unit * 1.5f - trackW
        val trackY = unit * 1.85f
        drawRoundRect(accent, androidx.compose.ui.geometry.Offset(trackX, trackY), androidx.compose.ui.geometry.Size(trackW, trackH),
            androidx.compose.ui.geometry.CornerRadius(trackH / 2))
        val thumbR = if (style == UiStyle.MATERIAL) trackH * .36f else trackH * .42f
        drawCircle(if (style == UiStyle.MATERIAL) Color.White.copy(alpha = .95f) else Color.White, thumbR,
            androidx.compose.ui.geometry.Offset(trackX + trackW - trackH / 2, trackY + trackH / 2))
        when (style) {
            UiStyle.IOS -> {
                val barW = w * .62f
                val barH = unit * 1.7f
                val x = (w - barW) / 2
                val y = h - barH - unit * .7f
                drawRoundRect(ink.copy(alpha = .10f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(barW, barH),
                    androidx.compose.ui.geometry.CornerRadius(barH / 2))
                drawRoundRect(Color.White.copy(alpha = if (dark) .35f else .9f), androidx.compose.ui.geometry.Offset(x, y),
                    androidx.compose.ui.geometry.Size(barW, barH), androidx.compose.ui.geometry.CornerRadius(barH / 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(unit * .08f))
                drawRoundRect(accent.copy(alpha = .25f), androidx.compose.ui.geometry.Offset(x + unit * .2f, y + unit * .2f),
                    androidx.compose.ui.geometry.Size(barW / 2 - unit * .2f, barH - unit * .4f), androidx.compose.ui.geometry.CornerRadius(barH / 2))
            }
            UiStyle.COLOROS -> {
                val barH = unit * 2f
                drawRect(ink.copy(alpha = .07f), androidx.compose.ui.geometry.Offset(0f, h - barH), androidx.compose.ui.geometry.Size(w, barH))
                drawCircle(accent, unit * .45f, androidx.compose.ui.geometry.Offset(w * .33f, h - barH / 2))
                drawCircle(ink.copy(alpha = .35f), unit * .45f, androidx.compose.ui.geometry.Offset(w * .67f, h - barH / 2))
            }
            UiStyle.HYPEROS -> {
                val barW = w - unit * 1.6f
                val barH = unit * 1.8f
                val y = h - barH - unit * .6f
                drawRoundRect(ink.copy(alpha = .10f), androidx.compose.ui.geometry.Offset(unit * .8f, y), androidx.compose.ui.geometry.Size(barW, barH),
                    androidx.compose.ui.geometry.CornerRadius(barH / 2))
                drawCircle(accent, unit * .42f, androidx.compose.ui.geometry.Offset(w * .35f, y + barH / 2))
                drawCircle(ink.copy(alpha = .35f), unit * .42f, androidx.compose.ui.geometry.Offset(w * .65f, y + barH / 2))
            }
            UiStyle.MATERIAL -> {
                val barH = unit * 2.4f
                drawRect(accent.copy(alpha = .08f), androidx.compose.ui.geometry.Offset(0f, h - barH), androidx.compose.ui.geometry.Size(w, barH))
                drawRoundRect(accent.copy(alpha = .30f), androidx.compose.ui.geometry.Offset(w * .33f - unit * .9f, h - barH + unit * .45f),
                    androidx.compose.ui.geometry.Size(unit * 1.8f, unit * .95f), androidx.compose.ui.geometry.CornerRadius(unit * .5f))
                drawCircle(ink.copy(alpha = .35f), unit * .3f, androidx.compose.ui.geometry.Offset(w * .67f, h - barH + unit * .92f))
            }
        }
    }
}

@Composable
private fun StorefrontRow(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Public)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("地区", style = MaterialTheme.typography.titleSmall)
                Text(
                    "搜索封面、动态封面和歌词时优先使用的 Apple Music 地区，找不到时会自动换区",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.padding(start = 50.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("cn" to "中国大陆", "tw" to "台湾", "hk" to "香港", "us" to "美国", "jp" to "日本").forEach { (code, label) ->
                SourceChip(label, selected = selected == code) { onSelect(code) }
            }
        }
    }
}

@Composable
private fun AppleTokenDialog(hasToken: Boolean, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var value by remember { mutableStateOf("") }
    OlbyineDialog(
        onDismissRequest = onDismiss,
        title = "Apple Music 订阅令牌",
        confirmButton = { GlassTextButton(onClick = { onSave(value) }, enabled = value.trim().length > 20) { Text("保存") } },
        dismissButton = {
            Row {
                if (hasToken) GlassTextButton(onClick = { onSave(null) }) { Text("清除", color = MaterialTheme.colorScheme.error) }
                GlassTextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Apple Music 的歌词只提供给订阅用户，所以需要你自己的 media-user-token：\n" +
                    "1. 在电脑浏览器登录 music.apple.com（需要有效订阅）；\n" +
                    "2. 打开开发者工具 → 应用（Application）→ Cookie → music.apple.com；\n" +
                    "3. 复制 media-user-token 的值粘贴到下面。\n\n" +
                    "令牌只保存在本机，只会发送给 Apple 用来获取歌词。封面、艺人资料和动态封面不需要它。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OlbyineTextField(value, { value = it }, if (hasToken) "替换为新的令牌" else "media-user-token")
        }
    }
}

@Composable
private fun UsbDriverRow(state: UsbDriverState, onRetry: () -> Unit) {
    val (label, tone) = when (state.status) {
        UsbDriverStatus.OFF -> "已关闭" to StatusTone.IDLE
        UsbDriverStatus.NO_DEVICE -> "等待插入 USB DAC" to StatusTone.IDLE
        UsbDriverStatus.NEEDS_PERMISSION -> "需要 USB 访问权限" to StatusTone.WARN
        UsbDriverStatus.READY -> "已独占 DAC，等待播放" to StatusTone.ACTIVE
        UsbDriverStatus.STREAMING -> (if (state.bitPerfect) "直连输出中 · 比特精确" else "直连输出中") to StatusTone.ACTIVE
        UsbDriverStatus.UNSUPPORTED -> "这个设备不是标准 USB 音频设备" to StatusTone.WARN
        UsbDriverStatus.ERROR -> "驱动出错" to StatusTone.ERROR
    }
    val dotColor by animateColorAsState(
        when (tone) {
            StatusTone.ACTIVE -> MaterialTheme.colorScheme.primary
            StatusTone.WARN -> Color(0xFFE0A33A)
            StatusTone.ERROR -> MaterialTheme.colorScheme.error
            StatusTone.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)
        },
        label = "usb-driver-dot",
    )
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (tone == StatusTone.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            listOfNotNull(state.deviceName, state.format, state.detail, state.capabilities?.let { "支持：$it" }).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.status == UsbDriverStatus.NEEDS_PERMISSION || state.status == UsbDriverStatus.ERROR ||
                state.status == UsbDriverStatus.NO_DEVICE
            ) {
                Spacer(Modifier.height(8.dp))
                PillButton("重新连接", onRetry, style = PillStyle.TONAL, height = 34.dp)
            }
        }
    }
}

@Composable
private fun GlassTuningEditor(tuning: GlassTuning, onChange: (GlassTuning) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp)) {
        Text("预设", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTuning.PRESETS.forEach { (name, preset) ->
                val active = preset.copy(gyro = tuning.gyro) == tuning
                Text(
                    name,
                    Modifier.clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onChange(preset.copy(gyro = tuning.gyro)) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            "玻璃只用在导航栏、底栏、浮层控件和弹窗上；玻璃面板里的按钮不再叠一层玻璃。安卓原生风格不使用玻璃。",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val pct: (Float) -> String = { "${(it * 100).roundToInt()}%" }
    SliderRow("高光", pct, tuning.highlight, 0f..2f) { onChange(tuning.copy(highlight = it)) }
    SliderRow("模糊", pct, tuning.blur, .3f..2f) { onChange(tuning.copy(blur = it)) }
    SliderRow("不透明度", pct, tuning.opacity, 0f..2f) { onChange(tuning.copy(opacity = it)) }
    SliderRow("折射强度", pct, tuning.refraction, 0f..2.5f) { onChange(tuning.copy(refraction = it)) }
    Spacer(Modifier.height(6.dp))
    GroupDivider()
    SettingSwitch(Icons.Rounded.BlurOn, "高光随手机倾斜移动", "边缘高光跟随陀螺仪（重力感应），像真实玻璃一样随角度流动",
        tuning.gyro) { onChange(tuning.copy(gyro = it)) }
}