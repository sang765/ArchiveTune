/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package moe.koiverse.archivetune.models

import moe.koiverse.archivetune.innertube.models.SongItem

/**
 * Data model for playlist suggestion system
 */
data class PlaylistSuggestion(
    val songs: List<SongItem> = emptyList(),
    val nextPageToken: String? = null,
    val currentQueryIndex: Int = 0,
    val queries: List<String> = emptyList(),
    val suggestedSongIds: Set<String> = emptySet(),
    val cacheTimestamp: Long = 0L
) {
    companion object {
        const val CACHE_DURATION_MS = 12 * 60 * 60 * 1000L // 12 hours
    }

    fun isCacheValid(): Boolean {
        return System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION_MS
    }
}
