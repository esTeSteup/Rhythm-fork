/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.domain.utils

/**
 * Process-wide gate for play-count updates.
 *
 * Playback state is observed by every MusicViewModel instance that is still alive in the
 * navigation back stack, and some playback entry points also update the counters explicitly.
 * Without a shared gate a single playback can increment the play count several times.
 *
 * The first report for a song wins; repeated reports for the same song within
 * [DEDUP_WINDOW_MS] are treated as the same playback. Monotonic time is used so wall-clock
 * changes cannot affect the window.
 */
object PlayCountDeduplicator {
    private const val DEDUP_WINDOW_MS = 2_000L

    private var lastSongId: String? = null
    private var lastCountedAtMs: Long = 0L

    /**
     * Returns true when this report should update the play counters. Pass [nowMs] in tests
     * to make the window deterministic.
     */
    @Synchronized
    fun tryAcquire(songId: String, nowMs: Long = System.nanoTime() / 1_000_000L): Boolean {
        if (songId == lastSongId && nowMs - lastCountedAtMs < DEDUP_WINDOW_MS) {
            return false
        }
        lastSongId = songId
        lastCountedAtMs = nowMs
        return true
    }

    @Synchronized
    fun resetForTesting() {
        lastSongId = null
        lastCountedAtMs = 0L
    }
}
