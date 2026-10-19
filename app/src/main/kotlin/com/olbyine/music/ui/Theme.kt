package com.olbyine.music.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.olbyine.music.data.ThemeMode
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

val OlbyinePink = Color(0xFFF2839E)
val OlbyinePurple = Color(0xFFA89BCB)

private val OlbyineShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(OlbyineTokens.Corner.control),
    medium = RoundedCornerShape(OlbyineTokens.Corner.card),
    large = RoundedCornerShape(OlbyineTokens.Corner.dialog),
    extraLarge = RoundedCornerShape(30.dp),
)

private val OlbyineTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineMedium = headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
        labelSmall = labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

internal val OverlineStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)

@Composable
fun OlbyineTheme(
    themeMode: ThemeMode,
    artworkColors: List<Int>?,
    useWallpaper: Boolean,
    style: com.olbyine.music.data.UiStyle = com.olbyine.music.data.UiStyle.MATERIAL,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val monet = style == com.olbyine.music.data.UiStyle.MATERIAL
    val wallpaper = useWallpaper && Build.VERSION.SDK_INT >= 31
    val artworkSeed = remember(artworkColors) { artworkColors?.let(::seedFromArtwork) }
    val scheme = remember(artworkSeed, dark, wallpaper, style) {
        when {
            !monet -> StylePalettes.scheme(style, dark)
            wallpaper -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> MonetScheme.generate(artworkSeed ?: OlbyinePink, dark)
        }
    }
    val playerScheme = remember(artworkSeed, wallpaper, style) {
        if (!monet) {
            StylePalettes.scheme(style, dark = true)
        } else {
            val seed = artworkSeed ?: if (wallpaper) dynamicDarkColorScheme(context).primary else OlbyinePink
            MonetScheme.generate(seed, dark = true, player = true)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
    }
    CompositionLocalProvider(LocalPlayerColorScheme provides playerScheme) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = OlbyineShapes,
            typography = OlbyineTypography,
            content = content,
        )
    }
}

internal val LocalPlayerColorScheme = staticCompositionLocalOf { MonetScheme.generate(OlbyinePink, dark = true, player = true) }

@Composable
internal fun PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LocalPlayerColorScheme.current,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

internal fun setSystemBarsLight(view: android.view.View, light: Boolean) {
    val activity = view.context as? Activity ?: return
    WindowCompat.getInsetsController(activity.window, view).apply {
        isAppearanceLightStatusBars = light
        isAppearanceLightNavigationBars = light
    }
}

internal fun seedFromArtwork(colors: List<Int>): Color? {
    if (colors.isEmpty()) return null
    return colors.map { Color(it) }.maxByOrNull { color ->
        val (l, c, _) = Oklch.from(color)
        c * (1f - 0.6f * kotlin.math.abs(l - 0.62f))
    }
}

internal object StylePalettes {
    private class Tones(
        val accent: Color, val onAccent: Color, val accentContainer: Color, val onAccentContainer: Color,
        val secondary: Color, val tertiary: Color,
        val background: Color, val ink: Color, val inkVariant: Color,
        val card: Color, val high: Color, val highest: Color, val lowest: Color,
        val outline: Color, val outlineVariant: Color,
    )

    private fun tones(style: com.olbyine.music.data.UiStyle, dark: Boolean): Tones = when (style) {
        com.olbyine.music.data.UiStyle.IOS -> if (dark) Tones(
            accent = Color.White, onAccent = Color.Black, accentContainer = Color(0xFF2C2C2E), onAccentContainer = Color.White,
            secondary = Color(0xFF8E8E93), tertiary = Color(0xFF636366),
            background = Color.Black, ink = Color.White, inkVariant = Color(0xFF8D8D93),
            card = Color(0xFF1C1C1E), high = Color(0xFF2C2C2E), highest = Color(0xFF3A3A3C), lowest = Color.Black,
            outline = Color(0xFF48484A), outlineVariant = Color(0xFF38383A),
        ) else Tones(
            accent = Color.Black, onAccent = Color.White, accentContainer = Color(0xFFE5E5EA), onAccentContainer = Color.Black,
            secondary = Color(0xFF8E8E93), tertiary = Color(0xFFAEAEB2),
            background = Color.White, ink = Color.Black, inkVariant = Color(0xFF8A8A8E),
            card = Color.White, high = Color(0xFFEEEEF0), highest = Color(0xFFE5E5EA), lowest = Color.White,
            outline = Color(0xFFC6C6C8), outlineVariant = Color(0xFFE5E5EA),
        )
        com.olbyine.music.data.UiStyle.COLOROS -> if (dark) Tones(
            accent = Color(0xFF4F8DFF), onAccent = Color.White, accentContainer = Color(0xFF16305C), onAccentContainer = Color(0xFFD8E5FF),
            secondary = Color(0xFF8A8A8A), tertiary = Color(0xFF2FC28E),
            background = Color(0xFF0B0B0C), ink = Color(0xFFF2F2F2), inkVariant = Color(0xFF8C8C8E),
            card = Color(0xFF1B1B1D), high = Color(0xFF262628), highest = Color(0xFF313134), lowest = Color(0xFF0B0B0C),
            outline = Color(0xFF3A3A3C), outlineVariant = Color(0xFF2A2A2C),
        ) else Tones(
            accent = Color(0xFF1F6BFF), onAccent = Color.White, accentContainer = Color(0xFFE1EBFF), onAccentContainer = Color(0xFF0A3A99),
            secondary = Color(0xFF8C8C8C), tertiary = Color(0xFF19A974),
            background = Color(0xFFF4F4F6), ink = Color(0xFF121212), inkVariant = Color(0xFF8A8A8E),
            card = Color.White, high = Color(0xFFEBEBEE), highest = Color(0xFFE0E0E4), lowest = Color.White,
            outline = Color(0xFFD5D5D8), outlineVariant = Color(0xFFE6E6E9),
        )
        else -> if (dark) Tones(
            accent = Color(0xFF3E8BFF), onAccent = Color.White, accentContainer = Color(0xFF14315E), onAccentContainer = Color(0xFFD6E6FF),
            secondary = Color(0xFF8C8C8C), tertiary = Color(0xFFFF8A3D),
            background = Color.Black, ink = Color(0xFFF0F0F0), inkVariant = Color(0xFF8C8C8C),
            card = Color(0xFF1A1A1A), high = Color(0xFF262626), highest = Color(0xFF333333), lowest = Color.Black,
            outline = Color(0xFF3A3A3A), outlineVariant = Color(0xFF292929),
        ) else Tones(
            accent = Color(0xFF3482FF), onAccent = Color.White, accentContainer = Color(0xFFE2EDFF), onAccentContainer = Color(0xFF0B3D8F),
            secondary = Color(0xFF999999), tertiary = Color(0xFFFF6A00),
            background = Color(0xFFF7F7F7), ink = Color.Black, inkVariant = Color(0xFF999999),
            card = Color.White, high = Color(0xFFF0F0F0), highest = Color(0xFFE6E6E6), lowest = Color.White,
            outline = Color(0xFFE0E0E0), outlineVariant = Color(0xFFEDEDED),
        )
    }

    fun groupedBackground(style: com.olbyine.music.data.UiStyle, dark: Boolean): Color? =
        if (style == com.olbyine.music.data.UiStyle.IOS && !dark) Color(0xFFF2F2F7) else null

    fun scheme(style: com.olbyine.music.data.UiStyle, dark: Boolean): ColorScheme {
        val t = tones(style, dark)
        val mono = style == com.olbyine.music.data.UiStyle.IOS
        val base = (if (dark) darkColorScheme() else lightColorScheme()).let {
            if (mono) it.copy(error = t.ink, onError = t.background, errorContainer = t.high, onErrorContainer = t.ink) else it
        }
        return base.copy(
            primary = t.accent,
            onPrimary = t.onAccent,
            primaryContainer = t.accentContainer,
            onPrimaryContainer = t.onAccentContainer,
            inversePrimary = t.accent,
            secondary = t.secondary,
            onSecondary = Color.White,
            secondaryContainer = t.high,
            onSecondaryContainer = t.ink,
            tertiary = t.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = t.tertiary.copy(alpha = .18f).compositeOverOpaque(t.background),
            onTertiaryContainer = t.ink,
            background = t.background,
            onBackground = t.ink,
            surface = t.background,
            onSurface = t.ink,
            surfaceVariant = t.high,
            onSurfaceVariant = t.inkVariant,
            surfaceTint = Color.Transparent,
            inverseSurface = t.ink,
            inverseOnSurface = t.background,
            outline = t.outline,
            outlineVariant = t.outlineVariant,
            surfaceBright = if (dark) t.high else t.card,
            surfaceDim = if (dark) t.background else t.high,
            surfaceContainerLowest = t.lowest,
            surfaceContainerLow = t.card,
            surfaceContainer = t.card,
            surfaceContainerHigh = t.high,
            surfaceContainerHighest = t.highest,
        )
    }

    private fun Color.compositeOverOpaque(background: Color): Color = Color(
        red = red * alpha + background.red * (1f - alpha),
        green = green * alpha + background.green * (1f - alpha),
        blue = blue * alpha + background.blue * (1f - alpha),
    )
}

internal object MonetScheme {
    fun generate(seed: Color, dark: Boolean, player: Boolean = false): ColorScheme {
        val (_, seedChroma, hue) = Oklch.from(seed)
        val accentC = seedChroma.coerceIn(0.085f, if (dark) 0.15f else 0.16f)
        val n = if (player) 2.2f else 1f
        val tertiaryHue = hue + 60f
        fun t(l: Float, c: Float, h: Float = hue) = Oklch.color(l, c, h)
        return if (dark) {
            darkColorScheme(
                primary = t(0.80f, accentC),
                onPrimary = t(0.27f, 0.07f),
                primaryContainer = t(0.36f, 0.09f),
                onPrimaryContainer = t(0.92f, 0.05f),
                inversePrimary = t(0.48f, accentC),
                secondary = t(0.80f, 0.05f),
                onSecondary = t(0.28f, 0.03f),
                secondaryContainer = t(0.32f, 0.04f),
                onSecondaryContainer = t(0.92f, 0.025f),
                tertiary = t(0.80f, 0.08f, tertiaryHue),
                onTertiary = t(0.28f, 0.05f, tertiaryHue),
                tertiaryContainer = t(0.36f, 0.07f, tertiaryHue),
                onTertiaryContainer = t(0.92f, 0.04f, tertiaryHue),
                background = t(0.155f, 0.011f * n),
                onBackground = t(0.945f, 0.007f),
                surface = t(0.165f, 0.012f * n),
                onSurface = t(0.945f, 0.007f),
                surfaceVariant = t(0.30f, 0.022f * n),
                onSurfaceVariant = t(0.78f, 0.02f),
                surfaceTint = t(0.80f, accentC),
                inverseSurface = t(0.93f, 0.008f),
                inverseOnSurface = t(0.25f, 0.012f),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                outline = t(0.60f, 0.02f),
                outlineVariant = t(0.33f, 0.018f * n),
                scrim = Color.Black,
                surfaceBright = t(0.32f, 0.02f * n),
                surfaceDim = t(0.155f, 0.011f * n),
                surfaceContainer = t(0.215f, 0.016f * n),
                surfaceContainerHigh = t(0.25f, 0.018f * n),
                surfaceContainerHighest = t(0.29f, 0.02f * n),
                surfaceContainerLow = t(0.19f, 0.014f * n),
                surfaceContainerLowest = t(0.13f, 0.01f * n),
            )
        } else {
            lightColorScheme(
                primary = t(0.50f, accentC),
                onPrimary = t(0.99f, 0.004f),
                primaryContainer = t(0.90f, 0.05f),
                onPrimaryContainer = t(0.30f, 0.08f),
                inversePrimary = t(0.80f, accentC),
                secondary = t(0.50f, 0.045f),
                onSecondary = t(0.99f, 0.004f),
                secondaryContainer = t(0.91f, 0.03f),
                onSecondaryContainer = t(0.30f, 0.035f),
                tertiary = t(0.50f, 0.08f, tertiaryHue),
                onTertiary = t(0.99f, 0.004f, tertiaryHue),
                tertiaryContainer = t(0.90f, 0.05f, tertiaryHue),
                onTertiaryContainer = t(0.30f, 0.06f, tertiaryHue),
                background = t(0.972f, 0.006f),
                onBackground = t(0.20f, 0.012f),
                surface = t(0.982f, 0.005f),
                onSurface = t(0.20f, 0.012f),
                surfaceVariant = t(0.92f, 0.015f),
                onSurfaceVariant = t(0.44f, 0.02f),
                surfaceTint = t(0.50f, accentC),
                inverseSurface = t(0.26f, 0.012f),
                inverseOnSurface = t(0.95f, 0.006f),
                error = Color(0xFFBA1A1A),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002),
                outline = t(0.62f, 0.02f),
                outlineVariant = t(0.87f, 0.012f),
                scrim = Color.Black,
                surfaceBright = t(0.995f, 0.003f),
                surfaceDim = t(0.90f, 0.01f),
                surfaceContainer = t(0.955f, 0.008f),
                surfaceContainerHigh = t(0.935f, 0.01f),
                surfaceContainerHighest = t(0.915f, 0.012f),
                surfaceContainerLow = t(0.992f, 0.004f),
                surfaceContainerLowest = Color.White,
            )
        }
    }
}

internal object Oklch {
    data class Lch(val l: Float, val c: Float, val h: Float)

    fun from(color: Color): Lch {
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        val lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        val chroma = sqrt(a * a + bb * bb)
        var hue = Math.toDegrees(atan2(bb, a).toDouble()).toFloat()
        if (hue < 0f) hue += 360f
        return Lch(lightness, chroma, hue)
    }

    fun color(lightness: Float, chroma: Float, hue: Float): Color {
        var low = 0f
        var high = chroma
        var best = toRgb(lightness, 0f, hue)
        if (inGamut(toRgb(lightness, chroma, hue))) return toColor(toRgb(lightness, chroma, hue))
        repeat(14) {
            val mid = (low + high) / 2f
            val rgb = toRgb(lightness, mid, hue)
            if (inGamut(rgb)) {
                best = rgb
                low = mid
            } else {
                high = mid
            }
        }
        return toColor(best)
    }

    private fun toRgb(lightness: Float, chroma: Float, hue: Float): FloatArray {
        val radians = Math.toRadians(hue.toDouble())
        val a = (chroma * cos(radians)).toFloat()
        val b = (chroma * sin(radians)).toFloat()
        val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
        val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
        val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }
        return floatArrayOf(
            4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
            -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
            -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
        )
    }

    private fun inGamut(rgb: FloatArray) = rgb.all { it in -0.0005f..1.0005f }

    private fun toColor(rgb: FloatArray) = Color(
        gamma(rgb[0]).coerceIn(0f, 1f),
        gamma(rgb[1]).coerceIn(0f, 1f),
        gamma(rgb[2]).coerceIn(0f, 1f),
    )

    private fun linear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    private fun gamma(channel: Float): Float {
        val c = channel.coerceIn(0f, 1f)
        return if (c <= 0.0031308f) 12.92f * c else (1.055 * c.toDouble().pow(1 / 2.4) - 0.055).toFloat()
    }
}
