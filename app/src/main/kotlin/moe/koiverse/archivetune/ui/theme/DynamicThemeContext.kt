package moe.koiverse.archivetune.ui.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.koiverse.archivetune.constants.DoNotApplyToPlayerKey
import moe.koiverse.archivetune.constants.DynamicColorDuringPlaybackKey
import moe.koiverse.archivetune.constants.DynamicColorFromAlbumPlaylistKey
import moe.koiverse.archivetune.constants.DynamicColorFromArtistKey
import moe.koiverse.archivetune.constants.OverwriteColorsKey

/**
 * Enum representing the current screen context for dynamic theme application
 */
enum class ThemeScreen {
    ARTIST,
    ALBUM,
    PLAYLIST,
    PLAYER,
    OTHER
}

/**
 * Data class representing the current theme context with extracted colors
 */
data class DynamicThemeContext(
    val currentScreen: ThemeScreen = ThemeScreen.OTHER,
    val extractedColors: List<Color> = emptyList(),
    val isPlaying: Boolean = false,
    val isOnTabWithColors: Boolean = false
)

/**
 * Manager object for handling dynamic theme context and color application logic
 */
object DynamicThemeManager {

    private val _context = MutableStateFlow(DynamicThemeContext())
    val context: StateFlow<DynamicThemeContext> = _context.asStateFlow()

    /**
     * Updates the current theme context with new values
     */
    fun updateContext(
        screen: ThemeScreen? = null,
        colors: List<Color>? = null,
        isPlaying: Boolean? = null,
        isOnTabWithColors: Boolean? = null
    ) {
        _context.value = _context.value.copy(
            currentScreen = screen ?: _context.value.currentScreen,
            extractedColors = colors ?: _context.value.extractedColors,
            isPlaying = isPlaying ?: _context.value.isPlaying,
            isOnTabWithColors = isOnTabWithColors ?: _context.value.isOnTabWithColors
        )
    }

    /**
     * Resets the theme context to default values
     */
    fun resetContext() {
        _context.value = DynamicThemeContext()
    }

    /**
     * Determines if dynamic colors should be applied based on preferences and context
     */
    fun shouldApplyDynamicColors(
        dynamicThemeEnabled: Boolean,
        dynamicColorFromArtist: Boolean,
        dynamicColorFromAlbumPlaylist: Boolean,
        dynamicColorDuringPlayback: Boolean,
        overwriteColors: Boolean
    ): Boolean {
        if (!dynamicThemeEnabled) return false

        val currentScreen = _context.value.currentScreen
        val isOnTabWithColors = _context.value.isOnTabWithColors
        val isPlaying = _context.value.isPlaying

        // Check if we're on a screen that should extract colors
        val shouldExtractColors = when (currentScreen) {
            ThemeScreen.ARTIST -> dynamicColorFromArtist
            ThemeScreen.ALBUM, ThemeScreen.PLAYLIST -> dynamicColorFromAlbumPlaylist
            else -> false
        }

        if (!shouldExtractColors && !overwriteColors) return false

        // Check playback state if that preference is enabled
        if (dynamicColorDuringPlayback && !isPlaying && isOnTabWithColors) {
            return false
        }

        return true
    }

    /**
     * Determines if tab colors should override player colors based on preferences
     */
    fun shouldOverwritePlayerColors(
        dynamicThemeEnabled: Boolean,
        overwriteColors: Boolean,
        doNotApplyToPlayer: Boolean
    ): Boolean {
        if (!dynamicThemeEnabled || !overwriteColors) return false
        if (doNotApplyToPlayer) return false
        return _context.value.isOnTabWithColors
    }

    /**
     * Gets the appropriate colors to use based on current context and preferences
     */
    fun getActiveColors(
        dynamicThemeEnabled: Boolean,
        dynamicColorFromArtist: Boolean,
        dynamicColorFromAlbumPlaylist: Boolean,
        dynamicColorDuringPlayback: Boolean,
        overwriteColors: Boolean,
        doNotApplyToPlayer: Boolean,
        playerColors: List<Color>,
        defaultColors: List<Color>
    ): List<Color> {
        if (!dynamicThemeEnabled) return defaultColors

        val currentScreen = _context.value.currentScreen
        val tabColors = _context.value.extractedColors
        val isPlaying = _context.value.isPlaying
        val isOnTabWithColors = _context.value.isOnTabWithColors

        // Check if we should use tab colors to overwrite
        val shouldOverwrite = shouldOverwritePlayerColors(
            dynamicThemeEnabled = dynamicThemeEnabled,
            overwriteColors = overwriteColors,
            doNotApplyToPlayer = doNotApplyToPlayer
        )

        if (shouldOverwrite && tabColors.isNotEmpty()) {
            return tabColors
        }

        // Check if we should use tab colors (without overwriting player)
        val shouldUseTabColors = when (currentScreen) {
            ThemeScreen.ARTIST -> dynamicColorFromArtist && tabColors.isNotEmpty() && isOnTabWithColors
            ThemeScreen.ALBUM, ThemeScreen.PLAYLIST -> dynamicColorFromAlbumPlaylist && tabColors.isNotEmpty() && isOnTabWithColors
            else -> false
        }

        if (shouldUseTabColors) {
            // Check playback state
            if (dynamicColorDuringPlayback && !isPlaying) {
                return defaultColors
            }
            return tabColors
        }

        // Use player colors if playing and dynamic colors enabled
        if (dynamicColorDuringPlayback && !isPlaying) {
            return defaultColors
        }

        return playerColors.ifEmpty { defaultColors }
    }

    /**
     * Determines if tab colors should be shown based on current screen and preferences
     */
    fun shouldShowTabColors(
        dynamicThemeEnabled: Boolean,
        dynamicColorFromArtist: Boolean,
        dynamicColorFromAlbumPlaylist: Boolean
    ): Boolean {
        if (!dynamicThemeEnabled) return false

        val currentScreen = _context.value.currentScreen
        return when (currentScreen) {
            ThemeScreen.ARTIST -> dynamicColorFromArtist
            ThemeScreen.ALBUM, ThemeScreen.PLAYLIST -> dynamicColorFromAlbumPlaylist
            else -> false
        }
    }
}
