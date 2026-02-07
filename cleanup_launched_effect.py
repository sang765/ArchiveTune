#!/usr/bin/env python3

# Read the MainActivity.kt file
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'r') as f:
    content = f.read()

# Find and remove any duplicate LaunchedEffect blocks
# We need to find the entire LaunchedEffect and clean it up

import re

# Pattern to match the old LaunchedEffect that should be removed
old_pattern = r'\s+playerConnection\.service\.currentMediaMetadata\.collectLatest\s+\{[^}]*(?:\{[^}]*\}[^}]*)*\}\s*\}'

# Remove any remaining old logic
content = re.sub(old_pattern, '', content, flags=re.DOTALL)

# Find the end of the new LaunchedEffect block and make sure it's properly closed
# Let's be more careful and find the complete new block

# Find the complete new LaunchedEffect block that we want to keep
new_launched_effect_pattern = r'(LaunchedEffect\(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor, navBackStackEntry\) \{[^}]*(?:\{[^}]*\}[^}]*)*\})'

matches = list(re.finditer(new_launched_effect_pattern, content, re.DOTALL))

if len(matches) > 0:
    # Keep only the first (new) LaunchedEffect block and remove everything else
    new_block = matches[0].group(1)
    
    # Find where this block starts and ends in the content
    start_pos = matches[0].start()
    end_pos = matches[0].end()
    
    # Find the next significant section after our LaunchedEffect
    remaining_content = content[end_pos:]
    
    # Find where ArchiveTuneTheme starts
    archive_theme_match = re.search(r'\s*ArchiveTuneTheme\(', remaining_content)
    
    if archive_theme_match:
        # Keep everything before our new block, then our new block, then everything after ArchiveTuneTheme
        before_block = content[:start_pos]
        after_archive_theme = remaining_content[archive_theme_match.start():]
        
        # Clean up the before_block to remove any incomplete LaunchedEffect
        before_block = re.sub(r'\s*LaunchedEffect\([^)]*$', '', before_block, flags=re.DOTALL)
        
        # Reconstruct the content
        content = before_block + '\n            ' + new_block + '\n' + after_archive_theme
    else:
        print("Could not find ArchiveTuneTheme section")
else:
    print("Could not find the new LaunchedEffect block")

# Write the cleaned content back
with open('/home/engine/project/app/src/main/kotlin/moe/koiverse/archivetune/MainActivity.kt', 'w') as f:
    f.write(content)

print("MainActivity.kt has been cleaned up successfully!")