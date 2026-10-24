/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Slowly spins the artwork while playback is active.
 *
 * The rotation freezes at its current angle when playback pauses and continues from there
 * when it resumes. Disabling the feature snaps the artwork back to its upright position.
 */
@Composable
fun rememberArtworkRotation(
    isPlaying: Boolean,
    enabled: Boolean,
    degreesPerSecond: Float = 8f
): Float {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            rotation.snapTo(0f)
        }
    }

    LaunchedEffect(isPlaying, enabled, degreesPerSecond) {
        if (!enabled || !isPlaying) return@LaunchedEffect

        val durationMs = ((360f / degreesPerSecond) * 1000f).toInt().coerceAtLeast(1_000)
        while (true) {
            val start = rotation.value
            rotation.animateTo(
                targetValue = start + 360f,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
            )
            // Keep the accumulated angle bounded while preserving the visual angle.
            rotation.snapTo(rotation.value % 360f)
        }
    }

    return rotation.value
}
