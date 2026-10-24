/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.domain.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayCountDeduplicatorTest {

    @Before
    fun setUp() {
        PlayCountDeduplicator.resetForTesting()
    }

    @Test
    fun sameSongWithinWindowIsCountedOnce() {
        assertTrue(PlayCountDeduplicator.tryAcquire("song-1", nowMs = 1_000L))
        assertFalse(PlayCountDeduplicator.tryAcquire("song-1", nowMs = 1_500L))
        assertFalse(PlayCountDeduplicator.tryAcquire("song-1", nowMs = 2_999L))
    }

    @Test
    fun sameSongAfterWindowIsCountedAgain() {
        assertTrue(PlayCountDeduplicator.tryAcquire("song-1", nowMs = 1_000L))
        assertTrue(PlayCountDeduplicator.tryAcquire("song-1", nowMs = 3_000L))
    }

    @Test
    fun differentSongsAreCountedImmediately() {
        assertTrue(PlayCountDeduplicator.tryAcquire("song-1", nowMs = 1_000L))
        assertTrue(PlayCountDeduplicator.tryAcquire("song-2", nowMs = 1_010L))
    }
}
