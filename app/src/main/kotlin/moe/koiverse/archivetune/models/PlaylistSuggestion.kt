/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package moe.koiverse.archivetune.models

import moe.koiverse.archivetune.innertube.models.YTItem

data class PlaylistSuggestion(
    val items: List<YTItem>,
)