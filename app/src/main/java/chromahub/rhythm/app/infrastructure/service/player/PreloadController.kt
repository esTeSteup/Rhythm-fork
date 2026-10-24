/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DataSpec
import chromahub.rhythm.app.features.streaming.di.StreamingMusicModule
import chromahub.rhythm.app.shared.data.model.AppSettings
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import androidx.core.net.toUri

@OptIn(UnstableApi::class)
class PreloadController(
    private val context: Context,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "PreloadController"
    }

    private var preloadManager: DefaultPreloadManager? = null
    private val targetPreloadStatusControl = PlaylistTargetPreloadStatusControl()

    init {
        initialize()
    }

    fun initialize() {
        try {
            val limit = appSettings.preloadLimit.value
            targetPreloadStatusControl.preloadLimit = limit

            val resolvingDataSourceFactory = ResolvingDataSource.Factory(
                DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()),
                object : ResolvingDataSource.Resolver {
                    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                        if (dataSpec.uri.scheme == "streaming") {
                            val trackId = dataSpec.uri.lastPathSegment
                            if (!trackId.isNullOrBlank()) {
                                val repository = StreamingMusicModule.provideStreamingMusicRepository(context)
                                val freshUrl = runBlocking { repository.getStreamingUrl(trackId) }
                                if (!freshUrl.isNullOrBlank()) {
                                    return dataSpec.withUri((freshUrl).toUri())
                                }
                            }
                        }
                        return dataSpec
                    }
                }
            )

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(resolvingDataSourceFactory)

            @Suppress("DEPRECATION")
            val builder = DefaultPreloadManager.Builder(context, targetPreloadStatusControl as TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus>)
                .setMediaSourceFactory(mediaSourceFactory)

            preloadManager = builder.build()
            Log.d(TAG, "PreloadController: DefaultPreloadManager initialized with limit: $limit")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DefaultPreloadManager, using fallback precaching", e)
        }
    }

    private var queue: List<MediaItem> = emptyList()

    fun setPlayingIndex(index: Int) {
        targetPreloadStatusControl.currentPlayingIndex = index
        refreshPreloadWindow()
    }

    fun addOrUpdateQueue(mediaItems: List<MediaItem>, currentIndex: Int = C.INDEX_UNSET) {
        queue = mediaItems
        targetPreloadStatusControl.currentPlayingIndex = currentIndex
        refreshPreloadWindow()
    }

    /**
     * Registers only the tracks around the current item that can actually be preloaded.
     *
     * Registering the whole queue made media3's DefaultPreloadManager recurse once per
     * PRELOAD_STATUS_NOT_PRELOADED item: onSkipped runs synchronously when already on the
     * application thread, so a library-sized queue overflows the stack. The resulting
     * StackOverflowError escapes through the player's timeline listener, fails the
     * MediaSession command that changed the queue, and leaves the app's MediaController
     * out of sync while the player keeps playing.
     */
    private fun refreshPreloadWindow() {
        val manager = preloadManager ?: return
        val currentIndex = targetPreloadStatusControl.currentPlayingIndex
        if (queue.isEmpty() || currentIndex == C.INDEX_UNSET) {
            manager.reset()
            return
        }

        val start = (currentIndex - 1).coerceAtLeast(0)
        val end = (currentIndex + targetPreloadStatusControl.preloadLimit + 1)
            .coerceAtMost(queue.lastIndex)

        manager.reset()
        for (index in start..end) {
            manager.add(queue[index], index)
        }
        manager.setCurrentPlayingIndex(currentIndex)
        manager.invalidate()
    }

    fun remove(mediaItem: MediaItem) {
        preloadManager?.remove(mediaItem)
    }

    fun release() {
        preloadManager?.release()
        preloadManager = null
    }

    private class PlaylistTargetPreloadStatusControl : TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {
        var currentPlayingIndex: Int = C.INDEX_UNSET
        var preloadLimit: Int = 3

        override fun getTargetPreloadStatus(rankingData: Int): DefaultPreloadManager.PreloadStatus {
            if (currentPlayingIndex == C.INDEX_UNSET) {
                return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
            }
            
            val distance = rankingData - currentPlayingIndex
            return when {
                // Preload the next few songs
                distance in 1..preloadLimit -> {
                    // Stage: source prepared is excellent for warm caching and rapid starts
                    DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_SOURCE_PREPARED
                }
                // Everything else: still return a valid status but at minimum level
                else -> DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
            }
        }
    }
}
