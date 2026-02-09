/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package moe.koiverse.archivetune.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.db.entities.AlbumEntity
import moe.koiverse.archivetune.db.entities.ArtistEntity
import moe.koiverse.archivetune.db.entities.Song
import moe.koiverse.archivetune.db.entities.SongEntity
import moe.koiverse.archivetune.innertube.models.SongItem
import moe.koiverse.archivetune.ui.component.IconButton
import moe.koiverse.archivetune.ui.component.ListItem
import moe.koiverse.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.koiverse.archivetune.ui.component.shimmer.ShimmerHost
import moe.koiverse.archivetune.ui.utils.makeTimeString
import com.valentinilk.shimmer.shimmer

@Composable
fun PlaylistSuggestionsSection(
    suggestions: List<SongItem>,
    isLoading: Boolean,
    onAddToPlaylist: (SongItem) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty() && !isLoading) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Header with title and refresh button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.you_might_like),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onRefresh,
                onLongClick = {}
            ) {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = stringResource(R.string.refresh_suggestions),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Horizontal scrolling song list
        val lazyListState = rememberLazyListState()
        val reachedEnd by remember {
            derivedStateOf {
                val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index >= suggestions.size - 3
            }
        }

        LaunchedEffect(reachedEnd) {
            if (reachedEnd && !isLoading) {
                onLoadMore()
            }
        }

        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = suggestions,
                key = { it.id }
            ) { song ->
                Box(
                    modifier = Modifier.width(300.dp)
                ) {
                    ListItem(
                        title = song.title,
                        subtitle = song.artists.joinToString { it.name } + 
                            (song.duration?.let { " • ${makeTimeString(it * 1000L)}" } ?: ""),
                        thumbnailContent = {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                            ) {
                                moe.koiverse.archivetune.ui.component.AsyncImage(
                                    url = song.thumbnail,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { onAddToPlaylist(song) },
                                onLongClick = {}
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.add),
                                    contentDescription = stringResource(R.string.add_to_playlist),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }

            // Loading indicator at the end
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .width(300.dp)
                            .height(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistSuggestionsSectionLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        ShimmerHost {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .shimmer(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(24.dp)
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(3) {
                    Box(
                        modifier = Modifier
                            .width(300.dp)
                            .shimmer()
                    ) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }
    }
}
