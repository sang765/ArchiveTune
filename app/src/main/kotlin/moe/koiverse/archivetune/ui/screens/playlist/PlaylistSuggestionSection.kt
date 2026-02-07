/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package moe.koiverse.archivetune.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.constants.ListItemHeight
import moe.koiverse.archivetune.innertube.models.SongItem
import moe.koiverse.archivetune.models.toSong
import moe.koiverse.archivetune.playback.PlayerConnection
import moe.koiverse.archivetune.ui.component.MenuState
import moe.koiverse.archivetune.ui.component.NavigationTitle
import moe.koiverse.archivetune.ui.component.SongListItem

@Composable
fun PlaylistSuggestionsSection(
    suggestions: List<SongItem>,
    onAddToPlaylist: (SongItem) -> Unit,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    modifier: Modifier = Modifier
) {
    if (suggestions.isNotEmpty()) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = WindowInsets.systemBars.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = suggestions,
                key = { it.id }
            ) { song ->
                SongListItem(
                    song = song.toSong(),
                    showInLibraryIcon = false,
                    showLikedIcon = false,
                    showDownloadIcon = false,
                    trailingContent = {
                        IconButton(
                            onClick = { onAddToPlaylist(song) }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.add),
                                contentDescription = stringResource(R.string.add_to_playlist),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .height(ListItemHeight)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PlaylistSuggestionsHeader(
    modifier: Modifier = Modifier
) {
    NavigationTitle(
        title = stringResource(R.string.you_might_like),
        modifier = modifier
    )
}