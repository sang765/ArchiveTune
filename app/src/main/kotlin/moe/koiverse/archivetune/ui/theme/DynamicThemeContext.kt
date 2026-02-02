package moe.koiverse.archivetune.ui.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeScreen {
    ARTIST, ALBUM, PLAYLIST, PLAYER, OTHER
}

data class DynamicThemeContext(
    val currentScreen: ThemeScreen = ThemeScreen.OTHER,
    val extractedColors: List<Color> = emptyList(),
    val isPlaying: Boolean = false
)

object DynamicThemeManager {
    private val _context = MutableStateFlow(DynamicThemeContext())
    val context: StateFlow<DynamicThemeContext> = _context.asStateFlow()

    fun updateContext(
        screen: ThemeScreen,
        colors: List<Color>,
        isPlaying: Boolean
    ) {
        _context.value = DynamicThemeContext(screen, colors, isPlaying)
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        _context.value = _context.value.copy(isPlaying = isPlaying)
    }

    fun updateColors(colors: List<Color>) {
        _context.value = _context.value.copy(extractedColors = colors)
    }

    fun updateScreen(screen: ThemeScreen) {
        _context.value = _context.value.copy(currentScreen = screen)
    }
}
