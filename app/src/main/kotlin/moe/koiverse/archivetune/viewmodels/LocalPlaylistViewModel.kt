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
import moe.koiverse.archivetune.innertube.models.SongItem
import moe.koiverse.archivetune.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    @ApplicationContext context: Context,
    val database: MusicDatabase,
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

    val playlistSuggestions: StateFlow<List<SongItem>?> = playlist
        .map { playlistEntity ->
            if (playlistEntity == null) return@map null
            loadPlaylistSuggestions(playlistEntity.playlist.name)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private suspend fun loadPlaylistSuggestions(playlistName: String): List<SongItem> {
        val existingSongIds = playlistSongs.first().map { it.song.id }
        return withContext(IO) {
            try {
                YouTube.search(playlistName, YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.filterNot { song -> song.id in existingSongIds }
                    ?.take(12)
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addSongToPlaylist(songItem: SongItem) {
        val existingSongs = playlistSongs.first()
        val songAlreadyInPlaylist = existingSongs.any { it.song.id == songItem.id }

        if (songAlreadyInPlaylist) return

        database.transaction {
            val songEntity = database.query {
                moe.koiverse.archivetune.db.entities.SongEntity(songItem.id)
            }

            if (songEntity == null) {
                insert(
                    moe.koiverse.archivetune.db.entities.SongEntity(
                        id = songItem.id,
                        title = songItem.title,
                        thumbnailUrl = songItem.thumbnail,
                        duration = songItem.duration ?: 0,
                    )
                )
            }

            val newPosition = existingSongs.size
            insert(
                moe.koiverse.archivetune.db.entities.PlaylistSongMap(
                    songId = songItem.id,
                    playlistId = playlistId,
                    position = newPosition,
                    setVideoId = songItem.setVideoId,
                )
            )
        }

        playlist.value?.playlist?.browseId?.let { browseId ->
            YouTube.addToPlaylist(browseId, songItem.id)
        }
    }

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
    }
}
