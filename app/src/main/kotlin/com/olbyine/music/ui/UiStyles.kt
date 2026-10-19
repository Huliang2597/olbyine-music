package com.olbyine.music.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.UiStyle

internal enum class SwitchKind { LIQUID, MATERIAL, COLOROS, HYPEROS }

internal enum class DockKind {
    FLOATING_GLASS,
    NAVIGATION_BAR,
    FLAT_BAR,
    FLOATING_BAR,
}

internal enum class TransportKind {
    GLYPHS,
    TONAL_SQUARE,
    ACCENT_CIRCLE,
    WHITE_CIRCLE,
}

internal enum class IconTileKind { FILLED, TINTED, PLAIN, CIRCLE }

@Immutable
internal data class StyleSpec(
    val style: UiStyle,
    val cardCorner: Dp,
    val cardMargin: Dp,
    val dividers: Boolean,
    val iconTile: IconTileKind,
    val switch: SwitchKind,
    val dock: DockKind,
    val transport: TransportKind,
    val popupLiquid: Boolean,
    val popupCorner: Dp,
    val titleWeight: FontWeight,
)

internal fun styleSpecOf(style: UiStyle): StyleSpec = when (style) {
    UiStyle.IOS -> StyleSpec(
        style = style,
        cardCorner = 26.dp,
        cardMargin = 16.dp,
        dividers = true,
        iconTile = IconTileKind.FILLED,
        switch = SwitchKind.LIQUID,
        dock = DockKind.FLOATING_GLASS,
        transport = TransportKind.GLYPHS,
        popupLiquid = true,
        popupCorner = 22.dp,
        titleWeight = FontWeight.Bold,
    )
    UiStyle.COLOROS -> StyleSpec(
        style = style,
        cardCorner = 18.dp,
        cardMargin = 16.dp,
        dividers = true,
        iconTile = IconTileKind.PLAIN,
        switch = SwitchKind.COLOROS,
        dock = DockKind.FLAT_BAR,
        transport = TransportKind.ACCENT_CIRCLE,
        popupLiquid = false,
        popupCorner = 18.dp,
        titleWeight = FontWeight.Bold,
    )
    UiStyle.HYPEROS -> StyleSpec(
        style = style,
        cardCorner = 28.dp,
        cardMargin = 14.dp,
        dividers = false,
        iconTile = IconTileKind.CIRCLE,
        switch = SwitchKind.HYPEROS,
        dock = DockKind.FLOATING_BAR,
        transport = TransportKind.WHITE_CIRCLE,
        popupLiquid = false,
        popupCorner = 26.dp,
        titleWeight = FontWeight.SemiBold,
    )
    UiStyle.MATERIAL -> StyleSpec(
        style = style,
        cardCorner = 16.dp,
        cardMargin = 12.dp,
        dividers = false,
        iconTile = IconTileKind.PLAIN,
        switch = SwitchKind.MATERIAL,
        dock = DockKind.NAVIGATION_BAR,
        transport = TransportKind.TONAL_SQUARE,
        popupLiquid = false,
        popupCorner = 12.dp,
        titleWeight = FontWeight.Normal,
    )
}

internal val LocalStyleSpec = staticCompositionLocalOf { styleSpecOf(UiStyle.IOS) }
