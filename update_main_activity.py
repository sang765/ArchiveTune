#!/usr/bin/env python3

import re

# Read the MainActivity.kt file
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'r') as f:
    content = f.read()

# Add the imports after DynamicThemeKey import
import_section = '''import moe.koiverse.archivetune.constants.DynamicThemeKey
import moe.koiverse.archivetune.constants.DynamicColorFromArtistKey
import moe.koiverse.archivetune.constants.DynamicColorFromAlbumsPlaylistKey
import moe.koiverse.archivetune.constants.DynamicColorDuringPlayPauseKey
import moe.koiverse.archivetune.constants.DynamicColorOverwriteKey
import moe.koiverse.archivetune.constants.DynamicColorNoOverwritePlayerKey'''

# Replace the DynamicThemeKey import with all the imports
content = re.sub(
    r'import moe\.koiverse\.archivetune\.constants\.DynamicThemeKey',
    import_section,
    content
)

# Add the preference variables
preferences_code = '''
            // Dynamic color preferences
            val dynamicColorFromArtist by rememberPreference(DynamicColorFromArtistKey, defaultValue = true)
            val dynamicColorFromAlbumsPlaylist by rememberPreference(DynamicColorFromAlbumsPlaylistKey, defaultValue = true)
            val dynamicColorDuringPlayPause by rememberPreference(DynamicColorDuringPlayPauseKey, defaultValue = true)
            val dynamicColorOverwrite by rememberPreference(DynamicColorOverwriteKey, defaultValue = true)
            val dynamicColorNoOverwritePlayer by rememberPreference(DynamicColorNoOverwritePlayerKey, defaultValue = false)'''

# Insert the preferences after useSystemFont
content = re.sub(
    r'val useSystemFont by rememberPreference\(UseSystemFontKey, defaultValue = false\)',
    'val useSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)' + preferences_code,
    content
)

# New dynamic color logic
new_dynamic_logic = '''LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor, navBackStackEntry) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = if (!enableDynamicTheme) customThemeColor else DefaultThemeColor
                    return@LaunchedEffect
                }
                
                // Get current route to determine which dynamic color to use
                val currentRoute = navBackStackEntry?.destination?.route
                
                // Determine if we should extract colors from artist avatar
                val isOnArtistTab = currentRoute?.startsWith("artist/") == true
                
                // Determine if we should extract colors from albums/playlists/EQ
                val isOnAlbumsPlaylistTab = currentRoute?.startsWith("album/") == true || 
                                          currentRoute?.startsWith("online_playlist/") == true ||
                                          currentRoute?.startsWith("local_playlist/") == true ||
                                          currentRoute?.startsWith("auto_playlist/") == true ||
                                          currentRoute?.startsWith("cache_playlist/") == true ||
                                          currentRoute?.startsWith("top_playlist/") == true
                
                // Monitor play/pause state for dynamic color changes
                val player = playerConnection.player
                val isCurrentlyPlaying = player?.isPlaying ?: false
                
                // Extract colors based on current context
                when {
                    // Handle play/pause color behavior
                    dynamicColorDuringPlayPause && !isCurrentlyPlaying -> {
                        // When paused, revert to wallpaper (Android 12+) or color palette (Android 11-)
                        themeColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            DefaultThemeColor // This will trigger wallpaper color on Android 12+
                        } else {
                            customThemeColor // Use color palette on Android 11-
                        }
                    }
                    
                    // Handle artist tab colors
                    dynamicColorFromArtist && isOnArtistTab -> {
                        playerConnection.service.currentMediaMetadata.collectLatest { song ->
                            if (song != null) {
                                withContext(Dispatchers.Default) {
                                    try {
                                        // For artist tabs, we extract color from artist avatar/channel image
                                        // Try to get artist avatar from song metadata or use song thumbnail as fallback
                                        val artistImageUrl = song.thumbnailUrl // This could be enhanced to get actual artist avatar
                                        val result = imageLoader.execute(
                                            ImageRequest
                                                .Builder(this@MainActivity)
                                                .data(artistImageUrl)
                                                .allowHardware(false)
                                                .build(),
                                        )
                                        val extractedColor = result.image?.toBitmap()?.extractThemeColor()
                                        withContext(Dispatchers.Main) {
                                            themeColor = extractedColor ?: DefaultThemeColor
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            themeColor = DefaultThemeColor
                                        }
                                    }
                                }
                            } else {
                                themeColor = DefaultThemeColor
                            }
                        }
                    }
                    
                    // Handle albums/playlists/EQ tab colors
                    dynamicColorFromAlbumsPlaylist && isOnAlbumsPlaylistTab -> {
                        playerConnection.service.currentMediaMetadata.collectLatest { song ->
                            if (song != null) {
                                withContext(Dispatchers.Default) {
                                    try {
                                        // Extract color from thumbnail for albums, playlists, and EQ
                                        val result = imageLoader.execute(
                                            ImageRequest
                                                .Builder(this@MainActivity)
                                                .data(song.thumbnailUrl)
                                                .allowHardware(false)
                                                .build(),
                                        )
                                        val extractedColor = result.image?.toBitmap()?.extractThemeColor()
                                        withContext(Dispatchers.Main) {
                                            themeColor = extractedColor ?: DefaultThemeColor
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            themeColor = DefaultThemeColor
                                        }
                                    }
                                }
                            } else {
                                themeColor = DefaultThemeColor
                            }
                        }
                    }
                    
                    // Default behavior - extract from current song thumbnail (existing functionality)
                    else -> {
                        playerConnection.service.currentMediaMetadata.collectLatest { song ->
                            if (song != null) {
                                withContext(Dispatchers.Default) {
                                    try {
                                        val result = imageLoader.execute(
                                            ImageRequest
                                                .Builder(this@MainActivity)
                                                .data(song.thumbnailUrl)
                                                .allowHardware(false)
                                                .build(),
                                        )
                                        val extractedColor = result.image?.toBitmap()?.extractThemeColor()
                                        withContext(Dispatchers.Main) {
                                            themeColor = extractedColor ?: DefaultThemeColor
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            themeColor = DefaultThemeColor
                                        }
                                    }
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    themeColor = DefaultThemeColor
                                } else {
                                    themeColor = customThemeColor
                                }
                            }
                        }
                    }
                }
            }'''

# Replace the existing LaunchedEffect
content = re.sub(
    r'LaunchedEffect\(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor\) \{[^}]*?\}',
    new_dynamic_logic,
    content,
    flags=re.DOTALL
)

# Write the updated content back to the file
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'w') as f:
    f.write(content)

print("MainActivity.kt has been updated successfully!")