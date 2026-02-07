package moe.koiverse.archivetune.models

import moe.koiverse.archivetune.innertube.models.YTItem

data class PlaylistSuggestion(
    val playlistId: String,
    val suggestions: List<YTItem>
)
