package com.olbyine.music.ui

import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.CubicBezierEasing

internal object OlbyineTokens {
    object Spacing {
        val xSmall = 4.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val xLarge = 24.dp
        val xxLarge = 32.dp

        val page = 20.dp
    }

    object Corner {
        val control = 12.dp
        val card = 18.dp
        val artwork = 22.dp
        val dialog = 28.dp
        val dock = 26.dp
        val thumb = 10.dp
    }

    object Motion {
        const val quickMs = 140
        const val standardMs = 200
        const val routeMs = 260

        val easeOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
        val easeInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
        val drawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    }
}
