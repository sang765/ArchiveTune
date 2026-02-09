/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package moe.koiverse.archivetune.utils

import moe.koiverse.archivetune.db.entities.PlaylistSong

/**
 * Utility to build search queries for playlist suggestions
 * Extracts keywords from playlist name and songs to generate multi-layered search queries
 */
object PlaylistSuggestionQueryBuilder {

    private val genreKeywords = setOf(
        "pop", "rock", "jazz", "blues", "country", "hip hop", "rap", "electronic",
        "edm", "classical", "metal", "indie", "folk", "soul", "r&b", "reggae",
        "punk", "alternative", "disco", "house", "techno", "trance", "dubstep",
        "k-pop", "j-pop", "latin", "salsa", "bossa nova", "ambient", "lofi"
    )

    private val moodKeywords = setOf(
        "chill", "relaxing", "energetic", "upbeat", "sad", "happy", "romantic",
        "melancholic", "calm", "intense", "dreamy", "dark", "bright", "smooth",
        "acoustic", "instrumental", "vocal", "party", "workout", "sleep", "study"
    )

    /**
     * Build prioritized search queries from playlist data
     */
    fun buildQueries(playlistName: String, songs: List<PlaylistSong>): List<String> {
        val queries = mutableListOf<String>()
        
        // Priority 1: Base query from playlist name
        val cleanedPlaylistName = cleanPlaylistName(playlistName)
        if (cleanedPlaylistName.isNotBlank()) {
            queries.add(cleanedPlaylistName)
        }

        // Priority 2: Extract genres/moods from playlist name
        val extractedKeywords = extractKeywords(playlistName)
        extractedKeywords.forEach { keyword ->
            queries.add(keyword)
        }

        // Priority 3: Most frequent artists from the playlist
        if (songs.isNotEmpty()) {
            val artistFrequency = songs
                .flatMap { it.song.artists }
                .groupingBy { it.name }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }
            
            artistFrequency.forEach { artist ->
                queries.add(artist)
                // Combine artist with playlist keywords
                extractedKeywords.forEach { keyword ->
                    queries.add("$artist $keyword")
                }
            }
        }

        // Priority 4: Genre/mood combinations
        val genres = extractedKeywords.filter { it in genreKeywords }
        val moods = extractedKeywords.filter { it in moodKeywords }
        
        if (genres.isNotEmpty() && moods.isNotEmpty()) {
            genres.forEach { genre ->
                moods.forEach { mood ->
                    queries.add("$genre $mood")
                }
            }
        }

        // Priority 5: Fallback general queries if nothing else worked
        if (queries.isEmpty()) {
            queries.addAll(listOf(
                "popular songs",
                "trending music",
                "top hits"
            ))
        }

        return queries.distinct()
    }

    /**
     * Clean playlist name for search (remove special chars, trim)
     */
    private fun cleanPlaylistName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .trim()
            .lowercase()
    }

    /**
     * Extract genre and mood keywords from text
     */
    private fun extractKeywords(text: String): List<String> {
        val lowercaseText = text.lowercase()
        val words = lowercaseText.split(Regex("\\s+"))
        val keywords = mutableListOf<String>()

        // Check for exact genre/mood matches
        genreKeywords.forEach { genre ->
            if (lowercaseText.contains(genre)) {
                keywords.add(genre)
            }
        }

        moodKeywords.forEach { mood ->
            if (lowercaseText.contains(mood)) {
                keywords.add(mood)
            }
        }

        // Check multi-word keywords
        val multiWordKeywords = genreKeywords + moodKeywords
        multiWordKeywords.filter { it.contains(" ") }.forEach { keyword ->
            if (lowercaseText.contains(keyword)) {
                keywords.add(keyword)
            }
        }

        return keywords.distinct()
    }
}
