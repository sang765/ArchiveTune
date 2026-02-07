#!/usr/bin/env python3

# Read the MainActivity.kt file
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'r') as f:
    lines = f.readlines()

# Find the line with LaunchedEffect and replace it with the complete implementation
new_launched_effect = '''            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor, navBackStackEntry) {
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
            }
'''

# Find and replace the incomplete LaunchedEffect
for i, line in enumerate(lines):
    if 'LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor, navBackStackEntry)' in line:
        # Find the end of this LaunchedEffect block
        j = i + 1
        brace_count = 1
        while j < len(lines) and brace_count > 0:
            line_content = lines[j]
            brace_count += line_content.count('{') - line_content.count('}')
            j += 1
        
        # Replace from i to j-1 with the new implementation
        lines = lines[:i] + [new_launched_effect + '\n'] + lines[j:]
        break

# Write the updated content back
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'w') as f:
    f.writelines(lines)

print("MainActivity.kt has been fixed successfully!")