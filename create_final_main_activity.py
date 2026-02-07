#!/usr/bin/env python3

# Read the original MainActivity.kt file
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt.backup', 'r') as f:
    lines = f.readlines()

# Add the new imports after DynamicThemeKey import
new_imports = [
    'import moe.koiverse.archivetune.constants.DynamicColorFromArtistKey\n',
    'import moe.koiverse.archivetune.constants.DynamicColorFromAlbumsPlaylistKey\n',
    'import moe.koiverse.archivetunePlayPauseKey\n',
    'import.constants.DynamicColorDuringiverse.archivetune moe.ko.constants.DynamicColorOverwriteKey\n',
    'import moe.koiverse.archivetune.constants.DynamicColorNoOverwritePlayerKey\n'
]

# Insert the imports after the DynamicThemeKey import (line 162)
for i, line in enumerate(lines):
    if 'import moe.koiverse.archivetune.constants.DynamicThemeKey' in line:
        for j, new_import in enumerate(new_imports):
            lines.insert(i + j + 1, new_import)
        break

# Add the new preference variables after useSystemFont (around line 551)
new_preferences = [
    '            \n',
    '            // Dynamic color preferences\n',
    '            val dynamicColorFromArtist by rememberPreference(DynamicColorFromArtistKey, defaultValue = true)\n',
    '            val dynamicColorFromAlbumsPlaylist by rememberPreference(DynamicColorFromAlbumsPlaylistKey, defaultValue = true)\n',
    '            val dynamicColorDuringPlayPause by rememberPreference(DynamicColorDuringPlayPauseKey, defaultValue = true)\n',
    '            val dynamicColorOverwrite by rememberPreference(DynamicColorOverwriteKey, defaultValue = true)\n',
    '            val dynamicColorNoOverwritePlayer by rememberPreference(DynamicColorNoOverwritePlayerKey, defaultValue = false)\n'
]

# Find the line with useSystemFont and insert preferences after it
for i, line in enumerate(lines):
    if 'val useSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)' in line:
        for j, pref in enumerate(new_preferences):
            lines.insert(i + j + 1, pref)
        break

# Find the LaunchedEffect and replace it with the new implementation
for i, line in enumerate(lines):
    if 'LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor)' in line:
        # Find the end of the current LaunchedEffect
        j = i + 1
        brace_count = 1
        while j < len(lines) and brace_count > 0:
            line_content = lines[j]
            brace_count += line_content.count('{') - line_content.count('}')
            j += 1
        
        # New LaunchedEffect implementation
        new_launched_effect = [
            '            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor, navBackStackEntry) {\n',
            '                val playerConnection = playerConnection\n',
            '                if (!enableDynamicTheme || playerConnection == null) {\n',
            '                    themeColor = if (!enableDynamicTheme) customThemeColor else DefaultThemeColor\n',
            '                    return@LaunchedEffect\n',
            '                }\n',
            '                \n',
            '                // Get current route to determine which dynamic color to use\n',
            '                val currentRoute = navBackStackEntry?.destination?.route\n',
            '                \n',
            '                // Determine if we should extract colors from artist avatar\n',
            '                val isOnArtistTab = currentRoute?.startsWith("artist/") == true\n',
            '                \n',
            '                // Determine if we should extract colors from albums/playlists/EQ\n',
            '                val isOnAlbumsPlaylistTab = currentRoute?.startsWith("album/") == true || \n',
            '                                          currentRoute?.startsWith("online_playlist/") == true ||\n',
            '                                          currentRoute?.startsWith("local_playlist/") == true ||\n',
            '                                          currentRoute?.startsWith("auto_playlist/") == true ||\n',
            '                                          currentRoute?.startsWith("cache_playlist/") == true ||\n',
            '                                          currentRoute?.startsWith("top_playlist/") == true\n',
            '                \n',
            '                // Monitor play/pause state for dynamic color changes\n',
            '                val player = playerConnection.player\n',
            '                val isCurrentlyPlaying = player?.isPlaying ?: false\n',
            '                \n',
            '                // Extract colors based on current context\n',
            '                when {\n',
            '                    // Handle play/pause color behavior\n',
            '                    dynamicColorDuringPlayPause && !isCurrentlyPlaying -> {\n',
            '                        // When paused, revert to wallpaper (Android 12+) or color palette (Android 11-)\n',
            '                        themeColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {\n',
            '                            DefaultThemeColor // This will trigger wallpaper color on Android 12+\n',
            '                        } else {\n',
            '                            customThemeColor // Use color palette on Android 11-\n',
            '                        }\n',
            '                    }\n',
            '                    \n',
            '                    // Handle artist tab colors\n',
            '                    dynamicColorFromArtist && isOnArtistTab -> {\n',
            '                        playerConnection.service.currentMediaMetadata.collectLatest { song ->\n',
            '                            if (song != null) {\n',
            '                                withContext(Dispatchers.Default) {\n',
            '                                    try {\n',
            '                                        // For artist tabs, we extract color from artist avatar/channel image\n',
            '                                        // Try to get artist avatar from song metadata or use song thumbnail as fallback\n',
            '                                        val artistImageUrl = song.thumbnailUrl // This could be enhanced to get actual artist avatar\n',
            '                                        val result = imageLoader.execute(\n',
            '                                            ImageRequest\n',
            '                                                .Builder(this@MainActivity)\n',
            '                                                .data(artistImageUrl)\n',
            '                                                .allowHardware(false)\n',
            '                                                .build(),\n',
            '                                        )\n',
            '                                        val extractedColor = result.image?.toBitmap()?.extractThemeColor()\n',
            '                                        withContext(Dispatchers.Main) {\n',
            '                                            themeColor = extractedColor ?: DefaultThemeColor\n',
            '                                        }\n',
            '                                    } catch (e: Exception) {\n',
            '                                        withContext(Dispatchers.Main) {\n',
            '                                            themeColor = DefaultThemeColor\n',
            '                                        }\n',
            '                                    }\n',
            '                                }\n',
            '                            } else {\n',
            '                                themeColor = DefaultThemeColor\n',
            '                            }\n',
            '                        }\n',
            '                    }\n',
            '                    \n',
            '                    // Handle albums/playlists/EQ tab colors\n',
            '                    dynamicColorFromAlbumsPlaylist && isOnAlbumsPlaylistTab -> {\n',
            '                        playerConnection.service.currentMediaMetadata.collectLatest { song ->\n',
            '                            if (song != null) {\n',
            '                                withContext(Dispatchers.Default) {\n',
            '                                    try {\n',
            '                                        // Extract color from thumbnail for albums, playlists, and EQ\n',
            '                                        val result = imageLoader.execute(\n',
            '                                            ImageRequest\n',
            '                                                .Builder(this@MainActivity)\n',
            '                                                .data(song.thumbnailUrl)\n',
            '                                                .allowHardware(false)\n',
            '                                                .build(),\n',
            '                                        )\n',
            '                                        val extractedColor = result.image?.toBitmap()?.extractThemeColor()\n',
            '                                        withContext(Dispatchers.Main) {\n',
            '                                            themeColor = extractedColor ?: DefaultThemeColor\n',
            '                                        }\n',
            '                                    } catch (e: Exception) {\n',
            '                                        withContext(Dispatchers.Main) {\n',
            '                                            themeColor = DefaultThemeColor\n',
            '                                        }\n',
            '                                    }\n',
            '                                }\n',
            '                            } else {\n',
            '                                themeColor = DefaultThemeColor\n',
            '                            }\n',
            '                        }\n',
            '                    }\n',
            '                    \n',
            '                    // Default behavior - extract from current song thumbnail (existing functionality)\n',
            '                    else -> {\n',
            '                        playerConnection.service.currentMediaMetadata.collectLatest { song ->\n',
            '                            if (song != null) {\n',
            '                                withContext(Dispatchers.Default) {\n',
            '                                    try {\n',
            '                                        val result = imageLoader.execute(\n',
            '                                            ImageRequest\n',
            '                                                .Builder(this@MainActivity)\n',
            '                                                .data(song.thumbnailUrl)\n',
            '                                                .allowHardware(false)\n',
            '                                                .build(),\n',
            '                                        )\n',
            '                                        val extractedColor = result.image?.toBitmap()?.extractThemeColor()\n',
            '                                        withContext(Dispatchers.Main) {\n',
            '                                            themeColor = extractedColor ?: DefaultThemeColor\n',
            '                                        }\n',
            '                                    } catch (e: Exception) {\n',
            '                                        withContext(Dispatchers.Main) {\n',
            '                                            themeColor = DefaultThemeColor\n',
            '                                        }\n',
            '                                    }\n',
            '                                }\n',
            '                            } else {\n',
            '                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {\n',
            '                                    themeColor = DefaultThemeColor\n',
            '                                } else {\n',
            '                                    themeColor = customThemeColor\n',
            '                                }\n',
            '                            }\n',
            '                        }\n',
            '                    }\n',
            '                }\n',
            '            }\n',
            '            \n'
        ]
        
        # Replace the old LaunchedEffect with the new one
        lines = lines[:i] + new_launched_effect + lines[j:]
        break

# Write the updated content back
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'w') as f:
    f.writelines(lines)

print("MainActivity.kt has been updated with comprehensive dynamic color functionality!")