package com.olbyine.music.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.olbyine.music.data.UiStyle
import java.util.IdentityHashMap

private fun defOf(key: GlyphKey, style: UiStyle): GlyphDef? = when (style) {
    UiStyle.IOS -> GlyphsIos.of(key)
    UiStyle.COLOROS -> GlyphsColoros.of(key)
    UiStyle.HYPEROS -> GlyphsHyperos.of(key)
    UiStyle.MATERIAL -> null
}

private val built = HashMap<Pair<GlyphKey, UiStyle>, ImageVector>()
private val keys = IdentityHashMap<ImageVector, Pair<GlyphKey, UiStyle>>()
private val missing = HashSet<Pair<GlyphKey, UiStyle>>()

internal fun glyphVector(key: GlyphKey, style: UiStyle): ImageVector? {
    val id = key to style
    built[id]?.let { return it }
    if (id in missing) return null
    val def = defOf(key, style)
    if (def == null) {
        missing += id
        return null
    }
    val black = SolidColor(Color.Black)
    val width = def.size / 24f * 1.75f
    val vector = ImageVector.Builder("Glyph.${style.name}.${key.name}", 24.dp, 24.dp, def.size, def.size).apply {
        def.paths.forEachIndexed { index, path ->
            val nodes = addPathNodes(path.d)
            when (path.mode) {
                'e' -> addPath(nodes, name = "f$index", fill = black, pathFillType = PathFillType.EvenOdd)
                's' -> addPath(nodes, name = "s$index", stroke = black, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
                'w' -> addPath(nodes, name = "s$index", stroke = black, strokeLineWidth = width * 1.35f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
                'b' -> addPath(nodes, name = "b$index", fill = black, stroke = black, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
                else -> addPath(nodes, name = "f$index", fill = black)
            }
        }
    }.build()
    built[id] = vector
    keys[vector] = id
    return vector
}

internal fun glyphOf(vector: ImageVector): Pair<GlyphKey, UiStyle>? = keys[vector]

internal fun strokePathNames(vector: ImageVector): List<String> {
    val names = ArrayList<String>()
    fun walk(group: VectorGroup) {
        for (i in 0 until group.size) {
            when (val node = group[i]) {
                is VectorGroup -> walk(node)
                is VectorPath -> if (node.name.startsWith("s")) names += node.name
            }
        }
    }
    walk(vector.root)
    return names
}

private val byFullName = mapOf(
    "Outlined.LibraryMusic" to GlyphKey.LIBRARY,
    "Rounded.LibraryMusic" to GlyphKey.LIBRARY_ON,
    "Filled.LibraryMusic" to GlyphKey.LIBRARY_ON,
    "Outlined.Cloud" to GlyphKey.STREAM,
    "Rounded.Cloud" to GlyphKey.STREAM_ON,
    "Filled.Cloud" to GlyphKey.STREAM_ON,
    "Outlined.Settings" to GlyphKey.SETTINGS,
    "Rounded.Settings" to GlyphKey.SETTINGS_ON,
    "Filled.Settings" to GlyphKey.SETTINGS_ON,
)

private fun keyFor(icon: ImageVector): GlyphKey? {
    val name = icon.name
    if (name.startsWith("Glyph.")) return null
    val family = name.removePrefix("AutoMirrored.")
    return byFullName[family] ?: GlyphMaterialNames[family.substringAfterLast('.')]
}

@Composable
internal fun styledVector(icon: ImageVector): ImageVector {
    val style = LocalStyleSpec.current.style
    if (style == UiStyle.MATERIAL) return icon
    val key = keyFor(icon) ?: return icon
    return glyphVector(key, style) ?: icon
}

@Composable
internal fun StyledIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    Icon(styledVector(icon), contentDescription, modifier, tint = tint)
}
