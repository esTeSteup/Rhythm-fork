/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/** Persisted lyric color style ids. */
object LyricsColorStyle {
    const val THEME = "THEME"
    const val RAINBOW = "RAINBOW"
    const val OCEAN = "OCEAN"
    const val SUNSET = "SUNSET"
    const val NEON = "NEON"
    const val MONO = "MONO"
    const val CUSTOM = "CUSTOM"

    val presets = listOf(THEME, RAINBOW, OCEAN, SUNSET, NEON, MONO, CUSTOM)
}

/**
 * Colors to use for a lyrics view. A null color means "keep the color supplied by the
 * player" (theme / ambient artwork colors).
 */
data class ResolvedLyricColors(
    val text: Color?,
    val active: Color?,
    val rainbow: Boolean
)

fun resolveLyricColors(
    style: String,
    customTextArgb: Int,
    customActiveArgb: Int
): ResolvedLyricColors = when (style) {
    LyricsColorStyle.RAINBOW -> ResolvedLyricColors(
        text = null,
        active = null,
        rainbow = true
    )
    LyricsColorStyle.OCEAN -> ResolvedLyricColors(
        text = Color(0xFFB3E5FC),
        active = Color(0xFF29B6F6),
        rainbow = false
    )
    LyricsColorStyle.SUNSET -> ResolvedLyricColors(
        text = Color(0xFFFFE0B2),
        active = Color(0xFFFF7043),
        rainbow = false
    )
    LyricsColorStyle.NEON -> ResolvedLyricColors(
        text = Color(0xFFE1BEE7),
        active = Color(0xFFE040FB),
        rainbow = false
    )
    LyricsColorStyle.MONO -> ResolvedLyricColors(
        text = Color(0xFFBDBDBD),
        active = Color(0xFFFFFFFF),
        rainbow = false
    )
    LyricsColorStyle.CUSTOM -> ResolvedLyricColors(
        text = Color(customTextArgb),
        active = Color(customActiveArgb),
        rainbow = false
    )
    else -> ResolvedLyricColors(text = null, active = null, rainbow = false)
}

private val RainbowColors = listOf(
    Color(0xFFFF5252),
    Color(0xFFFFAB40),
    Color(0xFFFFEB3B),
    Color(0xFF69F0AE),
    Color(0xFF40C4FF),
    Color(0xFF7C4DFF),
    Color(0xFFFF4081),
    Color(0xFFFF5252)
)

/**
 * A slowly travelling horizontal rainbow gradient. The gradient is intentionally wider
 * than a line so the colour shift is visible even on short lyric lines.
 */
@Composable
fun rememberLyricRainbowBrush(animated: Boolean = true): Brush {
    val transition = rememberInfiniteTransition(label = "lyricRainbow")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lyricRainbowShift"
    )

    val rainbowWidth = 1400f
    val offset = if (animated) shift * rainbowWidth else 0f
    return Brush.linearGradient(
        colors = RainbowColors,
        start = Offset(offset, 0f),
        end = Offset(offset + rainbowWidth, 0f),
        // Repeat instead of clamping: once the gradient moves past the text the default
        // clamp mode paints the whole line red and the animation visibly restarts.
        tileMode = TileMode.Repeated
    )
}

/** Swatches offered by the custom color picker. */
val LyricColorSwatches: List<Color> = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFB0B0B0),
    Color(0xFF000000),
    Color(0xFFFF5252),
    Color(0xFFFF7043),
    Color(0xFFFFAB40),
    Color(0xFFFFEB3B),
    Color(0xFF69F0AE),
    Color(0xFF00E676),
    Color(0xFF40C4FF),
    Color(0xFF2979FF),
    Color(0xFF7C4DFF),
    Color(0xFFE040FB),
    Color(0xFFFF4081),
    Color(0xFFB3E5FC),
    Color(0xFFFFE0B2),
    Color(0xFFE1BEE7),
    Color(0xFFC8E6C9)
)
