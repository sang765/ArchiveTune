/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package moe.koiverse.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.koiverse.archivetune.constants.PlaylistSongSortDescendingKey
import moe.koiverse.archivetune.constants.PlaylistSongSortType
import moe.koiverse.archivetune.constants.PlaylistSongSortTypeKey
import moe.koiverse.archivetune.db.MusicDatabase
import moe.koiverse.archivetune.db.entities.PlaylistSong
import moe.koiverse.archivetune.extensions.reversed
import moe.koiverse.archivetune.extensions.toEnum
import moe.koiverse.archivetune.innertube.YouTube
import moe.koiverse.archivetune.innertube.models.filterExplicit
import moe.koiverse.archivetune.innertube.models.filterVideo
import moe.koiverse.archivetune.innertube.utils.completed
import moe.koiverse.archivetune.models.PlaylistSuggestion
import moe.koiverse.archivetune.utils.PlaylistSuggestionQueryBuilder
import moe.koiverse.archivetune.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM) to (it[PlaylistSongSortDescendingKey]
                        ?: true)
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending) ->
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> songs
                PlaylistSongSortType.CREATE_DATE -> songs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> songs.sortedBy { it.song.song.title }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    songs
                        .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { artist -> artist.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, songsByAlbum) ->
                            songsByAlbum.sortedBy { playlistSong ->
                                playlistSong.song.artists.joinToString("") { artist -> artist.name }
                            }
                        }
                }

                PlaylistSongSortType.PLAY_TIME -> songs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Playlist Suggestions State
    private val _playlistSuggestions = MutableStateFlow(PlaylistSuggestion())
    val playlistSuggestions: StateFlow<PlaylistSuggestion> = _playlistSuggestions.asStateFlow()

    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions.asStateFlow()

    init {
        viewModelScope.launch {
            val sortedSongs =
                playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, playlistSong ->
                    if (playlistSong.map.position != index) {
                        update(playlistSong.map.copy(position = index))
                    }
                }
            }
        }

        // Auto-load suggestions on init
        viewModelScope.launch {
            playlist.collect { playlistData ->
                if (playlistData != null) {
                    loadPlaylistSuggestions()
                }
            }
        }
    }

    /**
     * Load playlist suggestions with caching and multi-layered query system
     */
    fun loadPlaylistSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if cache is valid
            val currentSuggestion = _playlistSuggestions.value
            if (currentSuggestion.isCacheValid() && currentSuggestion.songs.isNotEmpty()) {
                return@launch
            }

            _isLoadingSuggestions.value = true

            try {
                val currentPlaylist = playlist.value ?: return@launch
                val songs = playlistSongs.value

                // Build search queries
                val queries = PlaylistSuggestionQueryBuilder.buildQueries(
                    currentPlaylist.playlist.name,
                    songs
                )

                if (queries.isEmpty()) {
                    _isLoadingSuggestions.value = false
                    return@launch
                }

                // Get existing song IDs to filter duplicates
                val existingSongIds = songs.map { it.song.id }.toSet()

                // Try first query
                val searchResult = YouTube.search(
                    queries[0],
                    YouTube.SearchFilter.FILTER_SONG
                ).completed().getOrNull()

                if (searchResult != null) {
                    val filteredSongs = searchResult.items
                        .filterIsInstance<moe.koiverse.archivetune.innertube.models.SongItem>()
                        .filterVideo(true)
                        .filterExplicit(false)
                        .filter { it.id !in existingSongIds }
                        .take(30)

                    _playlistSuggestions.value = PlaylistSuggestion(
                        songs = filteredSongs,
                        nextPageToken = searchResult.continuation,
                        currentQueryIndex = 0,
                        queries = queries,
                        suggestedSongIds = filteredSongs.map { it.id }.toSet(),
                        cacheTimestamp = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingSuggestions.value = false
            }
        }
    }

    /**
     * Load more suggestions for infinite scroll
     */
    fun loadMoreSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSuggestion = _playlistSuggestions.value
            if (_isLoadingSuggestions.value || currentSuggestion.queries.isEmpty()) {
                return@launch
            }

            _isLoadingSuggestions.value = true

            try {
                val songs = playlistSongs.value
                val existingSongIds = songs.map { it.song.id }.toSet()
                val allSuggestedIds = currentSuggestion.suggestedSongIds + existingSongIds

                // Try continuation token first
                if (currentSuggestion.nextPageToken != null) {
                    val searchResult = YouTube.searchContinuation(
                        currentSuggestion.nextPageToken
                    ).completed().getOrNull()

                    if (searchResult != null) {
                        val newSongs = searchResult.items
                            .filterIsInstance<moe.koiverse.archivetune.innertube.models.SongItem>()
                            .filterVideo(true)
                            .filterExplicit(false)
                            .filter { it.id !in allSuggestedIds }

                        if (newSongs.isNotEmpty()) {
                            _playlistSuggestions.value = currentSuggestion.copy(
                                songs = currentSuggestion.songs + newSongs,
                                nextPageToken = searchResult.continuation,
                                suggestedSongIds = allSuggestedIds + newSongs.map { it.id }
                            )
                            _isLoadingSuggestions.value = false
                            return@launch
                        }
                    }
                }

                // If no continuation or exhausted, try next query
                val nextQueryIndex = currentSuggestion.currentQueryIndex + 1
                if (nextQueryIndex < currentSuggestion.queries.size) {
                    val searchResult = YouTube.search(
                        currentSuggestion.queries[nextQueryIndex],
                        YouTube.SearchFilter.FILTER_SONG
                    ).completed().getOrNull()

                    if (searchResult != null) {
                        val newSongs = searchResult.items
                            .filterIsInstance<moe.koiverse.archivetune.innertube.models.SongItem>()
                            .filterVideo(true)
                            .filterExplicit(false)
                            .filter { it.id !in allSuggestedIds }

                        _playlistSuggestions.value = currentSuggestion.copy(
                            songs = currentSuggestion.songs + newSongs,
                            nextPageToken = searchResult.continuation,
                            currentQueryIndex = nextQueryIndex,
                            suggestedSongIds = allSuggestedIds + newSongs.map { it.id }
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingSuggestions.value = false
            }
        }
    }

    /**
     * Reset and reload suggestions (for manual refresh)
     */
    fun resetAndLoadPlaylistSuggestions() {
        viewModelScope.launch {
            _playlistSuggestions.value = PlaylistSuggestion()
            loadPlaylistSuggestions()
        }
    }
}
