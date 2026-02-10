# Remaining UI Updates for Smooth Playback Feature

The core functionality for smooth playback has been implemented. The following UI files need to be updated to call the smooth playback functions:

## Files to Update

### 1. PlayerComponents.kt
Location: `app/src/main/kotlin/moe/koiverse/archivetune/ui/player/PlayerComponents.kt`

#### Play/Pause Button Handlers
Find play/pause button click handlers (around lines 672-684, 789-795, 951-957, 1100-1106) and update them to:

```kotlin
// Example pattern for play/pause buttons
onClick = {
    if (service.smoothPlayPauseEnabled.value) {
        coroutineScope.launch {
            player.smoothTogglePlayPause(
                service.playPauseFadeDuration.value,
                playerConnection.volumeFadeManager
            )
        }
    } else {
        player.togglePlayPause()
    }
}
```

#### Skip Next/Previous Button Handlers
Update skip buttons to respect the smooth transitions settings:

```kotlin
// Example for next button
onClick = {
    val shouldUseSmooth = service.smoothTrackTransitionsEnabled.value && 
                         service.smoothTransitionsAffectManualSkip.value
    playerConnection.seekToNext(smooth = shouldUseSmooth)
}

// Example for previous button
onClick = {
    val shouldUseSmooth = service.smoothTrackTransitionsEnabled.value && 
                         service.smoothTransitionsAffectManualSkip.value
    playerConnection.seekToPrevious(smooth = shouldUseSmooth)
}
```

### 2. MiniPlayerComponents.kt
Location: `app/src/main/kotlin/moe/koiverse/archivetune/ui/player/MiniPlayerComponents.kt`

Update play/pause button handler (around lines 261-273) using the same pattern as PlayerComponents.kt.

### 3. MiniPlayer.kt
Location: `app/src/main/kotlin/moe/koiverse/archivetune/ui/player/MiniPlayer.kt`

Update swipe gesture handlers for track skipping to check `SmoothTransitionsAffectManualSkipKey` preference and pass the `smooth` parameter to `seekToNext()`/`seekToPrevious()` methods.

## Implementation Pattern

All UI components that trigger playback control need to:

1. Access the `MusicService` via `playerConnection.service`
2. Check the relevant smooth playback state flows:
   - `smoothPlayPauseEnabled` for play/pause
   - `smoothTrackTransitionsEnabled` and `smoothTransitionsAffectManualSkip` for track navigation
3. Call the appropriate method:
   - `player.smoothTogglePlayPause()` with coroutine scope
   - `playerConnection.seekToNext(smooth = true/false)`
   - `playerConnection.seekToPrevious(smooth = true/false)`

## Required Imports

Add these imports to UI files:

```kotlin
import moe.koiverse.archivetune.extensions.smoothTogglePlayPause
import kotlinx.coroutines.launch
```

## Testing Checklist

- [ ] Smooth play/pause works with various duration settings (100ms to 1000ms)
- [ ] Smooth track transitions work with manual skip buttons
- [ ] Smooth track transitions work with swipe gestures in mini-player
- [ ] Settings correctly toggle smooth behavior
- [ ] All three language translations display correctly
- [ ] No audio glitches or pops during transitions
- [ ] Rapid play/pause toggles are handled gracefully
- [ ] Works with both streaming and offline music

## Notes

- The smooth transitions for automatic queue progression (non-manual) would require hooking into the player's track transition listener in MusicService
- Volume is properly restored after fades complete
- Discord presence updates are maintained during smooth transitions
